#include <jni.h>
#include <string>

#include <android/bitmap.h>
#include <android/log.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>

#include <fpdf_doc.h>
#include <fpdf_edit.h>
#include <fpdf_save.h>
#include <fpdf_text.h>
#include <fpdf_transformpage.h>
#include <fpdf_formfill.h>
#include <fpdfview.h>

#include "include/util.h"
#include "fpdf_annot.h"
#include <Mutex.h>
#include <mutex>
#include <vector>

extern "C" {
#include <stdio.h>
#include <string.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <unistd.h>
}

static std::mutex sLibraryLock;
static int sLibraryReferenceCount = 0;
JavaVM *javaVm;

static void initLibraryIfNeed() {
    const std::lock_guard<std::mutex> lock(sLibraryLock);
    if (sLibraryReferenceCount == 0) {
        LOGD("Init FPDF library");
        FPDF_InitLibrary();
    }
    sLibraryReferenceCount++;
    sLibraryLock.unlock();
}

static void destroyLibraryIfNeed() {
    const std::lock_guard<std::mutex> lock(sLibraryLock);
    sLibraryReferenceCount--;
    LOGD("sLibraryReferenceCount %d", sLibraryReferenceCount);
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

bool jniAttachCurrentThread(JNIEnv **env, bool *attachedOut) {
    JavaVMAttachArgs jvmArgs;
    jvmArgs.version = JNI_VERSION_1_6;

    bool attached = false;
    if (javaVm->GetEnv((void **) env, JNI_VERSION_1_6) == JNI_EDETACHED) {
        if (javaVm->AttachCurrentThread(env, &jvmArgs) != JNI_OK) {
            LOGE("Cannot attach current thread");
            return false;
        }
        attached = true;
    } else {
        attached = false;
    }
    *attachedOut = attached;
    return true;
}

bool jniDetachCurrentThread(bool attached) {
    if (attached && javaVm->DetachCurrentThread() != JNI_OK) {
        LOGE("Cannot detach current thread");
        return false;
    }
    return true;
}

class DocumentFile {

public:
    FPDF_DOCUMENT pdfDocument = nullptr;

public:
    jobject nativeSourceBridgeGlobalRef = nullptr;
    jbyte *cDataCopy = nullptr;

    DocumentFile() { initLibraryIfNeed(); }

    ~DocumentFile();
};

DocumentFile::~DocumentFile() {
    if (pdfDocument != nullptr) {
        FPDF_CloseDocument(pdfDocument);
        pdfDocument = nullptr;
    }
    if (cDataCopy != nullptr) {
        delete[] cDataCopy;
        cDataCopy = nullptr;
    }
    if (nativeSourceBridgeGlobalRef != nullptr) {
        JNIEnv *env;
        bool attached;
        if (jniAttachCurrentThread(&env, &attached)) {
            env->DeleteGlobalRef(nativeSourceBridgeGlobalRef);
            nativeSourceBridgeGlobalRef = nullptr; // Clear after deletion
            jniDetachCurrentThread(attached);
        } else {
            // Log error: Failed to attach thread, potential ref leak
            LOGE("Failed to attach JNI thread to delete global ref");
        }
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

static char *getErrorDescription(const unsigned long error) {
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
    char msgBuf[1024];

    va_list args;
    va_start(args, fmt);
    vsnprintf(msgBuf, sizeof(msgBuf), fmt, args);
    va_end(args);

    jclass exceptionClass = env->FindClass(className);
    return env->ThrowNew(exceptionClass, msgBuf);
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

jlong loadTextPageInternal(JNIEnv *env, DocumentFile *doc, jlong pagePtr) {
    try {
        if (doc == nullptr) throw std::runtime_error("Get page document null");

        auto page = reinterpret_cast<FPDF_PAGE>(pagePtr);
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

class FileWrite : public FPDF_FILEWRITE {
public:
    jobject callbackObject;
    jmethodID callbackMethodID;
    _JNIEnv *env;

    static int
    WriteBlockCallback(FPDF_FILEWRITE *pFileWrite, const void *data, unsigned long size) {
        auto *pThis = reinterpret_cast<FileWrite *>(pFileWrite);
        _JNIEnv *env = pThis->env;
        //Convert the native array to Java array.
        jbyteArray a = env->NewByteArray((int) size);
        if (a != nullptr) {
            env->SetByteArrayRegion(a, 0, (int) size, (const jbyte *) data);
            return env->CallIntMethod(pThis->callbackObject, pThis->callbackMethodID, a);
        }
        return -1;
    }
};

extern "C" { //For JNI support

int getBlock(void *param, unsigned long position, unsigned char *outBuffer, unsigned long size) {
    const int fd = reinterpret_cast<intptr_t>(param);
    const int readCount = pread(fd, outBuffer, size, position);
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
    std::unique_ptr<DocumentFile> docFile(new DocumentFile());

    FPDF_FILEACCESS loader;
    loader.m_FileLen = fileLength;
    loader.m_Param = reinterpret_cast<void *>(intptr_t(fd));
    loader.m_GetBlock = &getBlock;

    const char *cPassword = nullptr;
    if (password != nullptr) {
        cPassword = env->GetStringUTFChars(password, nullptr);
        if (env->ExceptionCheck()) {
            return -1; // docFile auto-released by unique_ptr
        }
    }

    FPDF_DOCUMENT document = FPDF_LoadCustomDocument(&loader, cPassword);
    if (cPassword != nullptr) {
        env->ReleaseStringUTFChars(password, cPassword);
    }

    if (!document) {
        const unsigned long errorNum = FPDF_GetLastError();
        if (errorNum == FPDF_ERR_PASSWORD) {
            jniThrowException(env, "com/ahmer/pdfium/PdfPasswordException",
                              "Password required or incorrect password.");
        } else {
            char *error = getErrorDescription(errorNum);
            jniThrowExceptionFmt(env, "java/io/IOException", "Cannot create document: %s", error);
            free(error);
        }
        return -1;
    }
    docFile->pdfDocument = document;
    return reinterpret_cast<jlong>(docFile.release()); // Transfer ownership
}

JNI_FUNC(jlong, PdfiumCore, nativeOpenMemDocument)(JNI_ARGS, jbyteArray data, jstring password) {
    std::unique_ptr<DocumentFile> docFile(new DocumentFile());

    const char *cPassword = nullptr;
    if (password != nullptr) {
        cPassword = env->GetStringUTFChars(password, nullptr);
    }

    jbyte *cData = env->GetByteArrayElements(data, nullptr);
    int size = (int) env->GetArrayLength(data);
    auto *cDataCopy = new jbyte[size];
    env->GetByteArrayRegion(data, 0, size, cDataCopy);
    FPDF_DOCUMENT document = FPDF_LoadMemDocument(reinterpret_cast<const void *>(cDataCopy), size,
                                                  cPassword);
    env->ReleaseByteArrayElements(data, cData, JNI_ABORT);
    if (cPassword != nullptr) {
        env->ReleaseStringUTFChars(password, cPassword);
    }

    if (!document) {
        const unsigned long errorNum = FPDF_GetLastError();
        if (errorNum == FPDF_ERR_PASSWORD) {
            jniThrowException(env, "com/ahmer/pdfium/PdfPasswordException",
                              "Password required or incorrect password.");
        } else {
            char *error = getErrorDescription(errorNum);
            jniThrowExceptionFmt(env, "java/io/IOException", "Cannot create document: %s", error);
            free(error);
        }
        return -1;
    }
    docFile->pdfDocument = document;
    return reinterpret_cast<jlong>(docFile.release());
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

static void closeTextPageInternal(jlong textPagePtr) {
    FPDFText_ClosePage(reinterpret_cast<FPDF_TEXTPAGE>(textPagePtr));
}

static void renderPageInternal(FPDF_PAGE page, ANativeWindow_Buffer *windowBuffer, int startX, int startY,
                               int canvasHorSize, int canvasVerSize, int drawSizeHor, int drawSizeVer,
                               bool annotation) {
    FPDF_BITMAP pdfBitmap = FPDFBitmap_CreateEx(canvasHorSize, canvasVerSize,
                                                FPDFBitmap_BGRA, windowBuffer->bits,
                                                (int) (windowBuffer->stride) * 4);

    if (drawSizeHor < canvasHorSize || drawSizeVer < canvasVerSize) {
        FPDFBitmap_FillRect(pdfBitmap, 0, 0, canvasHorSize, canvasVerSize, 0x848484FF); //Gray
    }
    int baseHorSize = (canvasHorSize < drawSizeHor) ? canvasHorSize : drawSizeHor;
    int baseVerSize = (canvasVerSize < drawSizeVer) ? canvasVerSize : drawSizeVer;
    int baseX = (startX < 0) ? 0 : startX;
    int baseY = (startY < 0) ? 0 : startY;
    int flags = FPDF_REVERSE_BYTE_ORDER;

    if (startX + baseHorSize > drawSizeHor) {
        baseHorSize = drawSizeHor - startX;
    }
    if (startY + baseVerSize > drawSizeVer) {
        baseVerSize = drawSizeVer - startY;
    }
    if (startX + drawSizeHor > canvasHorSize) {
        drawSizeHor = canvasHorSize - startX;
    }
    if (startY + drawSizeVer > canvasVerSize) {
        drawSizeVer = canvasVerSize - startY;
    }

    if (annotation) {
        flags |= FPDF_ANNOT;
    }

    FPDFBitmap_FillRect(pdfBitmap, baseX, baseY, baseHorSize, baseVerSize, 0xFFFFFFFF); //White
    FPDF_RenderPageBitmap(pdfBitmap, page, startX, startY, drawSizeHor, drawSizeVer, 0, flags);
}

/*
 * PdfDocument
 */
JNI_PdfDocument(jint, PdfiumCore, nativeGetPageCount)(JNI_ARGS, jlong docPtr) {
    auto *doc = reinterpret_cast<DocumentFile *>(docPtr);
    return (jint) FPDF_GetPageCount(doc->pdfDocument);
}

JNI_PdfDocument(jlong, PdfiumCore, nativeLoadPage)(JNI_ARGS, jlong docPtr, jint pageIndex) {
    auto *doc = reinterpret_cast<DocumentFile *>(docPtr);
    return loadPageInternal(env, doc, (int) pageIndex);
}

JNI_PdfDocument(void, PdfiumCore, nativeDeletePage)(JNI_ARGS, jlong docPtr, jint pageIndex) {
    auto *doc = reinterpret_cast<DocumentFile *>(docPtr);
    if (doc == nullptr) throw std::runtime_error("Get page document null");

    FPDF_DOCUMENT pdfDoc = doc->pdfDocument;
    if (pdfDoc != nullptr) {
        FPDFPage_Delete(pdfDoc, (int) pageIndex);
    }
}

JNI_PdfDocument(void, PdfiumCore, nativeCloseDocument)(JNI_ARGS, jlong docPtr) {
    auto *doc = reinterpret_cast<DocumentFile *>(docPtr);
    // The destructor will close the document
    delete doc;
}

JNI_PdfDocument(jlongArray, PdfiumCore, nativeLoadPages)(JNI_ARGS, jlong docPtr, jint fromIndex,
                                                         jint toIndex) {
    auto *doc = reinterpret_cast<DocumentFile *>(docPtr);

    // Check for invalid index range
    if (toIndex < fromIndex) {
        return nullptr;
    }

    // Calculate the number of pages to load
    const jint size = toIndex - fromIndex + 1;
    std::vector<jlong> pages(size);

    // Load pages into the vector
    for (jint i = 0; i < size; i++) {
        pages[i] = loadPageInternal(env, doc, static_cast<int>(fromIndex + i));
    }
    // Create Java array of appropriate size
    jlongArray javaPages = env->NewLongArray(size);
    if (javaPages == nullptr) {
        // Handle out-of-memory error (optional)
        return nullptr;
    }

    // Copy data from native vector to Java array
    env->SetLongArrayRegion(javaPages, 0, size, pages.data());
    return javaPages;
}

JNI_PdfDocument(jstring, PdfiumCore, nativeGetDocumentMetaText)(JNI_ARGS, jlong docPtr, jstring tag) {
    const char *cTag = env->GetStringUTFChars(tag, nullptr);
    if (cTag == nullptr) {
        return env->NewStringUTF("");
    }
    auto *doc = reinterpret_cast<DocumentFile *>(docPtr);

    size_t bufferLen = FPDF_GetMetaText(doc->pdfDocument, cTag, nullptr, 0);
    if (bufferLen <= 2) {
        return env->NewStringUTF("");
    }
    std::wstring text;
    FPDF_GetMetaText(doc->pdfDocument, cTag, WriteInto(&text, bufferLen + 1), bufferLen);
    env->ReleaseStringUTFChars(tag, cTag);
    return env->NewString((jchar *) text.c_str(), bufferLen / 2 - 1);
}

JNI_PdfDocument(jlong, PdfiumCore, nativeGetFirstChildBookmark)(JNI_ARGS, jlong docPtr, jlong bookmarkPtr) {
    auto *doc = reinterpret_cast<DocumentFile *>(docPtr);
    FPDF_BOOKMARK parent;
    if (bookmarkPtr == 0) {
        parent = nullptr;
    } else {
        parent = reinterpret_cast<FPDF_BOOKMARK>(bookmarkPtr);
    }
    FPDF_BOOKMARK bookmark = FPDFBookmark_GetFirstChild(doc->pdfDocument, parent);
    if (bookmark == nullptr) {
        return 0;
    }
    return reinterpret_cast<jlong>(bookmark);
}

JNI_PdfDocument(jlong, PdfiumCore, nativeGetSiblingBookmark)(JNI_ARGS, jlong docPtr, jlong bookmarkPtr) {

    auto *doc = reinterpret_cast<DocumentFile *>(docPtr);
    auto parent = reinterpret_cast<FPDF_BOOKMARK>(bookmarkPtr);
    FPDF_BOOKMARK bookmark = FPDFBookmark_GetNextSibling(doc->pdfDocument, parent);
    if (bookmark == nullptr) {
        return 0;
    }
    return reinterpret_cast<jlong>(bookmark);
}

JNI_PdfDocument(jlong, PdfiumCore, nativeLoadTextPage)(JNI_ARGS, jlong docPtr, jlong pagePtr) {
    auto *doc = reinterpret_cast<DocumentFile *>(docPtr);
    return loadTextPageInternal(env, doc, pagePtr);
}

JNI_PdfDocument(jstring, PdfiumCore, nativeGetBookmarkTitle)(JNI_ARGS, jlong bookmarkPtr) {

    auto bookmark = reinterpret_cast<FPDF_BOOKMARK>(bookmarkPtr);
    int bufferLen = FPDFBookmark_GetTitle(bookmark, nullptr, 0);
    if (bufferLen <= 2) {
        return env->NewStringUTF("");
    }
    std::wstring title;
    FPDFBookmark_GetTitle(bookmark, WriteInto(&title, bufferLen + 1), bufferLen);
    return env->NewString((jchar *) title.c_str(), bufferLen / 2 - 1);
}

JNI_PdfDocument(jboolean, PdfiumCore, nativeSaveAsCopy)(JNI_ARGS, jlong docPtr, jobject callback,
                                                        jint flags) {
    jclass callbackClass = env->FindClass("com/ahmer/pdfium/PdfWriteCallback");
    if (callback != nullptr && env->IsInstanceOf(callback, callbackClass)) {
        //Setup the callback to Java.
        FileWrite fw = FileWrite();
        fw.version = 1;
        fw.FPDF_FILEWRITE::WriteBlock = FileWrite::WriteBlockCallback;
        fw.callbackObject = callback;
        fw.callbackMethodID = env->GetMethodID(callbackClass, "WriteBlock", "([B)I");
        fw.env = env;

        auto *doc = reinterpret_cast<DocumentFile *>(docPtr);
        return FPDF_SaveAsCopy(doc->pdfDocument, &fw, flags);
    }
    return false;
}

JNI_PdfDocument(jlong, PdfiumCore, nativeGetBookmarkDestIndex)(JNI_ARGS, jlong docPtr, jlong bookmarkPtr) {
    auto *doc = reinterpret_cast<DocumentFile *>(docPtr);
    auto bookmark = reinterpret_cast<FPDF_BOOKMARK>(bookmarkPtr);
    FPDF_DEST dest = FPDFBookmark_GetDest(doc->pdfDocument, bookmark);
    if (dest == nullptr) {
        return -1;
    }
    return (jlong) FPDFDest_GetDestPageIndex(doc->pdfDocument, dest);
}

JNI_PdfDocument(jintArray, PdfiumCore, nativeGetPageCharCounts)(JNI_ARGS, jlong docPtr) {
    auto *doc = reinterpret_cast<DocumentFile *>(docPtr);
    auto pageCount = FPDF_GetPageCount(doc->pdfDocument);

    std::vector<int> charCounts;

    for (int i = 0; i < pageCount; i++) {
        auto page = FPDF_LoadPage(doc->pdfDocument, i);
        auto textPage = FPDFText_LoadPage(page);
        auto charCount = FPDFText_CountChars(textPage);
        charCounts.push_back(charCount);
        FPDFText_ClosePage(textPage);
        FPDF_ClosePage(page);
    }
    jintArray result = env->NewIntArray(charCounts.size());
    env->SetIntArrayRegion(result, 0, charCounts.size(), &charCounts[0]);
    return result;
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

JNI_FUNC(void, PdfiumCore, nativeRenderPageBitmap)(JNI_ARGS, jlong docPtr, jlong pagePtr, jobject bitmap,
                                                   jint startX, jint startY, jint drawSizeHor,
                                                   jint drawSizeVer, jboolean annotation) {
    auto *doc = reinterpret_cast<DocumentFile *>(docPtr);
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

    if (drawSizeHor < canvasHorSize || drawSizeVer < canvasVerSize) {
        FPDFBitmap_FillRect(pdfBitmap, 0, 0, canvasHorSize, canvasVerSize, 0x848484FF); //Gray
    }
    int baseHorSize = (canvasHorSize < drawSizeHor) ? canvasHorSize : (int) drawSizeHor;
    int baseVerSize = (canvasVerSize < drawSizeVer) ? canvasVerSize : (int) drawSizeVer;
    int baseX = (startX < 0) ? 0 : (int) startX;
    int baseY = (startY < 0) ? 0 : (int) startY;
    int flags = FPDF_REVERSE_BYTE_ORDER;

    FPDF_FORMFILLINFO form_callbacks = {0};
    form_callbacks.version = 2;
    FPDF_FORMHANDLE form;

    if (annotation) {
        form = FPDFDOC_InitFormFillEnvironment(doc->pdfDocument, &form_callbacks);
        flags |= FPDF_ANNOT;
    }

    FPDFBitmap_FillRect(pdfBitmap, baseX, baseY, baseHorSize, baseVerSize, 0xFFFFFFFF); //White
    FPDF_RenderPageBitmap(pdfBitmap, page, startX, startY, (int) drawSizeHor,
                          (int) drawSizeVer, 0, flags);

    if (annotation) {
        FPDF_FFLDraw(form, pdfBitmap, page, startX, startY, (int) drawSizeHor,
                     (int) drawSizeVer, 0, FPDF_ANNOT);
        FPDFDOC_ExitFormFillEnvironment(form);
    }

    if (info.format == ANDROID_BITMAP_FORMAT_RGB_565) {
        rgbBitmapTo565(tmp, sourceStride, addr, &info);
        free(tmp);
    }
    AndroidBitmap_unlockPixels(env, bitmap);
}

JNI_FUNC(void, PdfiumCore, nativeRenderPage)(JNI_ARGS, jlong pagePtr, jobject objSurface, jint startX,
                                             jint startY, jint drawSizeHor, jint drawSizeVer,
                                             jboolean annotation) {
    ANativeWindow *nativeWindow = ANativeWindow_fromSurface(env, objSurface);
    if (nativeWindow == nullptr) {
        LOGE("native window pointer null");
        return;
    }
    auto page = reinterpret_cast<FPDF_PAGE>(pagePtr);

    if (page == nullptr) {
        LOGE("Render page pointers invalid");
        return;
    }

    if (ANativeWindow_getFormat(nativeWindow) != WINDOW_FORMAT_RGBA_8888) {
        LOGD("Set format to RGBA_8888");
        ANativeWindow_setBuffersGeometry(nativeWindow,
                                         ANativeWindow_getWidth(nativeWindow),
                                         ANativeWindow_getHeight(nativeWindow),
                                         WINDOW_FORMAT_RGBA_8888);
    }

    ANativeWindow_Buffer buffer;
    int ret;
    if ((ret = ANativeWindow_lock(nativeWindow, &buffer, nullptr)) != 0) {
        LOGE("Locking native window failed: %s", strerror(ret * -1));
        return;
    }

    renderPageInternal(page, &buffer, (int) startX, (int) startY,
                       buffer.width, buffer.height, (int) drawSizeHor,
                       (int) drawSizeVer, (bool) annotation);

    ANativeWindow_unlockAndPost(nativeWindow);
    ANativeWindow_release(nativeWindow);
}

JNI_FUNC(jobject, PdfiumCore, nativeGetPageSizeByIndex)(JNI_ARGS, jlong docPtr, jint pageIndex, jint dpi) {
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
    int widthInt = (int) (width * dpi / 72);
    int heightInt = (int) (height * dpi / 72);
    jclass clazz = env->FindClass("com/ahmer/pdfium/util/Size");
    if (clazz == nullptr) {
        LOGE("Size class not found");
        jniThrowException(env, "java/lang/IllegalStateException", "Size class not found");
        return nullptr;
    }
    jmethodID constructorID = env->GetMethodID(clazz, "<init>", "(II)V");
    if (constructorID == nullptr) {
        LOGE("Size constructor not found");
        jniThrowException(env, "java/lang/IllegalStateException", "Size constructor not found");
        return nullptr;
    }
    return env->NewObject(clazz, constructorID, widthInt, heightInt);
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

JNI_FUNC(jobject, PdfiumCore, nativePageCoordsToDevice)(JNI_ARGS, jlong pagePtr, jint startX, jint startY,
                                                        jint sizeX, jint sizeY, jint rotate, jdouble pageX,
                                                        jdouble pageY) {
    auto page = reinterpret_cast<FPDF_PAGE>(pagePtr);
    int deviceX, deviceY;
    FPDF_PageToDevice(page, startX, startY, sizeX, sizeY, rotate, pageX,
                      pageY, &deviceX, &deviceY);
    jclass clazz = env->FindClass("android/graphics/Point");
    jmethodID constructorID = env->GetMethodID(clazz, "<init>", "(II)V");
    return env->NewObject(clazz, constructorID, deviceX, deviceY);
}

JNI_FUNC(jobject, PdfiumCore, nativeDeviceCoordsToPage)(JNI_ARGS, jlong pagePtr, jint startX, jint startY,
                                                        jint sizeX, jint sizeY, jint rotate, jint deviceX,
                                                        jint deviceY) {
    auto page = reinterpret_cast<FPDF_PAGE>(pagePtr);
    double pageX, pageY;
    FPDF_DeviceToPage(page, startX, startY, sizeX, sizeY, rotate, deviceX, deviceY, &pageX, &pageY);
    jclass clazz = env->FindClass("android/graphics/PointF");
    jmethodID constructorID = env->GetMethodID(clazz, "<init>", "(FF)V");
    return env->NewObject(clazz, constructorID, (float) pageX, (float) pageY);
}

JNI_FUNC(jint, PdfiumCore, nativeGetDestPageIndex)(JNI_ARGS, jlong docPtr, jlong linkPtr) {
    auto *doc = reinterpret_cast<DocumentFile *>(docPtr);
    auto link = reinterpret_cast<FPDF_LINK>(linkPtr);
    FPDF_DEST dest = FPDFLink_GetDest(doc->pdfDocument, link);
    if (dest == nullptr) {
        return -1;
    }
    auto index = FPDFDest_GetDestPageIndex(doc->pdfDocument, dest);
    return (jint) index;
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
    return env->NewObject(clazz, constructorID, fsRectF.left, fsRectF.top, fsRectF.right, fsRectF.bottom);
}

JNI_FUNC(jint, PdfiumCore, nativeGetPageRotation)(JNI_ARGS, jlong pagePtr) {
    auto page = reinterpret_cast<FPDF_PAGE>(pagePtr);
    return (jint) FPDFPage_GetRotation(page);
}

JNI_FUNC(jlong, PdfiumCore, nativeGetLinkAtCoord)(JNI_ARGS, jlong pagePtr, jint width, jint height, jint posX,
                                                  jint posY) {
    double px, py;
    FPDF_DeviceToPage((FPDF_PAGE) pagePtr, 0, 0, width, height, 0, posX, posY, &px, &py);
    return (jlong) FPDFLink_GetLinkAtPoint((FPDF_PAGE) pagePtr, px, py);
}

/*
 * PdfTextPage
 */
JNI_PdfTextPage(jint, PdfiumCore, nativeGetFontSize)(JNI_ARGS, jlong pagePtr, jint charIndex) {
    auto textPage = reinterpret_cast<FPDF_TEXTPAGE>(pagePtr);
    return (jdouble) FPDFText_GetFontSize(textPage, charIndex);
}

JNI_PdfTextPage(void, PdfiumCore, nativeCloseTextPage)(JNI_ARGS, jlong pagePtr) {
    closeTextPageInternal(pagePtr);
}

JNI_PdfTextPage(jint, PdfiumCore, nativeTextCountChars)(JNI_ARGS, jlong textPagePtr) {
    auto textPage = reinterpret_cast<FPDF_TEXTPAGE>(textPagePtr);
    return (jint) FPDFText_CountChars(textPage);
}

JNI_PdfTextPage(jint, PdfiumCore, nativeTextGetText)(JNI_ARGS, jlong textPagePtr, jint startIndex, jint count,
                                                     jshortArray result) {
    auto textPage = reinterpret_cast<FPDF_TEXTPAGE>(textPagePtr);
    jboolean isCopy = 1;
    auto *arr = (unsigned short *) env->GetShortArrayElements(result, &isCopy);
    jint output = (jint) FPDFText_GetText(textPage, (int) startIndex, (int) count, arr);
    if (isCopy) {
        env->SetShortArrayRegion(result, 0, output, (jshort *) arr);
        env->ReleaseShortArrayElements(result, (jshort *) arr, JNI_ABORT);
    }
    return output;
}

JNI_PdfTextPage(jint, PdfiumCore, nativeTextGetTextByteArray)(JNI_ARGS, jlong textPagePtr, jint startIndex,
                                                              jint count, jbyteArray result) {
    auto textPage = reinterpret_cast<FPDF_TEXTPAGE>(textPagePtr);
    jboolean isCopy = JNI_FALSE;
    jbyte *arr = env->GetByteArrayElements(result, &isCopy);
    if (arr == nullptr) {
        // Handle potential JNI error (e.g., return -1 or throw exception)
        return -1;
    }

    std::vector<unsigned short> buffer(count);
    jint output = static_cast<jint>(FPDFText_GetText(textPage, static_cast<int>(startIndex),
                                                     static_cast<int>(count), buffer.data()));
    memcpy(arr, buffer.data(), count * sizeof(unsigned short));

    if (isCopy) {
        // If it was a copy, update the Java array and discard the native copy
        env->SetByteArrayRegion(result, 0, count * sizeof(unsigned short), arr);
        env->ReleaseByteArrayElements(result, arr, JNI_ABORT);
    } else {
        // If it was a direct pointer, commit changes without copying
        env->ReleaseByteArrayElements(result, arr, 0);
    }
    return output;
}

JNI_PdfTextPage(jint, PdfiumCore, nativeTextGetUnicode)(JNI_ARGS, jlong textPagePtr, jint index) {
    auto textPage = reinterpret_cast<FPDF_TEXTPAGE>(textPagePtr);
    return (jint) FPDFText_GetUnicode(textPage, (int) index);
}

JNI_PdfTextPage(jdoubleArray, PdfiumCore, nativeTextGetCharBox)(JNI_ARGS, jlong textPagePtr, jint index) {
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

JNI_PdfTextPage(jobject, PdfiumCore, nativeTextGetLooseCharBox)(JNI_ARGS, jlong textPagePtr, jint index) {
    auto textPage = reinterpret_cast<FPDF_TEXTPAGE>(textPagePtr);
    FS_RECTF fsRectF;
    FPDF_BOOL result = FPDFText_GetLooseCharBox(textPage, (int) index, &fsRectF);
    if (!result) {
        return nullptr;
    }
    jclass clazz = env->FindClass("android/graphics/RectF");
    jmethodID constructorID = env->GetMethodID(clazz, "<init>", "(FFFF)V");
    return env->NewObject(clazz, constructorID, fsRectF.left, fsRectF.top, fsRectF.right, fsRectF.bottom);
}

JNI_PdfTextPage(jint, PdfiumCore, nativeTextGetCharIndexAtPos)(JNI_ARGS, jlong textPagePtr, jdouble x,
                                                               jdouble y, jdouble xTolerance,
                                                               jdouble yTolerance) {
    auto textPage = reinterpret_cast<FPDF_TEXTPAGE>(textPagePtr);
    return (jint) FPDFText_GetCharIndexAtPos(textPage, (double) x, (double) y,
                                             (double) xTolerance, (double) yTolerance);
}

JNI_PdfTextPage(jint, PdfiumCore, nativeTextCountRects)(JNI_ARGS, jlong textPagePtr, jint startIndex,
                                                        jint count) {
    auto textPage = reinterpret_cast<FPDF_TEXTPAGE>(textPagePtr);
    return (jint) FPDFText_CountRects(textPage, (int) startIndex, (int) count);
}

JNI_PdfTextPage(jdoubleArray, PdfiumCore, nativeTextGetRect)(JNI_ARGS, jlong textPagePtr, jint rectIndex) {
    auto textPage = reinterpret_cast<FPDF_TEXTPAGE>(textPagePtr);
    jdoubleArray result = env->NewDoubleArray(4);
    if (result == nullptr) {
        return nullptr;
    }
    double fill[4];
    FPDFText_GetRect(textPage, (int) rectIndex, &fill[0], &fill[1], &fill[2], &fill[3]);
    env->SetDoubleArrayRegion(result, 0, 4, (jdouble *) fill);
    return result;
}

JNI_PdfTextPage(jint, PdfiumCore, nativeTextGetBoundedText)(JNI_ARGS, jlong textPagePtr, jdouble left,
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

JNI_PdfTextPage(jlong, PdfiumCore, nativeFindStart)(JNI_ARGS, jlong textPagePtr, jstring findWhat, jint flags,
                                                    jint startIndex) {
    auto textPage = reinterpret_cast<FPDF_TEXTPAGE>(textPagePtr);

    const jchar *raw = env->GetStringChars(findWhat, nullptr);
    if (raw == nullptr) {
        // Handle error, possibly throw an exception
        return 0;
    }

    jsize len = env->GetStringLength(findWhat);
    std::u16string result(raw, raw + len);

    auto handle = FPDFText_FindStart(textPage, (FPDF_WIDESTRING) result.c_str(), flags,
                                     startIndex);

    env->ReleaseStringChars(findWhat, raw);

    return (jlong) handle;
}

JNI_PdfTextPage(jlong, PdfiumCore, nativeLoadWebLink)(JNI_ARGS, jlong textPagePtr) {
    auto textPage = reinterpret_cast<FPDF_TEXTPAGE>(textPagePtr);
    auto handle = FPDFLink_LoadWebLinks(textPage);
    return (jlong) handle;
}

JNI_PdfTextPage(jdoubleArray, PdfiumCore, nativeTextGetRects)(JNI_ARGS, jlong pageLinkPtr,
                                                              jintArray wordRanges) {
    auto textPage = reinterpret_cast<FPDF_TEXTPAGE>(pageLinkPtr);
    jsize numRanges = env->GetArrayLength(wordRanges) / 2;

    // Get the ranges array
    jint *ranges = env->GetIntArrayElements(wordRanges, nullptr);

    // Create a vector to store the data
    std::vector<double> data;

    // Iterate through the ranges
    for (jsize i = 0; i < numRanges; ++i) {
        // Get the start and length
        jint start = ranges[i * 2];
        jint length = ranges[i * 2 + 1];

        // Get the number of rectangles in the range
        int rectCount = FPDFText_CountRects(textPage, start, length);

        // Get the rectangles
        for (int j = 0; j < rectCount; ++j) {
            double left, top, right, bottom;
            FPDFText_GetRect(textPage, j, &left, &top, &right, &bottom);

            // Add the rectangle to the data vector (left, top, right, bottom)
            data.push_back(left);
            data.push_back(top);
            data.push_back(right);
            data.push_back(bottom);

            // Add the range to the data vector (start, length)
            data.push_back(static_cast<double>(start));
            data.push_back(static_cast<double>(length));
        }
    }

    // Release the ranges array
    env->ReleaseIntArrayElements(wordRanges, ranges, JNI_ABORT);

    // Create a jdoubleArray and copy the data
    jdoubleArray result = env->NewDoubleArray(data.size());
    if (result == nullptr) {
        return nullptr; // Out of memory error
    }
    env->SetDoubleArrayRegion(result, 0, data.size(), data.data());
    return result;
}

JNI_FindResult(jlong, PdfiumCore, nativeFindNext)(JNI_ARGS, jlong findHandle) {
    auto handle = reinterpret_cast<FPDF_SCHHANDLE>(findHandle);
    auto result = FPDFText_FindNext(handle);
    return result;
}

JNI_FindResult(jlong, PdfiumCore, nativeFindPrev)(JNI_ARGS, jlong findHandle) {
    auto handle = reinterpret_cast<FPDF_SCHHANDLE>(findHandle);
    auto result = FPDFText_FindPrev(handle);
    return result;
}

JNI_FindResult(jlong, PdfiumCore, nativeGetSchResultIndex)(JNI_ARGS, jlong findHandle) {
    auto handle = reinterpret_cast<FPDF_SCHHANDLE>(findHandle);
    auto result = FPDFText_GetSchResultIndex(handle);
    return result;
}

JNI_FindResult(jlong, PdfiumCore, nativeGetSchCount)(JNI_ARGS, jlong findHandle) {
    auto handle = reinterpret_cast<FPDF_SCHHANDLE>(findHandle);
    auto result = FPDFText_GetSchCount(handle);
    return result;
}

JNI_FindResult(void, PdfiumCore, nativeCloseFind)(JNI_ARGS, jlong findHandle) {
    auto handle = reinterpret_cast<FPDF_SCHHANDLE>(findHandle);
    FPDFText_FindClose(handle);
}

JNI_PdfTextPage(jfloatArray, PdfiumCore, nativeGetRect)(JNI_ARGS, jlong pageLinkPtr, jint linkIndex,
                                                        jint rectIndex) {
    auto pageLink = reinterpret_cast<FPDF_PAGELINK>(pageLinkPtr);

    double left;
    double top;
    double right;
    double bottom;

    if (FPDFLink_GetRect(pageLink, linkIndex, rectIndex, &left, &top, &right, &bottom)) {
        jfloatArray result = env->NewFloatArray(4);
        if (result == nullptr) {
            return nullptr;
        }
        jfloat array[4];
        array[0] = (float) left;
        array[1] = (float) top;
        array[2] = (float) right;
        array[3] = (float) bottom;

        env->SetFloatArrayRegion(result, 0, 4, array);
        return result;
    }
    return nullptr;
}

JNI_PdfTextPage(jintArray, PdfiumCore, nativeGetTextRange)(JNI_ARGS, jlong pageLinkPtr, jint index) {
    auto pageLink = reinterpret_cast<FPDF_PAGELINK>(pageLinkPtr);

    if (pageLink == nullptr) {
        LOGE("PageLink is null");
        jniThrowException(env, "java/lang/IllegalStateException", "Document is null");
        return nullptr;
    }

    int start, count;
    int result = FPDFLink_GetTextRange(pageLink, index, &start, &count);

    if (result == 0) {
        start = 0;
        count = 0;
    }

    jintArray retVal = env->NewIntArray(2);
    if (retVal == nullptr) {
        return nullptr;
    }

    jint buffer[] = {start, count};
    env->SetIntArrayRegion(retVal, 0, 2, buffer);

    return retVal;
}

JNI_PdfTextPage(void, PdfiumCore, nativeClosePageLink)(JNI_ARGS, jlong pageLinkPtr) {
    auto pageLink = reinterpret_cast<FPDF_PAGELINK>(pageLinkPtr);
    FPDFLink_CloseWebLinks(pageLink);
}

JNI_PdfTextPage(jint, PdfiumCore, nativeCountWebLinks)(JNI_ARGS, jlong pageLinkPtr) {
    auto pageLink = reinterpret_cast<FPDF_PAGELINK>(pageLinkPtr);
    auto result = FPDFLink_CountWebLinks(pageLink);
    LOGE("CountWebLinks result %d", result);
    return result;
}

JNI_PdfTextPage(jint, PdfiumCore, nativeGetURL)(JNI_ARGS, jlong pageLinkPtr, jint index, jint count,
                                                jbyteArray result) {
    auto pageLink = reinterpret_cast<FPDF_PAGELINK>(pageLinkPtr);

    jboolean isCopy = JNI_FALSE;
    jbyte *arr = env->GetByteArrayElements(result, &isCopy);
    if (arr == nullptr) {
        // Handle error: Maybe throw an exception or return an error code
        return 0;
    }

    std::vector<unsigned short> buffer(count);
    jint output = static_cast<jint>(FPDFLink_GetURL(pageLink, index, buffer.data(), count));

    //jint output = (jint) FPDFLink_GetURL(pageLink, index, buffer, count);

    memcpy(arr, buffer.data(), count * sizeof(unsigned short));
    if (isCopy) {
        // If it's a copy, update the Java array and abort the release
        env->SetByteArrayRegion(result, 0, count * sizeof(unsigned short), arr);
        env->ReleaseByteArrayElements(result, arr, JNI_ABORT);
    } else {
        // If not a copy, just release without copying back
        env->ReleaseByteArrayElements(result, arr, 0);
    }
    return output;
}

JNI_PdfTextPage(jint, PdfiumCore, nativeCountRects)(JNI_ARGS, jlong pageLinkPtr, jint index) {
    auto pageLink = reinterpret_cast<FPDF_PAGELINK>(pageLinkPtr);

    auto result = FPDFLink_CountRects(pageLink, index);
    LOGE("CountRect %d", result);

    return result;
}

}//extern C