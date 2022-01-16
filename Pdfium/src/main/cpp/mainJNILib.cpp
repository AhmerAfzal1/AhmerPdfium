#include "include/util.h"
#include <android/bitmap.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <fpdf_annot.h>
#include <fpdf_doc.h>
#include <fpdf_edit.h>
#include <fpdf_save.h>
#include <fpdf_text.h>
#include <fpdfview.h>
#include "java_env.h"
#include <fpdf_save.h>
#include <Mutex.h>
#include <string>
#include <vector>

#pragma clang diagnostic ignored "-Wconversion"
#pragma clang diagnostic ignored "-Wsign-conversion"
#pragma clang diagnostic ignored "-Wunused-command-line-argument"
#pragma clang diagnostic ignored "-Wunused-parameter"
#pragma clang diagnostic push

extern "C" {
#include <cstdio>
#include <cstring>
#include <sys/mman.h>
#include <sys/stat.h>
#include <unistd.h>
}

using namespace android;
static int sLibraryReferenceCount = 0;
static Mutex sLibraryLock;

static void initLibraryIfNeed() {
    Mutex::Autolock lock(sLibraryLock);
    if (sLibraryReferenceCount == 0) {
        LOGD("Init FPDF library");
        //FPDF_InitLibrary();
        FPDF_LIBRARY_CONFIG config;
        config.version = 2;
        config.m_pUserFontPaths = nullptr;
        config.m_pIsolate = nullptr;
        config.m_v8EmbedderSlot = 0;

        FPDF_InitLibraryWithConfig(&config);
    }
    sLibraryReferenceCount++;
}

static void destroyLibraryIfNeed() {
    Mutex::Autolock lock(sLibraryLock);
    sLibraryReferenceCount--;
    if (sLibraryReferenceCount == 0) {
        LOGD("Destroy FPDF library");
        FPDF_DestroyLibrary();
    }
}

struct rgb {
    uint8_t red;
    uint8_t green;
    uint8_t blue;
};

class DocumentFile {
public:
    FPDF_DOCUMENT pdfDocument = nullptr;
    size_t fileSize{};
    int fileFd{};

    DocumentFile() {
        initLibraryIfNeed();
    }

    ~DocumentFile();
};

DocumentFile::~DocumentFile() {
    if (pdfDocument != nullptr) {
        FPDF_CloseDocument(pdfDocument);
    }
    destroyLibraryIfNeed();
}

template<class string_type>
inline typename string_type::value_type *WriteInto(string_type *str, size_t length_with_null) {
    str->reserve(length_with_null);
    str->resize(length_with_null - 1);
    return &((*str)[0]);
}

inline long getFileSize(int fd) {
    struct stat file_state{};
    if (fstat(fd, &file_state) >= 0) {
        return (long) (file_state.st_size);
    } else {
        LOGE("Error getting file size");
        return 0;
    }
}

static char *getErrorDescription(const long error) {
    char *description = nullptr;
    switch (error) {
        case FPDF_ERR_SUCCESS:
            asprintf(&description, "No error.");
            break;
        case FPDF_ERR_FILE:
            asprintf(&description, "File not found or could not be opened.");
            break;
        case FPDF_ERR_FORMAT:
            asprintf(&description, "File not in PDF format or corrupted.");
            break;
        case FPDF_ERR_PASSWORD:
            asprintf(&description, "Incorrect password.");
            break;
        case FPDF_ERR_SECURITY:
            asprintf(&description, "Unsupported security scheme.");
            break;
        case FPDF_ERR_PAGE:
            asprintf(&description, "Page not found or content error.");
            break;
        default:
            asprintf(&description, "Unknown error.");
    }

    return description;
}

int jniThrowException(JNIEnv *env, const char *className, const char *message) {
    jclass exClass = env->FindClass(className);
    if (exClass == nullptr) {
        LOGE("Unable to find exception class %s", className);
        return -1;
    }
    if (env->ThrowNew(exClass, message) != JNI_OK) {
        LOGE("Failed throwing '%s' '%s'", className, message);
        return -1;
    }
    return 0;
}

int jniThrowExceptionFmt(JNIEnv *env, const char *className, const char *fmt, ...) {
    va_list args;
    va_start(args, fmt);
    char msgBuf[512];
    vsnprintf(msgBuf, sizeof(msgBuf), fmt, args);
    return jniThrowException(env, className, msgBuf);
    va_end(args);
}

jobject NewLong(JNIEnv *env, jlong value) {
    jclass cls = env->FindClass("java/lang/Long");
    jmethodID methodID = env->GetMethodID(cls, "<init>", "(J)V");
    return env->NewObject(cls, methodID, value);
}

jobject NewInteger(JNIEnv *env, jint value) {
    jclass cls = env->FindClass("java/lang/Integer");
    jmethodID methodID = env->GetMethodID(cls, "<init>", "(I)V");
    return env->NewObject(cls, methodID, value);
}

uint16_t rgbTo565(rgb *color) {
    return ((color->red >> 3) << 11) | ((color->green >> 2) << 5) | (color->blue >> 3);
}

void rgbBitmapTo565(void *source, int sourceStride, void *dest, AndroidBitmapInfo *info) {
    rgb *srcLine;
    uint16_t *dstLine;
    int y, x;
    for (y = 0; y < info->height; y++) {
        srcLine = (rgb *) source;
        dstLine = (uint16_t *) dest;
        for (x = 0; x < info->width; x++) {
            dstLine[x] = rgbTo565(&srcLine[x]);
        }
        source = (char *) source + sourceStride;
        dest = (char *) dest + info->stride;
    }
}

// Add method for writer pdf
jobject j_writer;
#define SIG_BYTE_BUFFER "Ljava/nio/ByteBuffer;"


struct PdfToFdWriter : FPDF_FILEWRITE {
    int dstFd;
};

