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
#include <fpdfview.h>

#include "include/util.h"
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

    DocumentFile() {
        initLibraryIfNeed();
    }

    ~DocumentFile();
};

DocumentFile::~DocumentFile() {
    if (pdfDocument != nullptr) {
        FPDF_CloseDocument(pdfDocument);
        pdfDocument = nullptr;
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

extern "C" { //For JNI support

static int
getBlock(void *param, unsigned long position, unsigned char *outBuffer, unsigned long size) {
    const int fd = reinterpret_cast<intptr_t>(param);
    const int readCount = pread(fd, outBuffer, size, position);
    if (readCount < 0) {
        LOGE("Cannot read from file descriptor. Error: %d", errno);
        return 0;
    }
    return 1;
}

class FileWrite : public FPDF_FILEWRITE {
public:
    jobject callbackObject;
    jmethodID callbackMethodID;
    _JNIEnv *env;

    static int
    WriteBlockCallback(FPDF_FILEWRITE *pFileWrite, const void *data, unsigned long size) {
        auto *pThis = static_cast<FileWrite *>(pFileWrite);
        _JNIEnv *env = pThis->env;
        //Convert the native array to Java array.
        jbyteArray a = env->NewByteArray((int) size);
        if (a != nullptr) {
            env->SetByteArrayRegion(a, 0, size, (const jbyte *) data);
            return env->CallIntMethod(pThis->callbackObject, pThis->callbackMethodID, a);
        }
        return -1;
    }
};

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

static void
renderPageInternal(FPDF_PAGE page, ANativeWindow_Buffer *windowBuffer, int startX, int startY,
                   int canvasHorSize, int canvasVerSize, int drawSizeHor, int drawSizeVer,
                   bool annotation) {
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
    if (annotation) {
        flags |= FPDF_ANNOT;
    }
    FPDFBitmap_FillRect(pdfBitmap, baseX, baseY, baseHorSize, baseVerSize, 0xFFFFFFFF); //White
    FPDF_RenderPageBitmap(pdfBitmap, page, startX, startY, drawSizeHor, drawSizeVer, 0, flags);
}

static jlong loadTextPageInternal(JNIEnv *env, DocumentFile *doc, jlong pagePtr) {
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

/*
 * PdfiumCore
 */
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
        const long errorNum = FPDF_GetLastError();
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
    env->GetByteArrayRegion(data, 0, size, cDataCopy);
    FPDF_DOCUMENT document = FPDF_LoadMemDocument(reinterpret_cast<const void *>(cDataCopy), size,
                                                  cPassword);
    env->ReleaseByteArrayElements(data, cData, JNI_ABORT);
    if (cPassword != nullptr) {
        env->ReleaseStringUTFChars(password, cPassword);
    }
    if (!document) {
        delete docFile;
        const long errorNum = FPDF_GetLastError();
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
    return reinterpret_cast<jlong>(docFile);
}

/*
 * PdfPage
 */
JNI_FUNC(jint, PdfPage, nativeGetPageRotation)(JNI_ARGS, jlong pagePtr) {
    auto page = reinterpret_cast<FPDF_PAGE>(pagePtr);
    return (jint) FPDFPage_GetRotation(page);
}

JNI_FUNC(void, PdfPage, nativeClosePage)(JNI_ARGS, jlong pagePtr) { closePageInternal(pagePtr); }

JNI_FUNC(void, PdfPage, nativeClosePages)(JNI_ARGS, jlongArray pagesPtr) {
    int length = (int) (env->GetArrayLength(pagesPtr));
    jlong *pages = env->GetLongArrayElements(pagesPtr, nullptr);
    int i;
    for (i = 0; i < length; i++) { closePageInternal(pages[i]); }
}

JNI_FUNC(jint, PdfPage, nativeGetPageWidthPixel)(JNI_ARGS, jlong pagePtr, jint dpi) {
    auto page = reinterpret_cast<FPDF_PAGE>(pagePtr);
    return (jint) (FPDF_GetPageWidth(page) * dpi / 72);
}

JNI_FUNC(jint, PdfPage, nativeGetPageHeightPixel)(JNI_ARGS, jlong pagePtr, jint dpi) {
    auto page = reinterpret_cast<FPDF_PAGE>(pagePtr);
    return (jint) (FPDF_GetPageHeight(page) * dpi / 72);
}

JNI_FUNC(jint, PdfPage, nativeGetPageWidthPoint)(JNI_ARGS, jlong pagePtr) {
    auto page = reinterpret_cast<FPDF_PAGE>(pagePtr);
    return (jint) FPDF_GetPageWidth(page);
}

JNI_FUNC(jint, PdfPage, nativeGetPageHeightPoint)(JNI_ARGS, jlong pagePtr) {
    auto page = reinterpret_cast<FPDF_PAGE>(pagePtr);
    return (jint) FPDF_GetPageHeight(page);
}

JNI_FUNC(jobject, PdfPage, nativeGetDestPageIndex)(JNI_ARGS, jlong docPtr, jlong linkPtr) {
    auto *doc = reinterpret_cast<DocumentFile *>(docPtr);
    auto link = reinterpret_cast<FPDF_LINK>(linkPtr);
    FPDF_DEST dest = FPDFLink_GetDest(doc->pdfDocument, link);
    if (dest == nullptr) {
        return nullptr;
    }
    long pageIdx = FPDFDest_GetDestPageIndex(doc->pdfDocument, dest);
    return NewInteger(env, (jint) pageIdx);
}

JNI_FUNC(jstring, PdfPage, nativeGetLinkURI)(JNI_ARGS, jlong docPtr, jlong linkPtr) {
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

JNI_FUNC(jobject, PdfPage, nativeGetLinkRect)(JNI_ARGS, jlong linkPtr) {
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

JNI_FUNC(jobject, PdfPage, nativeGetPageSizeByIndex)(JNI_ARGS, jlong docPtr, jint pageIndex,
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

JNI_FUNC(jlongArray, PdfPage, nativeGetPageLinks)(JNI_ARGS, jlong pagePtr) {
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

JNI_FUNC(jobject, PdfPage, nativePageCoordsToDevice)(JNI_ARGS, jlong pagePtr, jint startX,
                                                     jint startY, jint sizeX, jint sizeY,
                                                     jint rotate, jdouble pageX, jdouble pageY) {
    auto page = reinterpret_cast<FPDF_PAGE>(pagePtr);
    int deviceX, deviceY;
    FPDF_PageToDevice(page, startX, startY, sizeX, sizeY, rotate, pageX, pageY, &deviceX, &deviceY);
    jclass clazz = env->FindClass("android/graphics/Point");
    jmethodID constructorID = env->GetMethodID(clazz, "<init>", "(II)V");
    return env->NewObject(clazz, constructorID, deviceX, deviceY);
}

JNI_FUNC(jobject, PdfPage, nativeDeviceCoordsToPage)(JNI_ARGS, jlong pagePtr, jint startX,
                                                     jint startY, jint sizeX, jint sizeY,
                                                     jint rotate, jint deviceX, jint deviceY) {
    auto page = reinterpret_cast<FPDF_PAGE>(pagePtr);
    double pageX, pageY;
    FPDF_DeviceToPage(page, startX, startY, sizeX, sizeY, rotate, deviceX, deviceY, &pageX, &pageY);
    jclass clazz = env->FindClass("android/graphics/PointF");
    jmethodID constructorID = env->GetMethodID(clazz, "<init>", "(FF)V");
    return env->NewObject(clazz, constructorID, (float) pageX, (float) pageY);
}

JNI_FUNC(void, PdfPage, nativeRenderPage)(JNI_ARGS, jlong pagePtr, jobject objSurface,
                                          jint startX, jint startY, jint drawSizeHor,
                                          jint drawSizeVer, jboolean annotation) {
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
                       (int) drawSizeHor, (int) drawSizeVer, (bool) annotation);
    ANativeWindow_unlockAndPost(nativeWindow);
    ANativeWindow_release(nativeWindow);
}

JNI_FUNC(jlong, PdfPage, nativeGetLinkAtCoord)(JNI_ARGS, jlong pagePtr, jint width,
                                               jint height, jint posX, jint posY) {
    double px, py;
    FPDF_DeviceToPage((FPDF_PAGE) pagePtr, 0, 0, width, height, 0, posX, posY, &px, &py);
    return (jlong) FPDFLink_GetLinkAtPoint((FPDF_PAGE) pagePtr, px, py);
}

JNI_FUNC(void, PdfPage, nativeRenderPageBitmapWithMatrix)(JNI_ARGS, jlong pagePtr, jobject bitmap,
                                                          jfloatArray matrixValues,
                                                          jobject clipRect, jboolean annotation) {
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
    auto canvasHorSize = info.width;
    auto canvasVerSize = info.height;
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

    FPDF_BITMAP pdfBitmap = FPDFBitmap_CreateEx((int) canvasHorSize, (int) canvasVerSize,
                                                format, tmp, sourceStride);
    /*
    LOGD("Start X: %d", startX);
    LOGD("Start Y: %d", startY);
    LOGD("Canvas Hor: %d", canvasHorSize);
    LOGD("Canvas Ver: %d", canvasVerSize);
    LOGD("Draw Hor: %d", drawSizeHor);
    LOGD("Draw Ver: %d", drawSizeVer);
     */
    int flags = FPDF_REVERSE_BYTE_ORDER;
    if (annotation) {
        flags |= FPDF_ANNOT;
    }
    FPDFBitmap_FillRect(pdfBitmap, 0, 0, canvasHorSize, canvasVerSize, 0xFFFFFFFF); //White
    jclass clazz = env->FindClass("android/graphics/RectF");
    jfieldID left = env->GetFieldID(clazz, "left", "F");
    jfieldID top = env->GetFieldID(clazz, "top", "F");
    jfieldID right = env->GetFieldID(clazz, "right", "F");
    jfieldID bottom = env->GetFieldID(clazz, "bottom", "F");
    jfloat leftClip = env->GetFloatField(clipRect, left);
    jfloat topClip = env->GetFloatField(clipRect, top);
    jfloat rightClip = env->GetFloatField(clipRect, right);
    jfloat bottomClip = env->GetFloatField(clipRect, bottom);
    jboolean isCopy;
    auto matrixFloats = env->GetFloatArrayElements(matrixValues, &isCopy);
    auto matrix = FS_MATRIX();
    matrix.a = matrixFloats[0];
    matrix.b = 0;
    matrix.c = 0;
    matrix.d = matrixFloats[1];
    matrix.e = matrixFloats[2];
    matrix.f = matrixFloats[3];
    auto clip = FS_RECTF();
    clip.left = leftClip;
    clip.top = topClip;
    clip.right = rightClip;
    clip.bottom = bottomClip;
    if (isCopy) {
        env->ReleaseFloatArrayElements(matrixValues, (jfloat *) matrixFloats, JNI_ABORT);
    }
    FPDF_RenderPageBitmapWithMatrix(pdfBitmap, page, &matrix, &clip, flags);
    if (info.format == ANDROID_BITMAP_FORMAT_RGB_565) {
        rgbBitmapTo565(tmp, sourceStride, addr, &info);
        free(tmp);
    }
    AndroidBitmap_unlockPixels(env, bitmap);
}

JNI_FUNC(void, PdfPage, nativeRenderPageBitmap)(JNI_ARGS, jlong pagePtr, jobject bitmap,
                                                jint startX, jint startY, jint drawSizeHor,
                                                jint drawSizeVer, jboolean annotation) {
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
    if (annotation) {
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

JNI_FUNC(jfloatArray, PdfPage, nativeGetPageArtBox)(JNI_ARGS, jlong pagePtr) {
    auto page = reinterpret_cast<FPDF_PAGE>(pagePtr);
    jfloatArray result = env->NewFloatArray(4);
    if (result == nullptr) {
        return nullptr;
    }
    float rect[4];
    if (!FPDFPage_GetArtBox(page, &rect[0], &rect[1], &rect[2], &rect[3])) {
        rect[0] = -1.0f;
        rect[1] = -1.0f;
        rect[2] = -1.0f;
        rect[3] = -1.0f;
    }
    env->SetFloatArrayRegion(result, 0, 4, (jfloat *) rect);
    return result;
}

JNI_FUNC(jfloatArray, PdfPage, nativeGetPageBleedBox)(JNI_ARGS, jlong pagePtr) {
    auto page = reinterpret_cast<FPDF_PAGE>(pagePtr);
    jfloatArray result = env->NewFloatArray(4);
    if (result == nullptr) {
        return nullptr;
    }
    float rect[4];
    if (!FPDFPage_GetBleedBox(page, &rect[0], &rect[1], &rect[2], &rect[3])) {
        rect[0] = -1.0f;
        rect[1] = -1.0f;
        rect[2] = -1.0f;
        rect[3] = -1.0f;
    }
    env->SetFloatArrayRegion(result, 0, 4, (jfloat *) rect);
    return result;
}

JNI_FUNC(jfloatArray, PdfPage, nativeGetPageBoundingBox)(JNI_ARGS, jlong pagePtr) {
    auto page = reinterpret_cast<FPDF_PAGE>(pagePtr);
    jfloatArray result = env->NewFloatArray(4);
    if (result == nullptr) {
        return nullptr;
    }
    float rect[4];
    FS_RECTF fsRect;
    if (!FPDF_GetPageBoundingBox(page, &fsRect)) {
        rect[0] = -1.0f;
        rect[1] = -1.0f;
        rect[2] = -1.0f;
        rect[3] = -1.0f;
    } else {
        rect[0] = fsRect.left;
        rect[1] = fsRect.top;
        rect[2] = fsRect.right;
        rect[3] = fsRect.bottom;
    }
    env->SetFloatArrayRegion(result, 0, 4, (jfloat*)rect);
    return result;
}

JNI_FUNC(jfloatArray, PdfPage, nativeGetPageCropBox)(JNI_ARGS, jlong pagePtr) {
    auto page = reinterpret_cast<FPDF_PAGE>(pagePtr);
    jfloatArray result = env->NewFloatArray(4);
    if (result == nullptr) {
        return nullptr;
    }
    float rect[4];
    if (!FPDFPage_GetCropBox(page, &rect[0], &rect[1], &rect[2], &rect[3])) {
        rect[0] = -1.0f;
        rect[1] = -1.0f;
        rect[2] = -1.0f;
        rect[3] = -1.0f;
    }
    env->SetFloatArrayRegion(result, 0, 4, (jfloat *) rect);
    return result;
}

JNI_FUNC(jfloatArray, PdfPage, nativeGetPageMediaBox)(JNI_ARGS, jlong pagePtr) {
    auto page = reinterpret_cast<FPDF_PAGE>(pagePtr);
    jfloatArray result = env->NewFloatArray(4);
    if (result == nullptr) {
        return nullptr;
    }
    float rect[4];
    if (!FPDFPage_GetMediaBox(page, &rect[0], &rect[1], &rect[2], &rect[3])) {
        rect[0] = -1.0f;
        rect[1] = -1.0f;
        rect[2] = -1.0f;
        rect[3] = -1.0f;
    }
    env->SetFloatArrayRegion(result, 0, 4, (jfloat *) rect);
    return result;
}

JNI_FUNC(jfloatArray, PdfPage, nativeGetPageTrimBox)(JNI_ARGS, jlong pagePtr) {
    auto page = reinterpret_cast<FPDF_PAGE>(pagePtr);
    jfloatArray result = env->NewFloatArray(4);
    if (result == nullptr) {
        return nullptr;
    }
    float rect[4];
    if (!FPDFPage_GetTrimBox(page, &rect[0], &rect[1], &rect[2], &rect[3])) {
        rect[0] = -1.0f;
        rect[1] = -1.0f;
        rect[2] = -1.0f;
        rect[3] = -1.0f;
    }
    env->SetFloatArrayRegion(result, 0, 4, (jfloat*)rect);
    return result;
}

/*
 * PdfDocument
 */
JNI_FUNC(jstring, PdfDocument, nativeGetDocumentMetaText)(JNI_ARGS, jlong docPtr, jstring tag) {
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

JNI_FUNC(jobject, PdfDocument, nativeGetFirstChildBookmark)(JNI_ARGS, jlong docPtr,
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

JNI_FUNC(jobject, PdfDocument, nativeGetSiblingBookmark)(JNI_ARGS, jlong docPtr,
                                                         jlong bookmarkPtr) {
    auto *doc = reinterpret_cast<DocumentFile *>(docPtr);
    auto parent = reinterpret_cast<FPDF_BOOKMARK>(bookmarkPtr);
    FPDF_BOOKMARK bookmark = FPDFBookmark_GetNextSibling(doc->pdfDocument, parent);
    if (bookmark == nullptr) {
        return nullptr;
    }
    return NewLong(env, reinterpret_cast<jlong>(bookmark));
}

JNI_FUNC(jstring, PdfDocument, nativeGetBookmarkTitle)(JNI_ARGS, jlong bookmarkPtr) {
    auto bookmark = reinterpret_cast<FPDF_BOOKMARK>(bookmarkPtr);
    size_t bufferLen = FPDFBookmark_GetTitle(bookmark, nullptr, 0);
    if (bufferLen <= 2) {
        return env->NewStringUTF("");
    }
    std::wstring title;
    FPDFBookmark_GetTitle(bookmark, WriteInto(&title, bufferLen + 1), bufferLen);
    return env->NewString((jchar *) title.c_str(), bufferLen / 2 - 1);
}

JNI_FUNC(jlong, PdfDocument, nativeGetBookmarkDestIndex)(JNI_ARGS, jlong docPtr,
                                                         jlong bookmarkPtr) {
    auto *doc = reinterpret_cast<DocumentFile *>(docPtr);
    auto bookmark = reinterpret_cast<FPDF_BOOKMARK>(bookmarkPtr);
    FPDF_DEST dest = FPDFBookmark_GetDest(doc->pdfDocument, bookmark);
    if (dest == nullptr) {
        return -1;
    }
    return (jlong) FPDFDest_GetDestPageIndex(doc->pdfDocument, dest);
}

JNI_FUNC(jlong, PdfDocument, nativeLoadTextPage)(JNI_ARGS, jlong docPtr, jlong pagePtr) {
    auto *doc = reinterpret_cast<DocumentFile *>(docPtr);
    return loadTextPageInternal(env, doc, pagePtr);
}

JNI_FUNC(jint, PdfDocument, nativeGetPageCount)(JNI_ARGS, jlong documentPtr) {
    auto *doc = reinterpret_cast<DocumentFile *>(documentPtr);
    return (jint) FPDF_GetPageCount(doc->pdfDocument);
}

JNI_FUNC(void, PdfDocument, nativeCloseDocument)(JNI_ARGS, jlong documentPtr) {
    auto *doc = reinterpret_cast<DocumentFile *>(documentPtr);
    delete doc;
}

JNI_FUNC(jlong, PdfDocument, nativeLoadPage)(JNI_ARGS, jlong docPtr, jint pageIndex) {
    auto *doc = reinterpret_cast<DocumentFile *>(docPtr);
    return loadPageInternal(env, doc, (int) pageIndex);
}

JNI_FUNC(jboolean, PdfDocument, nativeSaveAsCopy)(JNI_ARGS, jlong docPtr, jobject callback) {
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
        return (jboolean) FPDF_SaveAsCopy(doc->pdfDocument, &fw, FPDF_NO_INCREMENTAL);
    }
    return false;
}

JNI_FUNC(jintArray, PdfDocument, nativeGetPageCharCounts)(JNI_ARGS, jlong docPtr) {
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

JNI_FUNC(jlongArray, PdfDocument, nativeLoadPages)(JNI_ARGS, jlong docPtr, jint fromIndex,
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

/*
 * PdfTextPage
 */
JNI_FUNC(void, PdfTextPage, nativeCloseTextPage)(JNI_ARGS, jlong pagePtr) {
    closeTextPageInternal(pagePtr);
}

JNI_FUNC(jdouble, PdfTextPage, nativeGetFontSize)(JNI_ARGS, jlong pagePtr, jint charIndex) {
    auto textPage = reinterpret_cast<FPDF_TEXTPAGE>(pagePtr);
    return (jdouble) FPDFText_GetFontSize(textPage, charIndex);
}

JNI_FUNC(jint, PdfTextPage, nativeTextCountChars)(JNI_ARGS, jlong textPagePtr) {
    auto textPage = reinterpret_cast<FPDF_TEXTPAGE>(textPagePtr);
    return (jint) FPDFText_CountChars(textPage);
}

JNI_FUNC(jint, PdfTextPage, nativeTextCountRects)(JNI_ARGS, jlong textPagePtr, jint startIndex,
                                                  jint count) {
    auto textPage = reinterpret_cast<FPDF_TEXTPAGE>(textPagePtr);
    return (jint) FPDFText_CountRects(textPage, (int) startIndex, (int) count);
}

JNI_FUNC(jint, PdfTextPage, nativeTextGetBoundedText)(JNI_ARGS, jlong textPagePtr, jdouble left,
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

JNI_FUNC(jdoubleArray, PdfTextPage, nativeTextGetCharBox)(JNI_ARGS, jlong textPagePtr,
                                                          jint index) {
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

JNI_FUNC(jint, PdfTextPage, nativeTextGetCharIndexAtPos)(JNI_ARGS, jlong textPagePtr, jdouble x,
                                                         jdouble y, jdouble xTolerance,
                                                         jdouble yTolerance) {
    auto textPage = reinterpret_cast<FPDF_TEXTPAGE>(textPagePtr);
    return (jint) FPDFText_GetCharIndexAtPos(textPage, (double) x, (double) y,
                                             (double) xTolerance, (double) yTolerance);
}

JNI_FUNC(jdoubleArray, PdfTextPage, nativeTextGetRect)(JNI_ARGS, jlong textPagePtr,
                                                       jint rectIndex) {
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

JNI_FUNC(jint, PdfTextPage, nativeTextGetText)(JNI_ARGS, jlong textPagePtr,
                                               jint startIndex, jint count, jshortArray result) {
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

JNI_FUNC(jint, PdfTextPage, nativeTextGetTextByteArray)(JNI_ARGS, jlong textPagePtr,
                                                        jint startIndex, jint count,
                                                        jbyteArray result) {
    auto textPage = reinterpret_cast<FPDF_TEXTPAGE>(textPagePtr);
    jboolean isCopy = 0;
    auto *arr = (jbyteArray) env->GetByteArrayElements(result, &isCopy);
    unsigned short buffer[count];
    jint output = (jint) FPDFText_GetText(textPage, (int) startIndex, (int) count, buffer);
    memcpy(arr, buffer, count * sizeof(unsigned short));
    if (isCopy) {
        env->SetByteArrayRegion(result, 0, count * 2, (jbyte *) arr);
        env->ReleaseByteArrayElements(result, (jbyte *) arr, JNI_ABORT);
    }
    return output;
}

JNI_FUNC(jint, PdfTextPage, nativeTextGetUnicode)(JNI_ARGS, jlong textPagePtr, jint index) {
    auto textPage = reinterpret_cast<FPDF_TEXTPAGE>(textPagePtr);
    return (jint) FPDFText_GetUnicode(textPage, (int) index);
}

}//extern C