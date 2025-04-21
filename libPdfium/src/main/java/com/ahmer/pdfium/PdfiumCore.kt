package com.ahmer.pdfium

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Point
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.os.ParcelFileDescriptor
import android.util.Log
import android.view.Surface
import androidx.annotation.ColorInt
import com.ahmer.pdfium.util.Config
import com.ahmer.pdfium.util.Size
import com.ahmer.pdfium.util.SizeF
import com.ahmer.pdfium.util.handleAlreadyClosed
import com.ahmer.pdfium.util.pdfiumConfig
import dalvik.annotation.optimization.FastNative
import kotlinx.coroutines.sync.Mutex
import java.io.Closeable
import java.io.IOException

/**
 * Represents a single page in a [PdfDocument].
 */
class PdfiumCore(
    val context: Context,
    config: Config = Config(),
) : Closeable {
    private var isClosed = false

    private external fun nativeClosePage(pagePtr: Long)

    private external fun nativeClosePages(pagesPtr: LongArray)

    @FastNative
    private external fun nativeDeviceCoordsToPage(
        pagePtr: Long, startX: Int, startY: Int, sizeX: Int, sizeY: Int,
        rotate: Int, deviceX: Int, deviceY: Int,
    ): PointF

    private external fun nativeGetDestPageIndex(docPtr: Long, linkPtr: Long): Int

    private external fun nativeGetLinkAtCoord(
        pagePtr: Long, width: Int, height: Int, posX: Int, posY: Int
    ): Long

    private external fun nativeGetLinkRect(linkPtr: Long): RectF

    private external fun nativeGetLinkURI(docPtr: Long, linkPtr: Long): String?

    @FastNative
    private external fun nativeGetPageArtBox(pagePtr: Long): FloatArray

    @FastNative
    private external fun nativeGetPageBleedBox(pagePtr: Long): FloatArray

    @FastNative
    private external fun nativeGetPageBoundingBox(pagePtr: Long): FloatArray

    @FastNative
    private external fun nativeGetPageCropBox(pagePtr: Long): FloatArray

    @FastNative
    private external fun nativeGetPageHeightPixel(pagePtr: Long, dpi: Int): Int

    @FastNative
    private external fun nativeGetPageHeightPoint(pagePtr: Long): Int

    private external fun nativeGetPageLinks(pagePtr: Long): LongArray

    //@FastNative
    //private external fun nativeGetPageMatrix(pagePtr: Long): FloatArray

    @FastNative
    private external fun nativeGetPageMediaBox(pagePtr: Long): FloatArray

    @FastNative
    private external fun nativeGetPageRotation(pagePtr: Long): Int

    private external fun nativeGetPageSizeByIndex(docPtr: Long, pageIndex: Int, dpi: Int): Size

    @FastNative
    private external fun nativeGetPageTrimBox(pagePtr: Long): FloatArray

    @FastNative
    private external fun nativeGetPageWidthPixel(pagePtr: Long, dpi: Int): Int

    @FastNative
    private external fun nativeGetPageWidthPoint(pagePtr: Long): Int

    private external fun nativeOpenDocument(parcelFileDescriptor: Int, password: String?): Long

    private external fun nativeOpenMemDocument(data: ByteArray, password: String?): Long

    @FastNative
    private external fun nativePageCoordsToDevice(
        pagePtr: Long, startX: Int, startY: Int, sizeX: Int, sizeY: Int,
        rotate: Int, pageX: Double, pageY: Double,
    ): Point

    private external fun nativeRenderPage(
        pagePtr: Long, bufferPtr: Long, startX: Int, startY: Int, drawSizeHor: Int,
        drawSizeVer: Int, annotation: Boolean, canvasColor: Int, pageBackgroundColor: Int,
    ): Boolean

    private external fun nativeRenderPageBitmap(
        docPtr: Long, pagePtr: Long, bitmap: Bitmap?, startX: Int, startY: Int, drawSizeHor: Int,
        drawSizeVer: Int, annotation: Boolean, canvasColor: Int, pageBackgroundColor: Int,
    )

    private external fun nativeRenderPageWithMatrix(
        pagePtr: Long, bufferPtr: Long, drawSizeHor: Int, drawSizeVer: Int, matrix: FloatArray,
        clipRect: FloatArray, annotation: Boolean, textMask: Boolean = false,
        canvasColor: Int, pageBackgroundColor: Int,
    ): Boolean

    private external fun nativeRenderPageSurface(
        pagePtr: Long, surface: Surface, startX: Int, startY: Int,
        annotation: Boolean, canvasColor: Int, pageBackgroundColor: Int,
    ): Boolean

    private external fun nativeRenderPageSurfaceWithMatrix(
        pagePtr: Long, surface: Surface, matrix: FloatArray, clipRect: FloatArray,
        annotation: Boolean, canvasColor: Int, pageBackgroundColor: Int,
    ): Boolean

    private external fun nativeRenderPageBitmapWithMatrix(
        pagePtr: Long, bitmap: Bitmap?, matrix: FloatArray, clipRect: RectF,
        annotation: Boolean, pageBackgroundColor: Int,
    )

    private val doc: PdfDocument = PdfDocument()
    var currentDpi: Int = 0

    init {
        pdfiumConfig = config
        Log.d(TAG, "Starting PdfiumAndroid ")
        currentDpi = context.resources?.displayMetrics?.densityDpi ?: -1
    }

    private fun pagePtr(index: Int): Long {
        return doc.pageMap[index]?.pagePtr ?: -1
    }

    @Throws(IOException::class)
    fun newDocument(parcelFileDescriptor: ParcelFileDescriptor): PdfDocument {
        return newDocument(parcelFileDescriptor = parcelFileDescriptor, password = null)
    }

    @Throws(IOException::class)
    fun newDocument(parcelFileDescriptor: ParcelFileDescriptor, password: String?): PdfDocument {
        doc.parcelFileDescriptor = parcelFileDescriptor
        synchronized(lock = lock) {
            doc.nativeDocPtr = nativeOpenDocument(
                parcelFileDescriptor = parcelFileDescriptor.fd, password = password
            )
        }
        return doc
    }

    @Throws(IOException::class)
    fun newDocument(data: ByteArray): PdfDocument {
        return newDocument(data = data, password = null)
    }

    @Throws(IOException::class)
    fun newDocument(data: ByteArray, password: String?): PdfDocument {
        synchronized(lock = lock) {
            doc.nativeDocPtr = nativeOpenMemDocument(data = data, password = password)
        }
        return doc
    }

    fun openTextPage(pageIndex: Int): PdfTextPage = doc.openTextPage(pageIndex = pageIndex)

    fun getPageWidth(pageIndex: Int): Int {
        if (handleAlreadyClosed(isClosed = isClosed || doc.isClosed)) return -1
        synchronized(lock = lock) {
            return nativeGetPageWidthPixel(pagePtr = pagePtr(index = pageIndex), dpi = currentDpi)
        }
    }

    fun getPageHeight(pageIndex: Int): Int {
        if (handleAlreadyClosed(isClosed = isClosed || doc.isClosed)) return -1
        synchronized(lock = lock) {
            return nativeGetPageHeightPixel(pagePtr = pagePtr(index = pageIndex), dpi = currentDpi)
        }
    }

    fun getPageWidthPoint(pageIndex: Int): Int {
        if (handleAlreadyClosed(isClosed = isClosed || doc.isClosed)) return -1
        synchronized(lock = lock) {
            return nativeGetPageWidthPoint(pagePtr = pagePtr(index = pageIndex))
        }
    }

    fun getPageHeightPoint(pageIndex: Int): Int {
        if (handleAlreadyClosed(isClosed = isClosed || doc.isClosed)) return -1
        synchronized(lock = lock) {
            return nativeGetPageHeightPoint(pagePtr = pagePtr(index = pageIndex))
        }
    }

    /*fun getPageMatrix(pageIndex: Int): Matrix? {
        if (handleAlreadyClosed(isClosed = isClosed || doc.isClosed)) return null
        synchronized(lock = lock) {
            // Translation is performed with [1 0 0 1 tx ty].
            // Scaling is performed with [sx 0 0 sy 0 0].
            // Matrix for transformation, in the form [a b c d e f], equivalent to:
            // | a  b  0 |
            // | c  d  0 |
            // | e  f  1 |

            val values = FloatArray(THREE_BY_THREE)

            val pageMatrix = nativeGetPageMatrix(pagePtr = pagePtr(index = pageIndex))

            Log.d(TAG, "pageMatrix[0] = ${pageMatrix[0]}")
            Log.d(TAG, "pageMatrix[1] = ${pageMatrix[1]}")
            Log.d(TAG, "pageMatrix[2] = ${pageMatrix[2]}")
            Log.d(TAG, "pageMatrix[3] = ${pageMatrix[3]}")
            Log.d(TAG, "pageMatrix[4] = ${pageMatrix[4]}")
            Log.d(TAG, "pageMatrix[5] = ${pageMatrix[5]}")

            values[Matrix.MSCALE_X] = pageMatrix[0]
            values[Matrix.MSKEW_X] = pageMatrix[1]
            values[Matrix.MSKEW_Y] = pageMatrix[2]
            values[Matrix.MSCALE_Y] = pageMatrix[3]

            values[Matrix.MTRANS_X] = pageMatrix[4]
            values[Matrix.MTRANS_Y] = pageMatrix[5]

            values[Matrix.MPERSP_0] = 0f
            values[Matrix.MPERSP_1] = 0f
            values[Matrix.MPERSP_2] = 1f

            return Matrix().apply {
                setValues(values)
            }
        }
    }*/

    /**
     * Get page rotation in degrees
     * @return
     *  -1 - Error
     *  0 - No rotation.
     *  1 - Rotated 90 degrees clockwise.
     *  2 - Rotated 180 degrees clockwise.
     *  3 - Rotated 270 degrees clockwise.
     *
     *  @throws IllegalStateException If the page or document is closed
     */
    fun getPageRotation(pageIndex: Int): Int {
        if (handleAlreadyClosed(isClosed = isClosed || doc.isClosed)) return -1
        synchronized(lock = lock) {
            return nativeGetPageRotation(pagePtr = pagePtr(index = pageIndex))
        }
    }

    fun getPageCropBox(pageIndex: Int): RectF {
        check(value = !isClosed && !doc.isClosed) { "Already closed" }
        synchronized(lock = lock) {
            val odd = nativeGetPageCropBox(pagePtr = pagePtr(index = pageIndex))
            return RectF().apply {
                left = odd[LEFT]
                top = odd[TOP]
                right = odd[RIGHT]
                bottom = odd[BOTTOM]
            }
        }
    }

    fun getPageMediaBox(pageIndex: Int): RectF {
        check(value = !isClosed && !doc.isClosed) { "Already closed" }
        synchronized(lock = lock) {
            val odd = nativeGetPageMediaBox(pagePtr = pagePtr(index = pageIndex))
            return RectF().apply {
                left = odd[LEFT]
                top = odd[TOP]
                right = odd[RIGHT]
                bottom = odd[BOTTOM]
            }
        }
    }

    fun getPageBleedBox(pageIndex: Int): RectF {
        check(value = !isClosed && !doc.isClosed) { "Already closed" }
        synchronized(lock = lock) {
            val odd = nativeGetPageBleedBox(pagePtr = pagePtr(index = pageIndex))
            return RectF().apply {
                left = odd[LEFT]
                top = odd[TOP]
                right = odd[RIGHT]
                bottom = odd[BOTTOM]
            }
        }
    }

    fun getPageTrimBox(pageIndex: Int): RectF {
        check(value = !isClosed && !doc.isClosed) { "Already closed" }
        synchronized(lock = lock) {
            val odd = nativeGetPageTrimBox(pagePtr = pagePtr(index = pageIndex))
            return RectF().apply {
                left = odd[LEFT]
                top = odd[TOP]
                right = odd[RIGHT]
                bottom = odd[BOTTOM]
            }
        }
    }

    fun getPageArtBox(pageIndex: Int): RectF {
        check(value = !isClosed && !doc.isClosed) { "Already closed" }
        synchronized(lock = lock) {
            val odd = nativeGetPageArtBox(pagePtr = pagePtr(index = pageIndex))
            return RectF().apply {
                left = odd[LEFT]
                top = odd[TOP]
                right = odd[RIGHT]
                bottom = odd[BOTTOM]
            }
        }
    }

    fun getPageBoundingBox(pageIndex: Int): RectF {
        check(value = !isClosed && !doc.isClosed) { "Already closed" }
        synchronized(lock = lock) {
            val odd = nativeGetPageBoundingBox(pagePtr = pagePtr(index = pageIndex))
            return RectF().apply {
                left = odd[LEFT]
                top = odd[TOP]
                right = odd[RIGHT]
                bottom = odd[BOTTOM]
            }
        }
    }

    fun getPageSize(pageIndex: Int): Size {
        check(value = !isClosed && !doc.isClosed) { "Already closed" }
        synchronized(lock = lock) {
            return nativeGetPageSizeByIndex(
                docPtr = doc.nativeDocPtr,
                pageIndex = pageIndex,
                dpi = currentDpi,
            )
        }
    }

    fun renderPage(
        pageIndex: Int,
        bufferPtr: Long,
        startX: Int,
        startY: Int,
        drawSizeX: Int,
        drawSizeY: Int,
        annotation: Boolean = false,
        @ColorInt
        canvasColor: Int = 0xFF848484.toInt(),
        @ColorInt
        pageBackgroundColor: Int = 0xFFFFFFFF.toInt(),
    ): Boolean {
        if (handleAlreadyClosed(isClosed = isClosed || doc.isClosed)) return false
        synchronized(lock = lock) {
            try {
                // nativeRenderPage(doc.mNativePagesPtr.get(pageIndex), surface, currentDpi);
                return nativeRenderPage(
                    pagePtr = pagePtr(index = pageIndex),
                    bufferPtr = bufferPtr,
                    startX = startX,
                    startY = startY,
                    drawSizeHor = drawSizeX,
                    drawSizeVer = drawSizeY,
                    annotation = annotation,
                    canvasColor = canvasColor,
                    pageBackgroundColor = pageBackgroundColor,
                )
            } catch (e: NullPointerException) {
                Log.e(TAG, "Context may be null", e)
            } catch (e: Exception) {
                Log.e(TAG, "Exception throw from native", e)
            }
        }
        return false
    }

    fun renderPage(
        pageIndex: Int,
        bufferPtr: Long,
        drawSizeX: Int,
        drawSizeY: Int,
        matrix: Matrix,
        clipRect: RectF,
        annotation: Boolean = false,
        textMask: Boolean = false,
        canvasColor: Int = 0xFF848484.toInt(),
        pageBackgroundColor: Int = 0xFFFFFFFF.toInt(),
    ): Boolean {
        if (handleAlreadyClosed(isClosed = isClosed || doc.isClosed)) return false
        val matrixValues = FloatArray(size = THREE_BY_THREE)
        matrix.getValues(matrixValues)
        synchronized(lock = lock) {
            return nativeRenderPageWithMatrix(
                pagePtr = pagePtr(index = pageIndex),
                bufferPtr = bufferPtr,
                drawSizeHor = drawSizeX,
                drawSizeVer = drawSizeY,
                matrix = floatArrayOf(
                    matrixValues[Matrix.MSCALE_X],
                    matrixValues[Matrix.MSCALE_Y],
                    matrixValues[Matrix.MTRANS_X],
                    matrixValues[Matrix.MTRANS_Y],
                ),
                clipRect = floatArrayOf(
                    clipRect.left,
                    clipRect.top,
                    clipRect.right,
                    clipRect.bottom,
                ),
                annotation = annotation,
                textMask = textMask,
                canvasColor = canvasColor,
                pageBackgroundColor = pageBackgroundColor,
            )
        }
    }

    fun renderPage(
        pageIndex: Int,
        surface: Surface,
        matrix: Matrix,
        clipRect: RectF,
        annotation: Boolean = false,
        canvasColor: Int = 0xFF848484.toInt(),
        pageBackgroundColor: Int = 0xFFFFFFFF.toInt(),
    ): Boolean {
        if (handleAlreadyClosed(isClosed = isClosed || doc.isClosed)) return false
        val matrixValues = FloatArray(size = THREE_BY_THREE)
        matrix.getValues(matrixValues)
        synchronized(lock = lock) {
            return nativeRenderPageSurfaceWithMatrix(
                pagePtr = pagePtr(index = pageIndex),
                surface = surface,
                matrix = floatArrayOf(
                    matrixValues[Matrix.MSCALE_X],
                    matrixValues[Matrix.MSCALE_Y],
                    matrixValues[Matrix.MTRANS_X],
                    matrixValues[Matrix.MTRANS_Y],
                ),
                clipRect = floatArrayOf(
                    clipRect.left,
                    clipRect.top,
                    clipRect.right,
                    clipRect.bottom,
                ),
                annotation = annotation,
                canvasColor = canvasColor,
                pageBackgroundColor = pageBackgroundColor,
            )
        }
    }

    fun renderPageBitmap(
        pageIndex: Int,
        bitmap: Bitmap?,
        startX: Int,
        startY: Int,
        drawSizeX: Int,
        drawSizeY: Int,
        annotation: Boolean = false,
        canvasColor: Int = 0xFF848484.toInt(),
        pageBackgroundColor: Int = 0xFFFFFFFF.toInt(),
    ) {
        if (handleAlreadyClosed(isClosed = isClosed || doc.isClosed)) return
        synchronized(lock = lock) {
            nativeRenderPageBitmap(
                docPtr = doc.nativeDocPtr,
                pagePtr = pagePtr(index = pageIndex),
                bitmap = bitmap,
                startX = startX,
                startY = startY,
                drawSizeHor = drawSizeX,
                drawSizeVer = drawSizeY,
                annotation = annotation,
                canvasColor = canvasColor,
                pageBackgroundColor = pageBackgroundColor,
            )
        }
    }

    fun renderPageBitmap(
        pageIndex: Int,
        bitmap: Bitmap?,
        matrix: Matrix,
        clipRect: RectF,
        annotation: Boolean = false,
        pageBackgroundColor: Int = 0xFFFFFFFF.toInt(),
    ) {
        if (handleAlreadyClosed(isClosed = isClosed || doc.isClosed)) return
        val matrixValues = FloatArray(THREE_BY_THREE)
        matrix.getValues(matrixValues)
        synchronized(lock = lock) {
            nativeRenderPageBitmapWithMatrix(
                pagePtr = pagePtr(index = pageIndex),
                bitmap = bitmap,
                matrix = floatArrayOf(
                    matrixValues[Matrix.MSCALE_X],
                    matrixValues[Matrix.MSCALE_Y],
                    matrixValues[Matrix.MTRANS_X],
                    matrixValues[Matrix.MTRANS_Y],
                ),
                clipRect = clipRect,
                annotation = annotation,
                pageBackgroundColor = pageBackgroundColor,
            )
        }
    }

    fun getPageLinks(
        pageIndex: Int,
        size: SizeF,
        posX: Float,
        posY: Float
    ): List<PdfDocument.Link> {
        if (handleAlreadyClosed(isClosed = isClosed || doc.isClosed)) return emptyList()
        synchronized(lock = lock) {
            val pagePtr: Long = pagePtr(index = pageIndex)
            val links: MutableList<PdfDocument.Link> = ArrayList()
            val pageLinks: LongArray = nativeGetPageLinks(pagePtr = pagePtr(index = pageIndex))
            val linkAtCoordinate: Long = nativeGetLinkAtCoord(
                pagePtr = pagePtr, width = size.width.toInt(), height = size.height.toInt(),
                posX = posX.toInt(), posY = posY.toInt()
            )
            for (linkPtr in pageLinks) {
                val index: Int =
                    nativeGetDestPageIndex(docPtr = doc.nativeDocPtr, linkPtr = linkPtr)
                val uri: String? = nativeGetLinkURI(docPtr = doc.nativeDocPtr, linkPtr = linkPtr)
                val rect: RectF = nativeGetLinkRect(linkPtr = linkPtr)
                if (uri == null) {
                    val linkUri: String? = nativeGetLinkURI(
                        docPtr = doc.nativeDocPtr, linkPtr = linkAtCoordinate
                    )
                    links.add(PdfDocument.Link(bounds = rect, destPageIndex = index, uri = linkUri))
                } else {
                    links.add(PdfDocument.Link(bounds = rect, destPageIndex = index, uri = uri))
                }
            }
            return links
        }
    }

    fun mapPageCoordsToDevice(
        pageIndex: Int,
        startX: Int,
        startY: Int,
        sizeX: Int,
        sizeY: Int,
        rotate: Int,
        pageX: Double,
        pageY: Double,
    ): Point {
        check(value = !isClosed && !doc.isClosed) { "Already closed" }
        return nativePageCoordsToDevice(
            pagePtr = pagePtr(index = pageIndex),
            startX = startX,
            startY = startY,
            sizeX = sizeX,
            sizeY = sizeY,
            rotate = rotate,
            pageX = pageX,
            pageY = pageY
        )
    }

    fun mapDeviceCoordsToPage(
        pageIndex: Int,
        startX: Int,
        startY: Int,
        sizeX: Int,
        sizeY: Int,
        rotate: Int,
        deviceX: Int,
        deviceY: Int,
    ): PointF {
        check(value = !isClosed && !doc.isClosed) { "Already closed" }
        return nativeDeviceCoordsToPage(
            pagePtr = pagePtr(index = pageIndex),
            startX = startX,
            startY = startY,
            sizeX = sizeX,
            sizeY = sizeY,
            rotate = rotate,
            deviceX = deviceX,
            deviceY = deviceY,
        )
    }

    fun mapRectToDevice(
        pageIndex: Int,
        startX: Int,
        startY: Int,
        sizeX: Int,
        sizeY: Int,
        rotate: Int,
        coords: RectF,
    ): RectF {
        check(value = !isClosed && !doc.isClosed) { "Already closed" }
        val leftTop = mapPageCoordsToDevice(
            pageIndex = pageIndex,
            startX = startX,
            startY = startY,
            sizeX = sizeX,
            sizeY = sizeY,
            rotate = rotate,
            pageX = coords.left.toDouble(),
            pageY = coords.top.toDouble(),
        )
        val rightBottom = mapPageCoordsToDevice(
            pageIndex = pageIndex,
            startX = startX,
            startY = startY,
            sizeX = sizeX,
            sizeY = sizeY,
            rotate = rotate,
            pageX = coords.right.toDouble(),
            pageY = coords.bottom.toDouble(),
        )
        return RectF(
            leftTop.x.toFloat(),
            leftTop.y.toFloat(),
            rightBottom.x.toFloat(),
            rightBottom.y.toFloat(),
        )
    }

    fun mapRectToPage(
        pageIndex: Int,
        startX: Int,
        startY: Int,
        sizeX: Int,
        sizeY: Int,
        rotate: Int,
        coords: Rect,
    ): RectF {
        check(value = !isClosed && !doc.isClosed) { "Already closed" }
        val leftTop = mapDeviceCoordsToPage(
            pageIndex = pageIndex,
            startX = startX,
            startY = startY,
            sizeX = sizeX,
            sizeY = sizeY,
            rotate = rotate,
            deviceX = coords.left,
            deviceY = coords.top,
        )
        val rightBottom = mapDeviceCoordsToPage(
            pageIndex = pageIndex,
            startX = startX,
            startY = startY,
            sizeX = sizeX,
            sizeY = sizeY,
            rotate = rotate,
            deviceX = coords.right,
            deviceY = coords.bottom,
        )
        return RectF(
            leftTop.x,
            leftTop.y,
            rightBottom.x,
            rightBottom.y
        )
    }

    override fun close() {
        if (handleAlreadyClosed(isClosed = isClosed || doc.isClosed)) return
        synchronized(lock = lock) {
            isClosed = true
            Log.v(TAG, "PdfDocument.close")
            val pageMap = doc.pageMap
            for (index in pageMap.keys) {
                pageMap[index]?.let { it ->
                    if (it.count > 1) {
                        it.count--
                        return
                    }
                    pageMap.remove(index)
                    pageMap[index]?.let {
                        nativeClosePage(pagePtr = it.pagePtr)
                    }
                }
            }
            doc.close()
        }
    }

    companion object {
        val TAG = PdfiumCore::class.java.name
        val lock: Any = Any()
        const val LEFT = 0
        const val TOP = 1
        const val RIGHT = 2
        const val BOTTOM = 3
        private const val THREE_BY_THREE = 9
        private const val RECT_SIZE = 4

        val surfaceMutex = Mutex()

        @JvmStatic
        private external fun nativeLockSurface(
            surface: Surface, dimensions: IntArray, ptrs: LongArray,
        ): Boolean

        @JvmStatic
        private external fun nativeUnlockSurface(ptrs: LongArray)

        fun lockSurface(
            surface: Surface,
            dimensions: IntArray,
            ptrs: LongArray,
        ): Boolean = synchronized(lock = lock) {
            nativeLockSurface(surface = surface, dimensions = dimensions, ptrs = ptrs)
        }

        fun unlockSurface(ptrs: LongArray) = synchronized(lock = lock) {
            nativeUnlockSurface(ptrs = ptrs)
        }

        init {
            Log.v(TAG, "Starting AhmerPdfium...")
            try {
                System.loadLibrary("pdfium")
                System.loadLibrary("pdfium_jni")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "UnsatisfiedLinkError: Native libraries failed to load", e)
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException: Native libraries failed to load", e)
            } catch (e: NullPointerException) {
                Log.e(TAG, "NullPointerException: Native libraries failed to load", e)
            }
        }
    }
}