static bool writeAllBytes(const int fd, const void *buffer, const size_t byteCount) {
    char *writeBuffer = static_cast<char *>(const_cast<void *>(buffer));
    size_t remainingBytes = byteCount;
    while (remainingBytes > 0) {
        ssize_t writtenByteCount = write(fd, writeBuffer, remainingBytes);
        if (writtenByteCount == -1) {
            if (errno == EINTR) {
                continue;
            }
            LOGE("Error writing to buffer: %d", errno);
            return false;
        }
        remainingBytes -= writtenByteCount;
        writeBuffer += writtenByteCount;
    }
    return true;
}

static int writeBlock(FPDF_FILEWRITE *owner, const void *buffer, unsigned long size) {
    const PdfToFdWriter *writer = reinterpret_cast<PdfToFdWriter *>(owner);
    const bool success = writeAllBytes(writer->dstFd, buffer, size);
    if (!success) {
        LOGE("Cannot write to file descriptor. Error:%d", errno);
        return 0;
    }
    return 1;
}

extern "C" { //For JNI support

static constexpr char kContentsKey[] = "Contents";

static int getBlock(void *param, unsigned long pos, unsigned char *outBuffer, unsigned long size) {
    const int fd = reinterpret_cast<intptr_t>(param);
    const int readCount = pread(fd, outBuffer, size, pos);
    if (readCount < 0) {
        LOGE("Cannot read from file descriptor. Error: %d", errno);
        return 0;
    }
    return 1;
}

JNI_FUNC(jlong, PdfiumCore, nativeOpenDocument)(JNI_ARGS, jint fd, jstring password) {
    auto fileLength = (size_t) getFileSize(fd);
    if (fileLength <= 0) {
        jniThrowException(env, "java/io/IOException", "File is empty");
        return -1;
    }
    auto *docFile = new DocumentFile();
    FPDF_FILEACCESS loader;
    loader.m_FileLen = fileLength;
    loader.m_Param = reinterpret_cast<void *>(intptr_t(fd));
    loader.m_GetBlock = &getBlock;

    const char *cPassword = nullptr;
    if (password != nullptr) {
        cPassword = env->GetStringUTFChars(password, nullptr);
    }

    FPDF_DOCUMENT document = FPDF_LoadCustomDocument(&loader, cPassword);

    if (cPassword != nullptr) {
        env->ReleaseStringUTFChars(password, cPassword);
    }

    if (!document) {
        delete docFile;
        const unsigned long errorNum = FPDF_GetLastError();
        if (errorNum == FPDF_ERR_PASSWORD) {
            jniThrowException(env, "com/ahmer/afzal/pdfium/PdfPasswordException",
                              "Password required or incorrect password.");
        } else {
            char *error = getErrorDescription((long) errorNum);
            jniThrowExceptionFmt(env, "java/io/IOException", "Cannot create document: %s", error);
            free(error);
        }

        return -1;
    }

    docFile->pdfDocument = document;

    return reinterpret_cast<jlong>(docFile);
}

JNI_FUNC(jlong, PdfiumCore, nativeOpenMemDocument)(JNI_ARGS, jbyteArray data, jstring password) {
    auto *docFile = new DocumentFile();
    const char *cPassword = nullptr;
    if (password != nullptr) {
        cPassword = env->GetStringUTFChars(password, nullptr);
    }
    jbyte *cData = env->GetByteArrayElements(data, nullptr);
    int size = (int) env->GetArrayLength(data);
    auto *cDataCopy = new jbyte[size];
    memcpy(cDataCopy, cData, size);
    FPDF_DOCUMENT document = FPDF_LoadMemDocument(reinterpret_cast<const void *>(cDataCopy), size,
                                                  cPassword);
    env->ReleaseByteArrayElements(data, cData, JNI_ABORT);
    if (cPassword != nullptr) {
        env->ReleaseStringUTFChars(password, cPassword);
    }
    if (!document) {
        delete docFile;
        const unsigned long errorNum = FPDF_GetLastError();
        if (errorNum == FPDF_ERR_PASSWORD) {
            jniThrowException(env, "com/ahmer/afzal/pdfium/PdfPasswordException",
                              "Password required or incorrect password.");
        } else {
            char *error = getErrorDescription((long) errorNum);
            jniThrowExceptionFmt(env, "java/io/IOException", "Cannot create document: %s", error);
            free(error);
        }
        return -1;
    }
    docFile->pdfDocument = document;
    return reinterpret_cast<jlong>(docFile);
}

JNI_FUNC(jint, PdfiumCore, nativeGetPageCount)(JNI_ARGS, jlong documentPtr) {
    auto *doc = reinterpret_cast<DocumentFile *>(documentPtr);
    return (jint) FPDF_GetPageCount(doc->pdfDocument);
}

JNI_FUNC(void, PdfiumCore, nativeCloseDocument)(JNI_ARGS, jlong documentPtr) {
    auto *doc = reinterpret_cast<DocumentFile *>(documentPtr);
    delete doc;
}

static jlong loadPageInternal(JNIEnv *env, DocumentFile *doc, int pageIndex) {
    try {
        if (doc == nullptr) throw std::runtime_error("Get page document null");
        FPDF_DOCUMENT pdfDoc = doc->pdfDocument;
        if (pdfDoc != nullptr) {
            FPDF_PAGE page = FPDF_LoadPage(pdfDoc, pageIndex);
            if (page == nullptr) {
                throw std::runtime_error("Loaded page is null");
            }
            return reinterpret_cast<jlong>(page);
        } else {
            throw std::runtime_error("Get page PDF document null");
        }
    } catch (const char *msg) {
        LOGE("%s", msg);
        jniThrowException(env, "java/lang/IllegalStateException", "Cannot load page");
        return -1;
    }
}

static void closePageInternal(jlong pagePtr) {
    FPDF_ClosePage(reinterpret_cast<FPDF_PAGE>(pagePtr));
}

JNI_FUNC(jlong, PdfiumCore, nativeLoadPage)(JNI_ARGS, jlong docPtr, jint pageIndex) {
    auto *doc = reinterpret_cast<DocumentFile *>(docPtr);
    return loadPageInternal(env, doc, (int) pageIndex);
}

JNI_FUNC(jlongArray, PdfiumCore, nativeLoadPages)(JNI_ARGS, jlong docPtr, jint fromIndex,
                                                  jint toIndex) {
    auto *doc = reinterpret_cast<DocumentFile *>(docPtr);
    if (toIndex < fromIndex) return nullptr;
    jlong pages[toIndex - fromIndex + 1];
    int i;
    for (i = 0; i <= (toIndex - fromIndex); i++) {
        pages[i] = loadPageInternal(env, doc, (int) (i + fromIndex));
    }
    jlongArray javaPages = env->NewLongArray((jsize) (toIndex - fromIndex + 1));
    env->SetLongArrayRegion(javaPages, 0, (jsize) (toIndex - fromIndex + 1), (const jlong *) pages);
    return javaPages;
}

JNI_FUNC(jint, PdfiumCore, nativeGetPageRotation)(JNI_ARGS, jlong pagePtr) {
    auto page = reinterpret_cast<FPDF_PAGE>(pagePtr);
    return (jint) FPDFPage_GetRotation(page);
}

JNI_FUNC(void, PdfiumCore, nativeClosePage)(JNI_ARGS, jlong pagePtr) {
    closePageInternal(pagePtr);
}

JNI_FUNC(void, PdfiumCore, nativeClosePages)(JNI_ARGS, jlongArray pagesPtr) {
    int length = (int) (env->GetArrayLength(pagesPtr));
    jlong *pages = env->GetLongArrayElements(pagesPtr, nullptr);
    int i;
    for (i = 0; i < length; i++) { closePageInternal(pages[i]); }
}

JNI_FUNC(jint, PdfiumCore, nativeGetPageWidthPixel)(JNI_ARGS, jlong pagePtr, jint dpi) {
    auto page = reinterpret_cast<FPDF_PAGE>(pagePtr);
    return (jint) (FPDF_GetPageWidth(page) * dpi / 72);
}

JNI_FUNC(jint, PdfiumCore, nativeGetPageHeightPixel)(JNI_ARGS, jlong pagePtr, jint dpi) {
    auto page = reinterpret_cast<FPDF_PAGE>(pagePtr);
    return (jint) (FPDF_GetPageHeight(page) * dpi / 72);
}

JNI_FUNC(jint, PdfiumCore, nativeGetPageWidthPoint)(JNI_ARGS, jlong pagePtr) {
    auto page = reinterpret_cast<FPDF_PAGE>(pagePtr);
    return (jint) FPDF_GetPageWidth(page);
}

JNI_FUNC(jint, PdfiumCore, nativeGetPageHeightPoint)(JNI_ARGS, jlong pagePtr) {
    auto page = reinterpret_cast<FPDF_PAGE>(pagePtr);
    return (jint) FPDF_GetPageHeight(page);
}

JNI_FUNC(jobject, PdfiumCore, nativeGetPageSizeByIndex)(JNI_ARGS, jlong docPtr, jint pageIndex,
                                                        jint dpi) {
    auto *doc = reinterpret_cast<DocumentFile *>(docPtr);
    if (doc == nullptr) {
        LOGE("Document is null");

        jniThrowException(env, "java/lang/IllegalStateException", "Document is null");
        return nullptr;
    }
    double width, height;
    int result = FPDF_GetPageSizeByIndex(doc->pdfDocument, pageIndex, &width, &height);
    if (result == 0) {
        width = 0;
        height = 0;
    }
    jint widthInt = (jint) (width * dpi / 72);
    jint heightInt = (jint) (height * dpi / 72);
    jclass clazz = env->FindClass("com/ahmer/afzal/pdfium/util/Size");
    jmethodID constructorID = env->GetMethodID(clazz, "<init>", "(II)V");
    return env->NewObject(clazz, constructorID, widthInt, heightInt);
}

static void
renderPageInternal(FPDF_PAGE page, ANativeWindow_Buffer *windowBuffer, int startX, int startY,
                   int canvasHorSize, int canvasVerSize, int drawSizeHor, int drawSizeVer,
                   bool renderAnnot) {

    FPDF_BITMAP pdfBitmap = FPDFBitmap_CreateEx(canvasHorSize, canvasVerSize, FPDFBitmap_BGRA,
                                                windowBuffer->bits,
                                                (int) (windowBuffer->stride) * 4);

    /*
    LOGD("Start X: %d", startX);
    LOGD("Start Y: %d", startY);
    LOGD("Canvas Hor: %d", canvasHorSize);
    LOGD("Canvas Ver: %d", canvasVerSize);
    LOGD("Draw Hor: %d", drawSizeHor);
    LOGD("Draw Ver: %d", drawSizeVer);
    */

    if (drawSizeHor < canvasHorSize || drawSizeVer < canvasVerSize) {
        FPDFBitmap_FillRect(pdfBitmap, 0, 0, canvasHorSize, canvasVerSize, 0x848484FF); //Gray
    }
    int baseHorSize = (canvasHorSize < drawSizeHor) ? canvasHorSize : drawSizeHor;
    int baseVerSize = (canvasVerSize < drawSizeVer) ? canvasVerSize : drawSizeVer;
    int baseX = (startX < 0) ? 0 : startX;
    int baseY = (startY < 0) ? 0 : startY;
    int flags = FPDF_REVERSE_BYTE_ORDER;
    if (renderAnnot) {
        flags |= FPDF_ANNOT;
    }
    FPDFBitmap_FillRect(pdfBitmap, baseX, baseY, baseHorSize, baseVerSize, 0xFFFFFFFF); //White
    FPDF_RenderPageBitmap(pdfBitmap, page, startX, startY, drawSizeHor, drawSizeVer, 0, flags);
}

JNI_FUNC(void, PdfiumCore, nativeRenderPage)(JNI_ARGS, jlong pagePtr, jobject objSurface, jint dpi,
                                             jint startX, jint startY, jint drawSizeHor,
                                             jint drawSizeVer, jboolean renderAnnot) {
    ANativeWindow *nativeWindow = ANativeWindow_fromSurface(env, objSurface);
    if (nativeWindow == nullptr) {
        LOGE("Native window pointer null");
        return;
    }
    auto page = reinterpret_cast<FPDF_PAGE>(pagePtr);
    if (page == nullptr || nativeWindow == nullptr) {
        LOGE("Render page pointers invalid");
        return;
    }
    if (ANativeWindow_getFormat(nativeWindow) != WINDOW_FORMAT_RGBA_8888) {
        LOGD("Set format to RGBA_8888");
        ANativeWindow_setBuffersGeometry(nativeWindow, ANativeWindow_getWidth(nativeWindow),
                                         ANativeWindow_getHeight(nativeWindow),
                                         WINDOW_FORMAT_RGBA_8888);
    }
    ANativeWindow_Buffer buffer;
    int ret;
    if ((ret = ANativeWindow_lock(nativeWindow, &buffer, nullptr)) != 0) {
        LOGE("Locking native window failed: %s", strerror(ret * -1));
        return;
    }

    renderPageInternal(page, &buffer, (int) startX, (int) startY, buffer.width, buffer.height,
                       (int) drawSizeHor, (int) drawSizeVer, (bool) renderAnnot);
    ANativeWindow_unlockAndPost(nativeWindow);
    ANativeWindow_release(nativeWindow);
}

JNI_FUNC(void, PdfiumCore, nativeRenderPageBitmap)(JNI_ARGS, jlong pagePtr, jobject bitmap,
                                                   jint dpi, jint startX, jint startY,
                                                   jint drawSizeHor, jint drawSizeVer,
                                                   jboolean renderAnnot) {
    auto page = reinterpret_cast<FPDF_PAGE>(pagePtr);
    if (page == nullptr || bitmap == nullptr) {
        LOGE("Render page pointers invalid");
        return;
    }
    AndroidBitmapInfo info;
    int ret;
    if ((ret = AndroidBitmap_getInfo(env, bitmap, &info)) < 0) {
        LOGE("Fetching bitmap info failed: %s", strerror(ret * -1));
        return;
    }
    int canvasHorSize = info.width;
    int canvasVerSize = info.height;
    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888 &&
        info.format != ANDROID_BITMAP_FORMAT_RGB_565) {
        LOGE("Bitmap format must be RGBA_8888 or RGB_565");
        return;
    }
    void *addr;
    if ((ret = AndroidBitmap_lockPixels(env, bitmap, &addr)) != 0) {
        LOGE("Locking bitmap failed: %s", strerror(ret * -1));
        return;
    }
    void *tmp;
    int format;
    int sourceStride;
    if (info.format == ANDROID_BITMAP_FORMAT_RGB_565) {
        tmp = malloc(canvasVerSize * canvasHorSize * sizeof(rgb));
        sourceStride = canvasHorSize * sizeof(rgb);
        format = FPDFBitmap_BGR;
    } else {
        tmp = addr;
        sourceStride = info.stride;
        format = FPDFBitmap_BGRA;
    }

    FPDF_BITMAP pdfBitmap = FPDFBitmap_CreateEx(canvasHorSize, canvasVerSize, format, tmp,
                                                sourceStride);

    /*
     LOGD("Start X: %d", startX);
     LOGD("Start Y: %d", startY);
     LOGD("Canvas Hor: %d", canvasHorSize);
     LOGD("Canvas Ver: %d", canvasVerSize);
     LOGD("Draw Hor: %d", drawSizeHor);
     LOGD("Draw Ver: %d", drawSizeVer);
     */

    if (drawSizeHor < canvasHorSize || drawSizeVer < canvasVerSize) {
        FPDFBitmap_FillRect(pdfBitmap, 0, 0, canvasHorSize, canvasVerSize, 0x848484FF); //Gray
    }
    int baseHorSize = (canvasHorSize < drawSizeHor) ? canvasHorSize : (int) drawSizeHor;
    int baseVerSize = (canvasVerSize < drawSizeVer) ? canvasVerSize : (int) drawSizeVer;
    int baseX = (startX < 0) ? 0 : (int) startX;
    int baseY = (startY < 0) ? 0 : (int) startY;
    int flags = FPDF_REVERSE_BYTE_ORDER;
    if (renderAnnot) {
        flags |= FPDF_ANNOT;
    }
    FPDFBitmap_FillRect(pdfBitmap, baseX, baseY, baseHorSize, baseVerSize, 0xFFFFFFFF); //White

    FPDF_RenderPageBitmap(pdfBitmap, page, startX, startY, (int) drawSizeHor, (int) drawSizeVer, 0,
                          flags);
    if (info.format == ANDROID_BITMAP_FORMAT_RGB_565) {
        rgbBitmapTo565(tmp, sourceStride, addr, &info);
        free(tmp);
    }
    AndroidBitmap_unlockPixels(env, bitmap);
}

