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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.IOException

/**
 * Core PDF processing class handling document operations, rendering, and coordinate transformations.
 *
 * @property context Android context for resource access
 * @property config Configuration parameters for PDF processing
 */
class PdfiumCore(
    val context: Context,
    private val config: Config = Config(),
) : Closeable {
    private var isClosed = false
    private val doc: PdfDocument = PdfDocument()

    private val currentDpi: Int by lazy {
        context.resources?.displayMetrics?.densityDpi ?: -1
    }

    init {
        pdfiumConfig = config
        Log.d(TAG, "Starting AhmerPdfium...")
    }

    private fun pagePtr(index: Int): Long {
        return doc.pageMap[index]?.pagePtr ?: -1
    }

    private fun buildRect(values: FloatArray): RectF = RectF().apply {
        require(value = values.size == 4) { "Invalid rect values from native" }
        set(values[0], values[1], values[2], values[3])
    }

    /**
     * Opens a new PDF document from a file descriptor.
     *
     * @param parcelFileDescriptor File descriptor of the PDF document.
     * @param password Optional password for protected documents.
     * @return The opened PDF document.
     * @throws IOException If the document cannot be opened.
     * @throws IllegalStateException If the document is already closed.
     */
    @Throws(IOException::class)
    suspend fun newDocument(
        parcelFileDescriptor: ParcelFileDescriptor, password: String? = null
    ): PdfDocument = withContext(context = Dispatchers.IO) {
        doc.parcelFileDescriptor = parcelFileDescriptor
        mutexLock.withLock {
            check(!isClosed && !doc.isClosed) { "Document is already closed" }
            doc.nativeDocPtr = nativeOpenDocument(
                parcelFileDescriptor = parcelFileDescriptor.fd, password = password
            )
        }
        doc
    }

    /**
     * Opens a new PDF document from a byte array
     *
     * @param data PDF document data as byte array
     * @param password Optional password for protected documents
     * @return The opened PDF document
     * @throws IOException If the document cannot be opened
     * @throws IllegalStateException If the document is already closed
     */
    @Throws(IOException::class)
    suspend fun newDocument(data: ByteArray, password: String? = null): PdfDocument =
        withContext(context = Dispatchers.IO) {
            mutexLock.withLock {
                check(!isClosed && !doc.isClosed) { "Document is already closed" }
                doc.nativeDocPtr = nativeOpenMemDocument(data = data, password = password)
            }
            doc
        }

    /**
     * Opens a text page for text extraction and operations
     *
     * @param pageIndex Index of the page to open
     * @return [PdfTextPage] instance for the specified page
     * @throws IllegalStateException If the document is closed
     */
    suspend fun openTextPage(pageIndex: Int): PdfTextPage = withContext(context = Dispatchers.IO) {
        return@withContext doc.openTextPage(pageIndex = pageIndex)
    }

    /**
     * Gets page width in pixels at current DPI
     *
     * @param pageIndex Index of the page
     * @return Page width in pixels or -1 if closed
     */
    suspend fun getPageWidthPixel(pageIndex: Int): Int = withContext(context = Dispatchers.IO) {
        mutexLock.withLock {
            if (handleAlreadyClosed(isClosed = isClosed || doc.isClosed)) return@withLock -1
            nativeGetPageWidthPixel(pagePtr = pagePtr(index = pageIndex), dpi = currentDpi)
        }
    }

    /**
     * Gets page height in pixels at current DPI
     *
     * @param pageIndex Index of the page
     * @return Page height in pixels or -1 if closed
     */
    suspend fun getPageHeightPixel(pageIndex: Int): Int = withContext(context = Dispatchers.IO) {
        mutexLock.withLock {
            if (handleAlreadyClosed(isClosed = isClosed || doc.isClosed)) return@withLock -1
            nativeGetPageHeightPixel(pagePtr = pagePtr(index = pageIndex), dpi = currentDpi)
        }
    }

    /**
     * Gets page width in PDF points (1/72 inch)
     *
     * @param pageIndex Index of the page
     * @return Page width in points or -1 if closed
     */
    suspend fun getPageWidthPoint(pageIndex: Int): Int = withContext(context = Dispatchers.IO) {
        mutexLock.withLock {
            if (handleAlreadyClosed(isClosed = isClosed || doc.isClosed)) return@withLock -1
            nativeGetPageWidthPoint(pagePtr = pagePtr(index = pageIndex))
        }
    }

    /**
     * Gets page height in pixels at current DPI
     *
     * @param pageIndex Index of the page
     * @return Page height in pixels or -1 if closed
     */
    suspend fun getPageHeightPoint(pageIndex: Int): Int = withContext(context = Dispatchers.IO) {
        mutexLock.withLock {
            if (handleAlreadyClosed(isClosed = isClosed || doc.isClosed)) return@withLock -1
            nativeGetPageHeightPoint(pagePtr = pagePtr(index = pageIndex))
        }
    }

    /**
     * Gets page rotation in degrees
     *
     * @param pageIndex Index of the page
     * @return One of:
     * - -1: Error
     * - 0: No rotation
     * - 1: 90° clockwise
     * - 2: 180°
     * - 3: 270° clockwise
     * @throws IllegalStateException If document is closed
     */
    suspend fun getPageRotation(pageIndex: Int): Int = withContext(context = Dispatchers.IO) {
        mutexLock.withLock {
            check(!isClosed && !doc.isClosed) { "Document is already closed" }
            nativeGetPageRotation(pagePtr = pagePtr(index = pageIndex))
        }
    }

    /**
     * Gets page crop box in PDF coordinates
     *
     * @param pageIndex Index of the page
     * @return [RectF] representing crop box
     * @throws IllegalStateException If document is closed
     */
    suspend fun getPageCropBox(pageIndex: Int): RectF = withContext(context = Dispatchers.IO) {
        mutexLock.withLock {
            check(!isClosed && !doc.isClosed) { "Document is already closed" }
            buildRect(values = nativeGetPageCropBox(pagePtr = pagePtr(index = pageIndex)))
        }
    }

    /**
     * Gets page media box in PDF coordinates.
     *
     * @param pageIndex Index of the page.
     * @return [RectF] representing media box.
     * @throws IllegalStateException If document is closed.
     */
    suspend fun getPageMediaBox(pageIndex: Int): RectF = withContext(context = Dispatchers.IO) {
        mutexLock.withLock {
            check(!isClosed && !doc.isClosed) { "Document is already closed" }
            buildRect(values = nativeGetPageMediaBox(pagePtr = pagePtr(index = pageIndex)))
        }
    }

    /**
     * Gets page bleed box in PDF coordinates.
     *
     * @param pageIndex Index of the page.
     * @return [RectF] representing bleed box.
     * @throws IllegalStateException If document is closed.
     */
    suspend fun getPageBleedBox(pageIndex: Int): RectF = withContext(context = Dispatchers.IO) {
        mutexLock.withLock {
            check(!isClosed && !doc.isClosed) { "Document is already closed" }
            buildRect(values = nativeGetPageBleedBox(pagePtr = pagePtr(index = pageIndex)))
        }
    }

    /**
     * Gets page trim box in PDF coordinates.
     *
     * @param pageIndex Index of the page.
     * @return [RectF] representing trim box.
     * @throws IllegalStateException If document is closed.
     */
    suspend fun getPageTrimBox(pageIndex: Int): RectF = withContext(context = Dispatchers.IO) {
        mutexLock.withLock {
            check(!isClosed && !doc.isClosed) { "Document is already closed" }
            buildRect(values = nativeGetPageTrimBox(pagePtr = pagePtr(index = pageIndex)))
        }
    }

    /**
     * Gets page art box in PDF coordinates.
     *
     * @param pageIndex Index of the page.
     * @return [RectF] representing art box.
     * @throws IllegalStateException If document is closed.
     */
    suspend fun getPageArtBox(pageIndex: Int): RectF = withContext(context = Dispatchers.IO) {
        mutexLock.withLock {
            check(!isClosed && !doc.isClosed) { "Document is already closed" }
            buildRect(values = nativeGetPageArtBox(pagePtr = pagePtr(index = pageIndex)))
        }
    }

    /**
     * Gets page bounding box in PDF coordinates.
     *
     * @param pageIndex Index of the page.
     * @return [RectF] representing bounding box.
     * @throws IllegalStateException If document is closed.
     */
    suspend fun getPageBoundingBox(pageIndex: Int): RectF = withContext(context = Dispatchers.IO) {
        mutexLock.withLock {
            check(!isClosed && !doc.isClosed) { "Document is already closed" }
            buildRect(values = nativeGetPageBoundingBox(pagePtr = pagePtr(index = pageIndex)))
        }
    }

    /**
     * Gets page size in pixels.
     *
     * @param pageIndex Index of the page.
     * @return [Size] representing page dimensions.
     * @throws IllegalStateException If document is closed.
     */
    suspend fun getPageSize(pageIndex: Int): Size = withContext(context = Dispatchers.IO) {
        mutexLock.withLock {
            check(!isClosed && !doc.isClosed) { "Document is already closed" }
            nativeGetPageSizeByIndex(
                docPtr = doc.nativeDocPtr,
                pageIndex = pageIndex,
                dpi = currentDpi,
            )
        }
    }

    /**
     * Renders PDF page to a bitmap buffer
     *
     * @param pageIndex Page index to render
     * @param bufferPtr Native pointer to bitmap buffer
     * @param startX X starting position in pixels
     * @param startY Y starting position in pixels
     * @param drawSizeX Horizontal draw size in pixels
     * @param drawSizeY Vertical draw size in pixels
     * @param annotation Whether to render annotations
     * @param canvasColor Background color for the canvas
     * @param pageBackgroundColor Background color for the page
     * @return true if rendering succeeded
     */
    suspend fun renderPage(
        pageIndex: Int,
        bufferPtr: Long,
        startX: Int,
        startY: Int,
        drawSizeX: Int,
        drawSizeY: Int,
        annotation: Boolean = false,
        @ColorInt canvasColor: Int = 0xFF848484.toInt(),
        @ColorInt pageBackgroundColor: Int = 0xFFFFFFFF.toInt(),
    ): Boolean = withContext(context = Dispatchers.IO) {
        mutexLock.withLock {
            if (handleAlreadyClosed(isClosed = isClosed || doc.isClosed)) return@withLock false
            try {
                return@withLock nativeRenderPage(
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
                return@withLock false
            } catch (e: Exception) {
                Log.e(TAG, "Exception throw from native", e)
                return@withLock false
            }
        }
    }

    /**
     * Renders PDF page to a bitmap buffer with transformation matrix.
     *
     * @param pageIndex Page index to render.
     * @param bufferPtr Native pointer to bitmap buffer.
     * @param drawSizeX Horizontal draw size in pixels.
     * @param drawSizeY Vertical draw size in pixels.
     * @param matrix Transformation matrix to apply.
     * @param clipRect Clipping rectangle in page coordinates.
     * @param annotation Whether to render annotations.
     * @param textMask Whether to render text as mask.
     * @param canvasColor Background color for the canvas.
     * @param pageBackgroundColor Background color for the page.
     * @return true if rendering succeeded.
     */
    suspend fun renderPage(
        pageIndex: Int,
        bufferPtr: Long,
        drawSizeX: Int,
        drawSizeY: Int,
        matrix: Matrix,
        clipRect: RectF,
        annotation: Boolean = false,
        textMask: Boolean = false,
        @ColorInt canvasColor: Int = 0xFF848484.toInt(),
        @ColorInt pageBackgroundColor: Int = 0xFFFFFFFF.toInt(),
    ): Boolean = withContext(context = Dispatchers.IO) {
        mutexLock.withLock {
            if (handleAlreadyClosed(isClosed = isClosed || doc.isClosed)) return@withLock false
            val matrixValues = FloatArray(size = THREE_BY_THREE).apply { matrix.getValues(this) }
            nativeRenderPageWithMatrix(
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

    /**
     * Renders PDF page to a Surface with transformation matrix
     *
     * @param pageIndex Page index to render
     * @param surface Target rendering surface
     * @param matrix Transformation matrix to apply
     * @param clipRect Clipping rectangle in page coordinates
     * @param annotation Whether to render annotations
     * @param canvasColor Background color for the canvas
     * @param pageBackgroundColor Background color for the page
     * @return true if rendering succeeded
     */
    suspend fun renderPage(
        pageIndex: Int,
        surface: Surface,
        matrix: Matrix,
        clipRect: RectF,
        annotation: Boolean = false,
        @ColorInt canvasColor: Int = 0xFF848484.toInt(),
        @ColorInt pageBackgroundColor: Int = 0xFFFFFFFF.toInt(),
    ): Boolean = withContext(context = Dispatchers.IO) {
        mutexLock.withLock {
            if (handleAlreadyClosed(isClosed = isClosed || doc.isClosed)) return@withLock false
            val matrixValues = FloatArray(THREE_BY_THREE).apply { matrix.getValues(this) }
            return@withLock nativeRenderPageSurfaceWithMatrix(
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

    /**
     * Renders PDF page to a Bitmap
     *
     * @param pageIndex Page index to render
     * @param bitmap Target bitmap for rendering
     * @param startX X starting position in pixels
     * @param startY Y starting position in pixels
     * @param drawSizeX Horizontal draw size in pixels
     * @param drawSizeY Vertical draw size in pixels
     * @param annotation Whether to render annotations
     * @param canvasColor Background color for the canvas
     * @param pageBackgroundColor Background color for the page
     */
    suspend fun renderPageBitmap(
        pageIndex: Int,
        bitmap: Bitmap?,
        startX: Int,
        startY: Int,
        drawSizeX: Int,
        drawSizeY: Int,
        annotation: Boolean = false,
        @ColorInt canvasColor: Int = 0xFF848484.toInt(),
        @ColorInt pageBackgroundColor: Int = 0xFFFFFFFF.toInt(),
    ) = withContext(context = Dispatchers.IO) {
        mutexLock.withLock {
            if (handleAlreadyClosed(isClosed = isClosed || doc.isClosed)) return@withLock
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

    /**
     * Renders PDF page to a Bitmap with transformation matrix
     *
     * @param pageIndex Page index to render
     * @param bitmap Target bitmap for rendering
     * @param matrix Transformation matrix to apply
     * @param clipRect Clipping rectangle in page coordinates
     * @param annotation Whether to render annotations
     * @param pageBackgroundColor Background color for the page
     */
    suspend fun renderPageBitmap(
        pageIndex: Int,
        bitmap: Bitmap?,
        matrix: Matrix,
        clipRect: RectF,
        annotation: Boolean = false,
        @ColorInt pageBackgroundColor: Int = 0xFFFFFFFF.toInt(),
    ) = withContext(context = Dispatchers.IO) {
        mutexLock.withLock {
            if (handleAlreadyClosed(isClosed = isClosed || doc.isClosed)) return@withLock
            val matrixValues = FloatArray(THREE_BY_THREE).apply { matrix.getValues(this) }
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

    /**
     * Retrieves list of links present on the page
     *
     * @param pageIndex Index of the page to scan
     * @param size Page dimensions in pixels
     * @param posX X coordinate for link detection
     * @param posY Y coordinate for link detection
     * @return List of detected [PdfDocument.Link] objects
     */
    suspend fun getPageLinks(
        pageIndex: Int,
        size: SizeF,
        posX: Float,
        posY: Float
    ): List<PdfDocument.Link> = withContext(context = Dispatchers.IO) {
        mutexLock.withLock {
            if (handleAlreadyClosed(isClosed = isClosed || doc.isClosed)) return@withLock emptyList()
            val pagePtr: Long = pagePtr(index = pageIndex)
            return@withLock nativeGetPageLinks(pagePtr = pagePtr)
                .toList()
                .mapNotNull { linkPtr ->
                    try {
                        val rect: RectF = nativeGetLinkRect(linkPtr = linkPtr)
                        val index: Int = nativeGetDestPageIndex(
                            docPtr = doc.nativeDocPtr,
                            linkPtr = linkPtr
                        )
                        val uri: String? = nativeGetLinkURI(
                            docPtr = doc.nativeDocPtr,
                            linkPtr = linkPtr
                        ) ?: nativeGetLinkURI(
                            docPtr = doc.nativeDocPtr,
                            linkPtr = nativeGetLinkAtCoord(
                                pagePtr = pagePtr,
                                width = size.width.toInt(),
                                height = size.height.toInt(),
                                posX = posX.toInt(),
                                posY = posY.toInt()
                            )
                        )
                        PdfDocument.Link(bounds = rect, destPageIndex = index, uri = uri)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing link", e)
                        null
                    }
                }
        }
    }

    /**
     * Converts page coordinates to device coordinates
     *
     * @param pageIndex Index of the page
     * @param startX Left coordinate of the display area
     * @param startY Top coordinate of the display area
     * @param sizeX Width of the display area
     * @param sizeY Height of the display area
     * @param rotate Page rotation (0-3)
     * @param pageX X coordinate in page space
     * @param pageY Y coordinate in page space
     * @return Converted [Point] in device coordinates
     * @throws IllegalStateException If document is closed
     */
    suspend fun mapPageCoordsToDevice(
        pageIndex: Int,
        startX: Int,
        startY: Int,
        sizeX: Int,
        sizeY: Int,
        rotate: Int,
        pageX: Double,
        pageY: Double,
    ): Point = withContext(context = Dispatchers.IO) {
        mutexLock.withLock {
            check(!isClosed && !doc.isClosed) { "Document is already closed" }
            return@withLock nativePageCoordsToDevice(
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
    }

    /**
     * Converts device coordinates to page coordinates
     *
     * @param pageIndex Index of the page
     * @param startX Left coordinate of the display area
     * @param startY Top coordinate of the display area
     * @param sizeX Width of the display area
     * @param sizeY Height of the display area
     * @param rotate Page rotation (0-3)
     * @param deviceX X coordinate in device space
     * @param deviceY Y coordinate in device space
     * @return Converted [PointF] in page coordinates
     * @throws IllegalStateException If document is closed
     */
    suspend fun mapDeviceCoordsToPage(
        pageIndex: Int,
        startX: Int,
        startY: Int,
        sizeX: Int,
        sizeY: Int,
        rotate: Int,
        deviceX: Int,
        deviceY: Int,
    ): PointF = withContext(context = Dispatchers.IO) {
        mutexLock.withLock {
            check(!isClosed && !doc.isClosed) { "Document is already closed" }
            return@withLock nativeDeviceCoordsToPage(
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
    }

    /**
     * Converts a rectangle from page coordinates to device coordinates
     *
     * @param pageIndex Index of the page
     * @param startX Left coordinate of the display area
     * @param startY Top coordinate of the display area
     * @param sizeX Width of the display area
     * @param sizeY Height of the display area
     * @param rotate Page rotation (0-3)
     * @param coords Rectangle in page coordinates to convert
     * @return Converted [RectF] in device coordinates
     * @throws IllegalStateException If document is closed
     */
    suspend fun mapRectToDevice(
        pageIndex: Int,
        startX: Int,
        startY: Int,
        sizeX: Int,
        sizeY: Int,
        rotate: Int,
        coords: RectF,
    ): RectF = withContext(context = Dispatchers.IO) {
        mutexLock.withLock {
            check(!isClosed && !doc.isClosed) { "Document is already closed" }
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
            RectF(
                leftTop.x.toFloat(),
                leftTop.y.toFloat(),
                rightBottom.x.toFloat(),
                rightBottom.y.toFloat(),
            )
        }
    }

    /**
     * Converts a rectangle from device coordinates to page coordinates
     *
     * @param pageIndex Index of the page
     * @param startX Left coordinate of the display area
     * @param startY Top coordinate of the display area
     * @param sizeX Width of the display area
     * @param sizeY Height of the display area
     * @param rotate Page rotation (0-3)
     * @param coords Rectangle in device coordinates to convert
     * @return Converted [RectF] in page coordinates
     * @throws IllegalStateException If document is closed
     */
    suspend fun mapRectToPage(
        pageIndex: Int,
        startX: Int,
        startY: Int,
        sizeX: Int,
        sizeY: Int,
        rotate: Int,
        coords: Rect,
    ): RectF = withContext(context = Dispatchers.IO) {
        mutexLock.withLock {
            check(!isClosed && !doc.isClosed) { "Document is already closed" }
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
            RectF(
                leftTop.x,
                leftTop.y,
                rightBottom.x,
                rightBottom.y
            )
        }
    }

    override fun close() {
        runBlocking(context = Dispatchers.IO) {
            mutexLock.withLock {
                if (handleAlreadyClosed(isClosed = isClosed || doc.isClosed)) return@withLock
                isClosed = true
                Log.v(TAG, "Closing PdfDocument")
                doc.pageMap.keys.forEach { index ->
                    doc.pageMap[index]?.let { page ->
                        if (page.count > 1) {
                            page.count--
                        } else {
                            nativeClosePage(pagePtr = page.pagePtr)
                            doc.pageMap.remove(key = index)
                        }
                    }
                }
                doc.close()
            }
        }
    }

    companion object {
        private const val THREE_BY_THREE: Int = 9
        val lock: Any = Any()
        val mutexLock: Mutex = Mutex()
        val surfaceMutex: Mutex = Mutex()
        val TAG: String = PdfiumCore::class.java.name

        @JvmStatic
        private external fun nativeClosePage(pagePtr: Long)

        @JvmStatic
        private external fun nativeClosePages(pagesPtr: LongArray)

        @JvmStatic
        @FastNative
        private external fun nativeDeviceCoordsToPage(
            pagePtr: Long, startX: Int, startY: Int, sizeX: Int, sizeY: Int,
            rotate: Int, deviceX: Int, deviceY: Int,
        ): PointF

        @JvmStatic
        private external fun nativeGetDestPageIndex(docPtr: Long, linkPtr: Long): Int

        @JvmStatic
        private external fun nativeGetLinkAtCoord(
            pagePtr: Long, width: Int, height: Int, posX: Int, posY: Int
        ): Long

        @JvmStatic
        private external fun nativeGetLinkRect(linkPtr: Long): RectF

        @JvmStatic
        private external fun nativeGetLinkURI(docPtr: Long, linkPtr: Long): String?

        @JvmStatic
        @FastNative
        private external fun nativeGetPageArtBox(pagePtr: Long): FloatArray

        @JvmStatic
        @FastNative
        private external fun nativeGetPageBleedBox(pagePtr: Long): FloatArray

        @JvmStatic
        @FastNative
        private external fun nativeGetPageBoundingBox(pagePtr: Long): FloatArray

        @JvmStatic
        @FastNative
        private external fun nativeGetPageCropBox(pagePtr: Long): FloatArray

        @JvmStatic
        @FastNative
        private external fun nativeGetPageHeightPixel(pagePtr: Long, dpi: Int): Int

        @JvmStatic
        @FastNative
        private external fun nativeGetPageHeightPoint(pagePtr: Long): Int

        @JvmStatic
        private external fun nativeGetPageLinks(pagePtr: Long): LongArray

        @JvmStatic
        @FastNative
        private external fun nativeGetPageMediaBox(pagePtr: Long): FloatArray

        @JvmStatic
        @FastNative
        private external fun nativeGetPageRotation(pagePtr: Long): Int

        @JvmStatic
        private external fun nativeGetPageSizeByIndex(docPtr: Long, pageIndex: Int, dpi: Int): Size

        @JvmStatic
        @FastNative
        private external fun nativeGetPageTrimBox(pagePtr: Long): FloatArray

        @JvmStatic
        @FastNative
        private external fun nativeGetPageWidthPixel(pagePtr: Long, dpi: Int): Int

        @JvmStatic
        @FastNative
        private external fun nativeGetPageWidthPoint(pagePtr: Long): Int

        @JvmStatic
        private external fun nativeOpenDocument(parcelFileDescriptor: Int, password: String?): Long

        @JvmStatic
        private external fun nativeOpenMemDocument(data: ByteArray, password: String?): Long

        @JvmStatic
        @FastNative
        private external fun nativePageCoordsToDevice(
            pagePtr: Long, startX: Int, startY: Int, sizeX: Int, sizeY: Int,
            rotate: Int, pageX: Double, pageY: Double,
        ): Point

        @JvmStatic
        private external fun nativeRenderPage(
            pagePtr: Long, bufferPtr: Long, startX: Int, startY: Int, drawSizeHor: Int,
            drawSizeVer: Int, annotation: Boolean, canvasColor: Int, pageBackgroundColor: Int,
        ): Boolean

        @JvmStatic
        private external fun nativeRenderPageBitmap(
            docPtr: Long, pagePtr: Long, bitmap: Bitmap?, startX: Int, startY: Int,
            drawSizeHor: Int, drawSizeVer: Int, annotation: Boolean, canvasColor: Int,
            pageBackgroundColor: Int,
        )

        @JvmStatic
        private external fun nativeRenderPageWithMatrix(
            pagePtr: Long, bufferPtr: Long, drawSizeHor: Int, drawSizeVer: Int, matrix: FloatArray,
            clipRect: FloatArray, annotation: Boolean, textMask: Boolean = false,
            canvasColor: Int, pageBackgroundColor: Int,
        ): Boolean

        @JvmStatic
        private external fun nativeRenderPageSurface(
            pagePtr: Long, surface: Surface, startX: Int, startY: Int,
            annotation: Boolean, canvasColor: Int, pageBackgroundColor: Int,
        ): Boolean

        @JvmStatic
        private external fun nativeRenderPageSurfaceWithMatrix(
            pagePtr: Long, surface: Surface, matrix: FloatArray, clipRect: FloatArray,
            annotation: Boolean, canvasColor: Int, pageBackgroundColor: Int,
        ): Boolean

        @JvmStatic
        private external fun nativeRenderPageBitmapWithMatrix(
            pagePtr: Long, bitmap: Bitmap?, matrix: FloatArray, clipRect: RectF,
            annotation: Boolean, pageBackgroundColor: Int,
        )

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