package com.ahmer.pdfium

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Point
import android.graphics.PointF
import android.graphics.RectF
import android.util.Log
import android.view.Surface
import com.ahmer.pdfium.util.Size
import com.ahmer.pdfium.util.SizeF
import java.io.Closeable

/**
 * Represents a single page in a [PdfDocument].
 */
class PdfPage(
    val document: PdfDocument, val pageIndex: Int, val pagePtr: Long,
    private val pageMap: MutableMap<Int, PdfDocument.PageCount>
) : Closeable {
    private var isClosed = false

    private external fun nativeClosePage(pagePtr: Long)
    private external fun nativeClosePages(pagesPtr: LongArray)
    private external fun nativeDeviceCoordsToPage(
        pagePtr: Long, startX: Int, startY: Int, sizeX: Int, sizeY: Int, rotate: Int,
        deviceX: Int, deviceY: Int
    ): PointF

    private external fun nativeGetDestPageIndex(docPtr: Long, linkPtr: Long): Int?
    private external fun nativeGetLinkAtCoord(
        pagePtr: Long, width: Int, height: Int, posX: Int, posY: Int
    ): Long

    private external fun nativeGetLinkRect(linkPtr: Long): RectF?
    private external fun nativeGetLinkURI(docPtr: Long, linkPtr: Long): String?
    private external fun nativeGetPageArtBox(pagePtr: Long): FloatArray
    private external fun nativeGetPageBleedBox(pagePtr: Long): FloatArray
    private external fun nativeGetPageBoundingBox(pagePtr: Long): FloatArray
    private external fun nativeGetPageCropBox(pagePtr: Long): FloatArray
    private external fun nativeGetPageHeightPixel(pagePtr: Long, dpi: Int): Int
    private external fun nativeGetPageHeightPoint(pagePtr: Long): Int
    private external fun nativeGetPageLinks(pagePtr: Long): LongArray
    private external fun nativeGetPageMediaBox(pagePtr: Long): FloatArray
    private external fun nativeGetPageRotation(pagePtr: Long): Int
    private external fun nativeGetPageSizeByIndex(docPtr: Long, pageIndex: Int, dpi: Int): Size
    private external fun nativeGetPageTrimBox(pagePtr: Long): FloatArray
    private external fun nativeGetPageWidthPixel(pagePtr: Long, dpi: Int): Int
    private external fun nativeGetPageWidthPoint(pagePtr: Long): Int
    private external fun nativePageCoordsToDevice(
        pagePtr: Long, startX: Int, startY: Int, sizeX: Int, sizeY: Int, rotate: Int,
        pageX: Double, pageY: Double
    ): Point

    private external fun nativeRenderPage(
        pagePtr: Long, surface: Surface, startX: Int, startY: Int, drawSizeHor: Int,
        drawSizeVer: Int, annotation: Boolean
    )

    private external fun nativeRenderPageBitmap(
        pagePtr: Long, bitmap: Bitmap?, startX: Int, startY: Int, drawSizeHor: Int,
        drawSizeVer: Int, annotation: Boolean
    )

    private external fun nativeRenderPageBitmapWithMatrix(
        pagePtr: Long, bitmap: Bitmap?, matrix: FloatArray, clipRect: RectF, annotation: Boolean
    )

    /**
     * Open a text page
     * @return the opened [PdfTextPage]
     * @throws IllegalArgumentException if document is closed or the page cannot be loaded
     */
    fun openTextPage(): PdfTextPage = document.openTextPage(page = this)

    /**
     * Get page height in pixels.
     * @return page height in pixels
     * @throws IllegalStateException If the page or document is closed
     */
    val getPageHeight: Int by lazy {
        check(value = !isClosed && !document.isClosed) { "Already closed" }
        synchronized(lock = PdfiumCore.lock) {
            nativeGetPageHeightPixel(pagePtr = pagePtr, dpi = PdfiumCore.screenDpi)
        }
    }

    /**
     * Get page width in pixels.
     * @return page width in pixels
     * @throws IllegalStateException If the page or document is closed
     */
    val getPageWidth: Int by lazy {
        check(value = !isClosed && !document.isClosed) { "Already closed" }
        synchronized(lock = PdfiumCore.lock) {
            nativeGetPageWidthPixel(pagePtr = pagePtr, PdfiumCore.screenDpi)
        }
    }

    /**
     * Get page height in PostScript points (1/72th of an inch)
     * @return page height in points
     * @throws IllegalStateException If the page or document is closed
     */
    val getPageHeightPoint: Int by lazy {
        check(value = !isClosed && !document.isClosed) { "Already closed" }
        synchronized(lock = PdfiumCore.lock) {
            nativeGetPageHeightPoint(pagePtr = pagePtr)
        }
    }

    /**
     * Get page width in PostScript points (1/72th of an inch).
     * @return page width in points
     * @throws IllegalStateException If the page or document is closed
     */
    val getPageWidthPoint: Int by lazy {
        check(value = !isClosed && !document.isClosed) { "Already closed" }
        synchronized(lock = PdfiumCore.lock) {
            nativeGetPageWidthPoint(pagePtr = pagePtr)
        }
    }

    /**
     *  Get the page's size in pixels
     *  @return page size in pixels
     *  @throws IllegalStateException If the page or document is closed
     */
    val getPageSize: Size by lazy {
        check(value = !isClosed && !document.isClosed) { "Already closed" }
        synchronized(lock = PdfiumCore.lock) {
            nativeGetPageSizeByIndex(
                docPtr = document.nativeDocPtr, pageIndex = pageIndex, dpi = PdfiumCore.screenDpi
            )
        }
    }

    /**
     * Get page rotation in degrees
     * @return page rotation
     */
    val getPageRotation: Int by lazy {
        check(value = !isClosed && !document.isClosed) { "Already closed" }
        nativeGetPageRotation(pagePtr = pagePtr)
    }

    /**
     * Render page fragment on [Surface].<br></br>
     * @param surface Surface on which to render page
     * @param startX left position of the page in the surface
     * @param startY top position of the page in the surface
     * @param drawSizeX horizontal size of the page on the surface
     * @param drawSizeY vertical size of the page on the surface
     * @param annotation whether render annotation
     * @throws IllegalStateException If the page or document is closed
     */
    fun renderPage(
        surface: Surface, startX: Int, startY: Int,
        drawSizeX: Int, drawSizeY: Int, annotation: Boolean
    ) {
        check(value = !isClosed && !document.isClosed) { "Already closed" }
        synchronized(lock = PdfiumCore.lock) {
            try {
                nativeRenderPage(
                    pagePtr = pagePtr, surface = surface, startX = startX, startY = startY,
                    drawSizeHor = drawSizeX, drawSizeVer = drawSizeY, annotation = annotation
                )
            } catch (e: NullPointerException) {
                Log.e(TAG, "Context may be null", e)
                e.printStackTrace()
            } catch (e: Exception) {
                Log.e(TAG, "Exception throw from native", e)
                e.printStackTrace()
            }
        }
    }

    /**
     * Render page fragment on [Bitmap].<br></br>
     * @param bitmap Bitmap on which to render page
     * @param startX left position of the page in the bitmap
     * @param startY top position of the page in the bitmap
     * @param drawSizeX horizontal size of the page on the bitmap
     * @param drawSizeY vertical size of the page on the bitmap
     * @param annotation whether render annotation
     * @throws IllegalStateException If the page or document is closed
     *
     * Supported bitmap configurations:
     *
     *  * ARGB_8888 - best quality, high memory usage, higher possibility of OutOfMemoryError
     *  * RGB_565 - little worse quality, 1/2 the memory usage
     */
    fun renderPageBitmap(
        bitmap: Bitmap?, startX: Int, startY: Int,
        drawSizeX: Int, drawSizeY: Int, annotation: Boolean
    ) {
        check(value = !isClosed && !document.isClosed) { "Already closed" }
        synchronized(lock = PdfiumCore.lock) {
            nativeRenderPageBitmap(
                pagePtr = pagePtr, bitmap = bitmap, startX = startX, startY = startY,
                drawSizeHor = drawSizeX, drawSizeVer = drawSizeY, annotation = annotation
            )
        }
    }

    /**
     * Render page fragment on [Bitmap] with no annotation.<br></br>
     * @param bitmap Bitmap on which to render page
     * @param startX left position of the page in the bitmap
     * @param startY top position of the page in the bitmap
     * @param drawSizeX horizontal size of the page on the bitmap
     * @param drawSizeY vertical size of the page on the bitmap
     * @throws IllegalStateException If the page or document is closed
     *
     * Supported bitmap configurations:
     *
     *  * ARGB_8888 - best quality, high memory usage, higher possibility of OutOfMemoryError
     *  * RGB_565 - little worse quality, 1/2 the memory usage
     */
    fun renderPageBitmap(
        bitmap: Bitmap?, startX: Int, startY: Int, drawSizeX: Int, drawSizeY: Int
    ) {
        check(value = !isClosed && !document.isClosed) { "Already closed" }
        synchronized(lock = PdfiumCore.lock) {
            renderPageBitmap(
                bitmap = bitmap, startX = startX, startY = startY, drawSizeX = drawSizeX,
                drawSizeY = drawSizeY, annotation = false
            )
        }
    }

    fun renderPageBitmap(bitmap: Bitmap?, matrix: Matrix, clipRect: RectF, annotation: Boolean) {
        check(value = !isClosed && !document.isClosed) { "Already closed" }
        val matrixValues = FloatArray(size = THREE_BY_THREE)
        matrix.getValues(matrixValues)
        synchronized(lock = PdfiumCore.lock) {
            nativeRenderPageBitmapWithMatrix(
                pagePtr = pagePtr, bitmap = bitmap, matrix = floatArrayOf(
                    matrixValues[Matrix.MSCALE_X], matrixValues[Matrix.MSCALE_Y],
                    matrixValues[Matrix.MTRANS_X], matrixValues[Matrix.MTRANS_Y]
                ), clipRect = clipRect, annotation = annotation
            )
        }
    }

    /**
     * Get all links from given page
     */
    fun getPageLinks(size: SizeF, posX: Float, posY: Float): List<PdfDocument.Link> {
        check(value = !isClosed && !document.isClosed) { "Already closed" }
        synchronized(lock = PdfiumCore.lock) {
            val links: MutableList<PdfDocument.Link> = ArrayList()
            val pageLinks: LongArray = nativeGetPageLinks(pagePtr = pagePtr)
            val linkAtCoordinate: Long = nativeGetLinkAtCoord(
                pagePtr = pagePtr, width = size.width.toInt(), height = size.height.toInt(),
                posX = posX.toInt(), posY = posY.toInt()
            )
            for (linkPtr in pageLinks) {
                val index: Int? = nativeGetDestPageIndex(
                    docPtr = document.nativeDocPtr, linkPtr = linkAtCoordinate
                )
                val mUri: String? =
                    nativeGetLinkURI(docPtr = document.nativeDocPtr, linkPtr = linkPtr)
                val rectF: RectF? = nativeGetLinkRect(linkPtr = linkPtr)
                if (mUri == null) {
                    val uri: String? = nativeGetLinkURI(
                        docPtr = document.nativeDocPtr, linkPtr = linkAtCoordinate
                    )
                    links.add(PdfDocument.Link(bounds = rectF, destPageIndex = index, uri = uri))
                } else {
                    links.add(PdfDocument.Link(bounds = rectF, destPageIndex = index, uri = mUri))
                }
            }
            return links
        }
    }

    /**
     * Map device screen coordinates to page coordinates
     *
     * @param startX    left pixel position of the display area in device coordinates
     * @param startY    top pixel position of the display area in device coordinates
     * @param sizeX     horizontal size (in pixels) for displaying the page
     * @param sizeY     vertical size (in pixels) for displaying the page
     * @param rotate    page orientation: 0 (normal), 1 (rotated 90 degrees clockwise),
     * 2 (rotated 180 degrees), 3 (rotated 90 degrees counter-clockwise)
     * @param pageX     X value in page coordinates
     * @param pageY     Y value in page coordinate
     * @return mapped coordinates
     * @throws IllegalStateException If the page or document is closed
     */
    fun mapPageCoordsToDevice(
        startX: Int, startY: Int, sizeX: Int, sizeY: Int, rotate: Int, pageX: Double, pageY: Double
    ): Point {
        check(value = !isClosed && !document.isClosed) { "Already closed" }
        return nativePageCoordsToDevice(
            pagePtr = pagePtr, startX = startX, startY = startY, sizeX = sizeX, sizeY = sizeY,
            rotate = rotate, pageX = pageX, pageY = pageY
        )
    }

    /**
     * Maps a rectangle from page space to device space
     *
     * @param startX    left pixel position of the display area in device coordinates
     * @param startY    top pixel position of the display area in device coordinates
     * @param sizeX     horizontal size (in pixels) for displaying the page
     * @param sizeY     vertical size (in pixels) for displaying the page
     * @param rotate    page orientation: 0 (normal), 1 (rotated 90 degrees clockwise),
     * 2 (rotated 180 degrees), 3 (rotated 90 degrees counter-clockwise)
     * @param coords    rectangle to map
     *
     * @return mapped coordinates
     *
     * @throws IllegalStateException If the page or document is closed
     */
    fun mapRectToDevice(
        startX: Int, startY: Int, sizeX: Int, sizeY: Int, rotate: Int, coords: RectF
    ): RectF {
        val leftTop = mapPageCoordsToDevice(
            startX = startX, startY = startY, sizeX = sizeX, sizeY = sizeY, rotate = rotate,
            pageX = coords.left.toDouble(), pageY = coords.top.toDouble()
        )
        val rightBottom = mapPageCoordsToDevice(
            startX = startX, startY = startY, sizeX = sizeX, sizeY = sizeY, rotate = rotate,
            pageX = coords.right.toDouble(), pageY = coords.bottom.toDouble()
        )
        return RectF(
            leftTop.x.toFloat(), leftTop.y.toFloat(),
            rightBottom.x.toFloat(), rightBottom.y.toFloat()
        )
    }

    /**
     * Map device screen coordinates to page coordinates
     *
     * @param startX    left pixel position of the display area in device coordinates
     * @param startY    top pixel position of the display area in device coordinates
     * @param sizeX     horizontal size (in pixels) for displaying the page
     * @param sizeY     vertical size (in pixels) for displaying the page
     * @param rotate    page orientation: 0 (normal), 1 (rotated 90 degrees clockwise),
     * 2 (rotated 180 degrees), 3 (rotated 90 degrees counter-clockwise)
     * @param deviceX   X value in page coordinates
     * @param deviceY   Y value in page coordinate
     * @return mapped coordinates
     * @throws IllegalStateException If the page or document is closed
     */
    fun mapDeviceCoordsToPage(
        startX: Int, startY: Int, sizeX: Int, sizeY: Int, rotate: Int, deviceX: Int, deviceY: Int
    ): PointF {
        check(value = !isClosed && !document.isClosed) { "Already closed" }
        return nativeDeviceCoordsToPage(
            pagePtr = pagePtr, startX = startX, startY = startY, sizeX = sizeX, sizeY = sizeY,
            rotate = rotate, deviceX = deviceX, deviceY = deviceY
        )
    }

    /**
     *  Get the page's art box in PostScript points (1/72th of an inch)
     *  @return page art box in points or RectF(-1, -1, -1, -1) if not present
     *  @throws IllegalStateException If the page or document is closed
     */
    fun getPageArtBox(): RectF {
        check(value = !isClosed && !document.isClosed) { "Already closed" }
        synchronized(lock = PdfiumCore.lock) {
            val bound = nativeGetPageArtBox(pagePtr = pagePtr)
            return RectF().apply {
                left = bound[LEFT]
                top = bound[TOP]
                right = bound[RIGHT]
                bottom = bound[BOTTOM]
            }
        }
    }

    /**
     *  Get the page's bleed box in PostScript points (1/72th of an inch)
     *  @return page bleed box in points or RectF(-1, -1, -1, -1) if not present
     *  @throws IllegalStateException If the page or document is closed
     */
    fun getPageBleedBox(): RectF {
        check(value = !isClosed && !document.isClosed) { "Already closed" }
        synchronized(lock = PdfiumCore.lock) {
            val bound = nativeGetPageBleedBox(pagePtr = pagePtr)
            return RectF().apply {
                left = bound[LEFT]
                top = bound[TOP]
                right = bound[RIGHT]
                bottom = bound[BOTTOM]
            }
        }
    }

    /**
     *  Get the page's bounding box in PostScript points (1/72th of an inch)
     *  @return page bounding box in points or RectF(-1, -1, -1, -1) if not present
     *  @throws IllegalStateException If the page or document is closed
     */
    fun getPageBoundingBox(): RectF {
        check(value = !isClosed && !document.isClosed) { "Already closed" }
        synchronized(lock = PdfiumCore.lock) {
            val bound = nativeGetPageBoundingBox(pagePtr = pagePtr)
            return RectF().apply {
                left = bound[LEFT]
                top = bound[TOP]
                right = bound[RIGHT]
                bottom = bound[BOTTOM]
            }
        }
    }

    /**
     *  Get the page's crop box in PostScript points (1/72th of an inch)
     *  @return page crop box in points or RectF(-1, -1, -1, -1) if not present
     *  @throws IllegalStateException If the page or document is closed
     */
    fun getPageCropBox(): RectF {
        check(value = !isClosed && !document.isClosed) { "Already closed" }
        synchronized(lock = PdfiumCore.lock) {
            val bound = nativeGetPageCropBox(pagePtr = pagePtr)
            return RectF().apply {
                left = bound[LEFT]
                top = bound[TOP]
                right = bound[RIGHT]
                bottom = bound[BOTTOM]
            }
        }
    }

    /**
     *  Get the page's media box in PostScript points (1/72th of an inch)
     *  @return page media box in points or RectF(-1, -1, -1, -1) if not present
     *  @throws IllegalStateException If the page or document is closed
     */
    fun getPageMediaBox(): RectF {
        check(value = !isClosed && !document.isClosed) { "Already closed" }
        synchronized(lock = PdfiumCore.lock) {
            val bound = nativeGetPageMediaBox(pagePtr = pagePtr)
            return RectF().apply {
                left = bound[LEFT]
                top = bound[TOP]
                right = bound[RIGHT]
                bottom = bound[BOTTOM]
            }
        }
    }

    /**
     *  Get the page's trim box in PostScript points (1/72th of an inch)
     *  @return page trim box in points or RectF(-1, -1, -1, -1) if not present
     *  @throws IllegalStateException If the page or document is closed
     */
    fun getPageTrimBox(): RectF {
        check(value = !isClosed && !document.isClosed) { "Already closed" }
        synchronized(lock = PdfiumCore.lock) {
            val bound = nativeGetPageTrimBox(pagePtr = pagePtr)
            return RectF().apply {
                left = bound[LEFT]
                top = bound[TOP]
                right = bound[RIGHT]
                bottom = bound[BOTTOM]
            }
        }
    }

    /**
     * Close the page and release all resources
     */
    override fun close() {
        if (isClosed) return
        synchronized(lock = PdfiumCore.lock) {
            pageMap[pageIndex]?.let {
                if (it.count > 1) {
                    it.count--
                    return
                }
            }
            pageMap.remove(key = pageIndex)
            isClosed = true
            nativeClosePage(pagePtr = pagePtr)
        }
    }

    companion object {
        const val LEFT = 0
        const val TOP = 1
        const val RIGHT = 2
        const val BOTTOM = 3
        private const val THREE_BY_THREE = 9
        private val TAG = PdfPage::class.java.name
    }
}