JNI_FUNC(jstring, PdfiumCore, nativeGetDocumentMetaText)(JNI_ARGS, jlong docPtr, jstring tag) {
    const char *ctag = env->GetStringUTFChars(tag, nullptr);
    if (ctag == nullptr) {
        return env->NewStringUTF("");
    }
    auto *doc = reinterpret_cast<DocumentFile *>(docPtr);
    size_t bufferLen = FPDF_GetMetaText(doc->pdfDocument, ctag, nullptr, 0);
    if (bufferLen <= 2) {
        return env->NewStringUTF("");
    }
    std::wstring text;
    FPDF_GetMetaText(doc->pdfDocument, ctag, WriteInto(&text, bufferLen + 1), bufferLen);
    env->ReleaseStringUTFChars(tag, ctag);
    return env->NewString((jchar *) text.c_str(), bufferLen / 2 - 1);
}

JNI_FUNC(jobject, PdfiumCore, nativeGetFirstChildBookmark)(JNI_ARGS, jlong docPtr,
                                                           jobject bookmarkPtr) {
    auto *doc = reinterpret_cast<DocumentFile *>(docPtr);
    FPDF_BOOKMARK parent;
    if (bookmarkPtr == nullptr) {
        parent = nullptr;
    } else {
        jclass longClass = env->GetObjectClass(bookmarkPtr);
        jmethodID longValueMethod = env->GetMethodID(longClass, "longValue", "()J");
        jlong ptr = env->CallLongMethod(bookmarkPtr, longValueMethod);
        parent = reinterpret_cast<FPDF_BOOKMARK>(ptr);
    }
    FPDF_BOOKMARK bookmark = FPDFBookmark_GetFirstChild(doc->pdfDocument, parent);
    if (bookmark == nullptr) {
        return nullptr;
    }
    return NewLong(env, reinterpret_cast<jlong>(bookmark));
}

JNI_FUNC(jobject, PdfiumCore, nativeGetSiblingBookmark)(JNI_ARGS, jlong docPtr, jlong bookmarkPtr) {
    auto *doc = reinterpret_cast<DocumentFile *>(docPtr);
    auto parent = reinterpret_cast<FPDF_BOOKMARK>(bookmarkPtr);
    FPDF_BOOKMARK bookmark = FPDFBookmark_GetNextSibling(doc->pdfDocument, parent);
    if (bookmark == nullptr) {
        return nullptr;
    }
    return NewLong(env, reinterpret_cast<jlong>(bookmark));
}

JNI_FUNC(jstring, PdfiumCore, nativeGetBookmarkTitle)(JNI_ARGS, jlong bookmarkPtr) {
    auto bookmark = reinterpret_cast<FPDF_BOOKMARK>(bookmarkPtr);
    size_t bufferLen = FPDFBookmark_GetTitle(bookmark, nullptr, 0);
    if (bufferLen <= 2) {
        return env->NewStringUTF("");
    }
    std::wstring title;
    FPDFBookmark_GetTitle(bookmark, WriteInto(&title, bufferLen + 1), bufferLen);
    return env->NewString((jchar *) title.c_str(), bufferLen / 2 - 1);
}

JNI_FUNC(jlong, PdfiumCore, nativeGetBookmarkDestIndex)(JNI_ARGS, jlong docPtr, jlong bookmarkPtr) {
    auto *doc = reinterpret_cast<DocumentFile *>(docPtr);
    auto bookmark = reinterpret_cast<FPDF_BOOKMARK>(bookmarkPtr);
    FPDF_DEST dest = FPDFBookmark_GetDest(doc->pdfDocument, bookmark);
    if (dest == nullptr) {
        return -1;
    }
    return (jlong) FPDFDest_GetDestPageIndex(doc->pdfDocument, dest);
}

JNI_FUNC(jlongArray, PdfiumCore, nativeGetPageLinks)(JNI_ARGS, jlong pagePtr) {
    auto page = reinterpret_cast<FPDF_PAGE>(pagePtr);
    int pos = 0;
    std::vector<jlong> links;
    FPDF_LINK link;
    while (FPDFLink_Enumerate(page, &pos, &link)) {
        links.push_back(reinterpret_cast<jlong>(link));
    }
    jlongArray result = env->NewLongArray(links.size());
    env->SetLongArrayRegion(result, 0, links.size(), &links[0]);
    return result;
}

JNI_FUNC(jobject, PdfiumCore, nativeGetDestPageIndex)(JNI_ARGS, jlong docPtr, jlong linkPtr) {
    auto *doc = reinterpret_cast<DocumentFile *>(docPtr);
    auto link = reinterpret_cast<FPDF_LINK>(linkPtr);
    FPDF_DEST dest = FPDFLink_GetDest(doc->pdfDocument, link);
    if (dest == nullptr) {
        return nullptr;
    }
    unsigned long index = FPDFDest_GetDestPageIndex(doc->pdfDocument, dest);
    return NewInteger(env, (jint) index);
}

JNI_FUNC(jstring, PdfiumCore, nativeGetLinkURI)(JNI_ARGS, jlong docPtr, jlong linkPtr) {
    auto *doc = reinterpret_cast<DocumentFile *>(docPtr);
    auto link = reinterpret_cast<FPDF_LINK>(linkPtr);
    FPDF_ACTION action = FPDFLink_GetAction(link);
    if (action == nullptr) {
        return nullptr;
    }
    size_t bufferLen = FPDFAction_GetURIPath(doc->pdfDocument, action, nullptr, 0);
    if (bufferLen <= 0) {
        return env->NewStringUTF("");
    }
    std::string uri;
    FPDFAction_GetURIPath(doc->pdfDocument, action, WriteInto(&uri, bufferLen), bufferLen);
    return env->NewStringUTF(uri.c_str());
}

JNI_FUNC(jobject, PdfiumCore, nativeGetLinkRect)(JNI_ARGS, jlong linkPtr) {
    auto link = reinterpret_cast<FPDF_LINK>(linkPtr);
    FS_RECTF fsRectF;
    FPDF_BOOL result = FPDFLink_GetAnnotRect(link, &fsRectF);
    if (!result) {
        return nullptr;
    }
    jclass clazz = env->FindClass("android/graphics/RectF");
    jmethodID constructorID = env->GetMethodID(clazz, "<init>", "(FFFF)V");
    return env->NewObject(clazz, constructorID, fsRectF.left, fsRectF.top, fsRectF.right,
                          fsRectF.bottom);
}

JNI_FUNC(jobject, PdfiumCore, nativePageCoordinateToDevice)(JNI_ARGS, jlong pagePtr, jint startX,
                                                            jint startY, jint sizeX, jint sizeY,
                                                            jint rotate, jdouble pageX,
                                                            jdouble pageY) {
    auto page = reinterpret_cast<FPDF_PAGE>(pagePtr);
    int deviceX, deviceY;
    FPDF_PageToDevice(page, startX, startY, sizeX, sizeY, rotate, pageX, pageY, &deviceX, &deviceY);
    jclass clazz = env->FindClass("android/graphics/Point");
    jmethodID constructorID = env->GetMethodID(clazz, "<init>", "(II)V");
    return env->NewObject(clazz, constructorID, deviceX, deviceY);
}

JNI_FUNC(jobject, PdfiumCore, nativeDeviceCoordinateToPage)(JNI_ARGS, jlong pagePtr, jint startX,
                                                            jint startY, jint sizeX, jint sizeY,
                                                            jint rotate, jint deviceX,
                                                            jint deviceY) {
    auto page = reinterpret_cast<FPDF_PAGE>(pagePtr);
    double pageX, pageY;
    FPDF_DeviceToPage(page, startX, startY, sizeX, sizeY, rotate, deviceX, deviceY, &pageX, &pageY);
    jclass clazz = env->FindClass("android/graphics/PointF");
    jmethodID constructorID = env->GetMethodID(clazz, "<init>", "(FF)V");
    return env->NewObject(clazz, constructorID, (float) pageX, (float) pageY);
}

static jlong loadTextPageInternal(JNIEnv *env, DocumentFile *doc, int textPageIndex) {
    try {
        if (doc == nullptr) throw std::runtime_error("Get page document null");
        auto page = reinterpret_cast<FPDF_PAGE>(loadPageInternal(env, doc, textPageIndex));
        if (page != nullptr) {
            FPDF_TEXTPAGE textPage = FPDFText_LoadPage(page);
            if (textPage == nullptr) {
                throw std::runtime_error("Loaded text page is null");
            }
            return reinterpret_cast<jlong>(textPage);
        } else {
            throw std::runtime_error("Load page null");
        }
    } catch (const char *msg) {
        LOGE("%s", msg);
        jniThrowException(env, "java/lang/IllegalStateException", "Cannot load text page");
        return -1;
    }
}

static void closeTextPageInternal(jlong textPagePtr) {
    FPDFText_ClosePage(reinterpret_cast<FPDF_TEXTPAGE>(textPagePtr));
}

JNI_FUNC(jlong, PdfiumCore, nativeLoadTextPage)(JNI_ARGS, jlong docPtr, jint pageIndex) {
    auto *doc = reinterpret_cast<DocumentFile *>(docPtr);
    return loadTextPageInternal(env, doc, (int) pageIndex);
}

JNI_FUNC(jlongArray, PdfiumCore, nativeLoadTextPages)(JNI_ARGS, jlong docPtr, jint fromIndex,
                                                      jint toIndex) {
    auto *doc = reinterpret_cast<DocumentFile *>(docPtr);
    if (toIndex < fromIndex) return nullptr;
    jlong pages[toIndex - fromIndex + 1];
    int i;
    for (i = 0; i <= (toIndex - fromIndex); i++) {
        pages[i] = loadTextPageInternal(env, doc, (i + fromIndex));
    }
    jlongArray javaPages = env->NewLongArray((jsize) (toIndex - fromIndex + 1));
    env->SetLongArrayRegion(javaPages, 0, (jsize) (toIndex - fromIndex + 1), (const jlong *) pages);
    return javaPages;
}

JNI_FUNC(void, PdfiumCore, nativeCloseTextPage)(JNI_ARGS, jlong textPagePtr) {
    closeTextPageInternal(textPagePtr);
}

JNI_FUNC(void, PdfiumCore, nativeCloseTextPages)(JNI_ARGS, jlongArray textPagesPtr) {
    int length = (int) (env->GetArrayLength(textPagesPtr));
    jlong *textPages = env->GetLongArrayElements(textPagesPtr, nullptr);
    int i;
    for (i = 0; i < length; i++) { closeTextPageInternal(textPages[i]); }
}

JNI_FUNC(jint, PdfiumCore, nativeTextCountChars)(JNI_ARGS, jlong textPagePtr) {
    auto textPage = reinterpret_cast<FPDF_TEXTPAGE>(textPagePtr);
    return (jint) FPDFText_CountChars(textPage);// FPDF_TEXTPAGE
}

JNI_FUNC(jint, PdfiumCore, nativeTextGetUnicode)(JNI_ARGS, jlong textPagePtr, jint index) {
    auto textPage = reinterpret_cast<FPDF_TEXTPAGE>(textPagePtr);
    return (jint) FPDFText_GetUnicode(textPage, (int) index);
}

JNI_FUNC(jdoubleArray, PdfiumCore, nativeTextGetCharBox)(JNI_ARGS, jlong textPagePtr, jint index) {
    auto textPage = reinterpret_cast<FPDF_TEXTPAGE>(textPagePtr);
    jdoubleArray result = env->NewDoubleArray(4);
    if (result == nullptr) {
        return nullptr;
    }
    double fill[4];
    FPDFText_GetCharBox(textPage, (int) index, &fill[0], &fill[1], &fill[2], &fill[3]);
    env->SetDoubleArrayRegion(result, 0, 4, (jdouble *) fill);
    return result;
}

JNI_FUNC(jint, PdfiumCore, nativeTextGetCharIndexAtPos)(JNI_ARGS, jlong textPtr, jdouble posX,
                                                        jdouble posY, jdouble tolX, jdouble tolY) {
    return FPDFText_GetCharIndexAtPos((FPDF_TEXTPAGE) textPtr, posX, posY, tolX, tolY);
}

JNI_FUNC(jint, PdfiumCore, nativeTextGetText)(JNI_ARGS, jlong textPagePtr, jint start_index,
                                              jint count, jshortArray result) {
    auto textPage = reinterpret_cast<FPDF_TEXTPAGE>(textPagePtr);
    jboolean isCopy = 0;
    auto *arr = (unsigned short *) env->GetShortArrayElements(result, &isCopy);
    jint output = (jint) FPDFText_GetText(textPage, (int) start_index, (int) count, arr);
    if (isCopy) {
        env->SetShortArrayRegion(result, 0, output, (jshort *) arr);
        env->ReleaseShortArrayElements(result, (jshort *) arr, JNI_ABORT);
    }
    return output;
}

JNI_FUNC(jint, PdfiumCore, nativeTextCountRects)(JNI_ARGS, jlong textPagePtr, jint start_index,
                                                 jint count) {
    auto textPage = reinterpret_cast<FPDF_TEXTPAGE>(textPagePtr);
    return (jint) FPDFText_CountRects(textPage, (int) start_index, (int) count);
}

JNI_FUNC(jdoubleArray, PdfiumCore, nativeTextGetRect)(JNI_ARGS, jlong textPagePtr,
                                                      jint rect_index) {
    auto textPage = reinterpret_cast<FPDF_TEXTPAGE>(textPagePtr);
    jdoubleArray result = env->NewDoubleArray(4);
    if (result == nullptr) {
        return nullptr;
    }
    double fill[4];
    FPDFText_GetRect(textPage, (int) rect_index, &fill[0], &fill[1], &fill[2], &fill[3]);
    env->SetDoubleArrayRegion(result, 0, 4, (jdouble *) fill);
    return result;
}


JNI_FUNC(jint, PdfiumCore, nativeTextGetBoundedTextLength)(JNI_ARGS, jlong textPagePtr,
                                                           jdouble left, jdouble top, jdouble right,
                                                           jdouble bottom) {
    auto textPage = reinterpret_cast<FPDF_TEXTPAGE>(textPagePtr);
    return (jint) FPDFText_GetBoundedText(textPage, (double) left, (double) top, (double) right,
                                          (double) bottom, nullptr, 0);
}

JNI_FUNC(jint, PdfiumCore, nativeTextGetBoundedText)(JNI_ARGS, jlong textPagePtr, jdouble left,
                                                     jdouble top, jdouble right, jdouble bottom,
                                                     jshortArray arr) {
    auto textPage = reinterpret_cast<FPDF_TEXTPAGE>(textPagePtr);
    jboolean isCopy = 0;
    unsigned short *buffer = nullptr;
    int bufLen = 0;
    if (arr != nullptr) {
        buffer = (unsigned short *) env->GetShortArrayElements(arr, &isCopy);
        bufLen = env->GetArrayLength(arr);
    }
    jint output = (jint) FPDFText_GetBoundedText(textPage, (double) left, (double) top,
                                                 (double) right, (double) bottom, buffer, bufLen);
    if (isCopy) {
        env->SetShortArrayRegion(arr, 0, output, (jshort *) buffer);
        env->ReleaseShortArrayElements(arr, (jshort *) buffer, JNI_ABORT);
    }
    return output;
}

//////////////////////////////////////////
// Begin PDF SDK Search
//////////////////////////////////////////

unsigned short *convertWideString(JNIEnv *env, jstring query) {

    std::wstring value;
    const jchar *raw = env->GetStringChars(query, nullptr);
    jsize len = env->GetStringLength(query);
    value.assign(raw, raw + len);
    env->ReleaseStringChars(query, raw);

    size_t length = sizeof(uint16_t) * (value.length() + 1);
    auto *result = static_cast<unsigned short *>(malloc(length));
    char *ptr = reinterpret_cast<char *>(result);
    size_t i = 0;
    for (wchar_t w : value) {
        ptr[i++] = w & 0xff;
        ptr[i++] = (w >> 8) & 0xff;
    }
    ptr[i++] = 0;
    ptr[i] = 0;

    return result;
}

JNI_FUNC(jlong, PdfiumCore, nativeSearchStart)(JNI_ARGS, jlong textPagePtr, jstring query,
                                               jboolean matchCase, jboolean matchWholeWord) {
    // convert jstring to UTF-16LE encoded wide strings
    unsigned short *pQuery = convertWideString(env, query);
    auto textPage = reinterpret_cast<FPDF_TEXTPAGE>(textPagePtr);
    FPDF_SCHHANDLE search;
    unsigned long flags = 0;
    if (matchCase) {
        flags = FPDF_MATCHCASE;
    }
    if (matchWholeWord) {
        flags = flags | FPDF_MATCHWHOLEWORD;
    }
    search = FPDFText_FindStart(textPage, pQuery, flags, 0);
    return reinterpret_cast<jlong>(search);
}

JNI_FUNC(void, PdfiumCore, nativeSearchStop)(JNI_ARGS, jlong searchHandlePtr) {
    auto search = reinterpret_cast<FPDF_SCHHANDLE>(searchHandlePtr);
    FPDFText_FindClose(search);
}

JNI_FUNC(jboolean, PdfiumCore, nativeSearchNext)(JNI_ARGS, jlong searchHandlePtr) {
    auto search = reinterpret_cast<FPDF_SCHHANDLE>(searchHandlePtr);
    FPDF_BOOL result = FPDFText_FindNext(search);
    return result ? JNI_TRUE : JNI_FALSE;
}

JNI_FUNC(jboolean, PdfiumCore, nativeSearchPrev)(JNI_ARGS, jlong searchHandlePtr) {
    auto search = reinterpret_cast<FPDF_SCHHANDLE>(searchHandlePtr);
    FPDF_BOOL result = FPDFText_FindPrev(search);
    return result ? JNI_TRUE : JNI_FALSE;
}

JNI_FUNC(jint, PdfiumCore, nativeGetCharIndexOfSearchResult)(JNI_ARGS, jlong searchHandlePtr) {
    auto search = reinterpret_cast<FPDF_SCHHANDLE>(searchHandlePtr);
    return FPDFText_GetSchResultIndex(search);
}

JNI_FUNC(jint, PdfiumCore, nativeCountSearchResult)(JNI_ARGS, jlong searchHandlePtr) {
    auto search = reinterpret_cast<FPDF_SCHHANDLE>(searchHandlePtr);
    return FPDFText_GetSchCount(search);
}

//////////////////////////////////////////
// Begin PDF Annotation api
//////////////////////////////////////////
JNI_FUNC(jlong, PdfiumCore, nativeAddTextAnnotation)(JNI_ARGS, jlong docPtr, int page_index,
                                                     jstring text_, jintArray color_,
                                                     jintArray bound_) {

    FPDF_PAGE page;
    auto *doc = reinterpret_cast<DocumentFile *>(docPtr);
    int pagePtr = loadPageInternal(env, doc, page_index);
    if (pagePtr == -1) {
        return -1;
    } else {
        page = reinterpret_cast<FPDF_PAGE>(pagePtr);
    }

    // Get the bound array
    jint *bounds = env->GetIntArrayElements(bound_, nullptr);
    int boundsLen = (int) (env->GetArrayLength(bound_));
    if (boundsLen != 4) {
        return -1;
    }

    // Set the annotation rectangle.
    FS_RECTF rect;
    rect.left = bounds[0];
    rect.top = bounds[1];
    rect.right = bounds[2];
    rect.bottom = bounds[3];

    // Get the text color
    unsigned int R, G, B, A;
    jint *colors = env->GetIntArrayElements(color_, nullptr);
    int colorsLen = (int) (env->GetArrayLength(color_));
    if (colorsLen == 4) {
        R = colors[0];
        G = colors[1];
        B = colors[2];
        A = colors[3];
    } else {
        R = 51u;
        G = 102u;
        B = 153u;
        A = 204u;
    }

    // Add a text annotation to the page.
    FPDF_ANNOTATION annot = FPDFPage_CreateAnnot(page, FPDF_ANNOT_TEXT);

    // set the rectangle of the annotation
    FPDFAnnot_SetRect(annot, &rect);
    env->ReleaseIntArrayElements(bound_, bounds, 0);

    // Set the color of the annotation.
    FPDFAnnot_SetColor(annot, FPDFANNOT_COLORTYPE_Color, R, G, B, A);
    env->ReleaseIntArrayElements(color_, colors, 0);

    // Set the content of the annotation.
    unsigned short *kContents = convertWideString(env, text_);
    FPDFAnnot_SetStringValue(annot, kContentsKey, kContents);

    // save page
    FPDF_DOCUMENT pdfDoc = doc->pdfDocument;
    if (!FPDF_SaveAsCopy(pdfDoc, nullptr, FPDF_INCREMENTAL)) {
        return -1;
    }

    // close page
    closePageInternal(pagePtr);

    // reload page
    pagePtr = loadPageInternal(env, doc, page_index);

    jclass clazz = env->FindClass("com/ahmer/afzal/pdfium/PdfiumCore");
    jmethodID callback = env->GetMethodID(clazz, "onAnnotationAdded",
                                          "(Ljava/lang/Integer;)Ljava/lang/Long;");
    env->CallObjectMethod(thiz, callback, page_index, pagePtr);

    return reinterpret_cast<jlong>(annot);
}

// Add method for insert image
JNI_FUNC(void, PdfiumCore, nativeInsertImage)(JNI_ARGS, jlong docPtr, jint pageIndex,
                                              jobject bitmap, jfloat a, jfloat b, jfloat c,
                                              jfloat d, jfloat e, jfloat f) {
    auto *doc = reinterpret_cast<DocumentFile *>(docPtr);
    FPDF_PAGE page = FPDF_LoadPage(doc->pdfDocument, pageIndex);
    if (page == nullptr) {
        LOGE("nativeInsertImage: Loaded page is null");
        return;
    }

    AndroidBitmapInfo info;
    int ret;
    if ((ret = AndroidBitmap_getInfo(env, bitmap, &info)) < 0) {
        LOGE("Fetching bitmap info failed: %s", strerror(ret * -1));
        return;
    }

    int w = info.width;
    int h = info.height;

    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        LOGE("Bitmap format must be RGBA_8888");
        return;
    }

    void *addr;
    if ((ret = AndroidBitmap_lockPixels(env, bitmap, &addr)) != 0) {
        LOGE("Locking bitmap failed: %s", strerror(ret * -1));
        return;
    }
    auto *oldAddr = static_cast<unsigned char *>(addr);

    unsigned char *tmp;
    tmp = static_cast<unsigned char *>(malloc(h * w * sizeof(uint8_t) * 4));

    //convert data
    for (int ih = 0; ih < h; ++ih) {
        for (int iw = 0; iw < w; ++iw) {
            int i = ih * w + iw;
            int idx = i * 4;
            //argb -> bgra
            tmp[idx] = oldAddr[idx + 3];
            tmp[idx + 1] = oldAddr[idx + 2];
            tmp[idx + 2] = oldAddr[idx + 1];
            tmp[idx + 3] = oldAddr[idx];
        }
    }

    FPDF_BITMAP pdfBitmap = FPDFBitmap_CreateEx(w, h, FPDFBitmap_BGRA, tmp, info.stride);

    auto imgObj = FPDFPageObj_NewImageObj(doc->pdfDocument);
    FPDFImageObj_SetBitmap(&page, 1, imgObj, pdfBitmap);
    FPDFPageObj_Transform(imgObj, a, b, c, d, e, f);
    FPDFPage_InsertObject(page, imgObj);
}

// Add method for save pdf
JNI_FUNC(void, PdfiumCore, nativeSavePdf)(JNI_ARGS, jlong docPtr, jstring path,
                                          jboolean incremental) {
    auto str = env->GetStringUTFChars(path, nullptr);
    //clear and allow write
    auto pFile = fopen(str, "wb+");
    auto *doc = reinterpret_cast<DocumentFile *>(docPtr);

    PdfToFdWriter writer{};
    writer.dstFd = fileno(pFile);
    writer.WriteBlock = &writeBlock;
    FPDF_BOOL success = FPDF_SaveAsCopy(doc->pdfDocument, &writer,
                                        incremental ? FPDF_INCREMENTAL : FPDF_NO_INCREMENTAL);
    fclose(pFile);
    if (!success) {
        jniThrowExceptionFmt(env, "java/io/IOException",
                             "Cannot write to fd. Error: %d", errno);
    }
}
}//extern C

#pragma clang diagnostic pop
