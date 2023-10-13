package com.ahmer.pdfium

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Point
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.util.Log
import android.view.Surface
import com.ahmer.pdfium.util.Size
import java.io.Closeable

class PdfPage(
    val doc: PdfDocument, val pageIndex: Int, val pagePtr: Long,
    private val pageMap: MutableMap<Int, PdfDocument.PageCount>
) : Closeable {
    private var isClosed = false

    private external fun nativeClosePage(pagePtr: Long)
    private external fun nativeClosePages(pagesPtr: LongArray)
    private external fun nativeDeviceCoordsToPage(
        pagePtr: Long, startX: Int, startY: Int, sizeX: Int, sizeY: Int,
        rotate: Int, deviceX: Double, deviceY: Double
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
    private external fun nativeGetPageSizeByIndex(docPtr: Long, pageIndex: Int, dpi: Int): Size
    private external fun nativeGetPageTrimBox(pagePtr: Long): FloatArray
    private external fun nativeGetPageWidthPixel(pagePtr: Long, dpi: Int): Int
    private external fun nativeGetPageWidthPoint(pagePtr: Long): Int
    private external fun nativePageCoordsToDevice(
        pagePtr: Long, startX: Int, startY: Int, sizeX: Int, sizeY: Int,
        rotate: Int, pageX: Double, pageY: Double
    ): Point

    private external fun nativeRenderPage(
        pagePtr: Long, surface: Surface?, startX: Int, startY: Int,
        drawSizeHor: Int, drawSizeVer: Int, renderAnnot: Boolean
    )

    private external fun nativeRenderPageBitmap(
        pagePtr: Long, bitmap: Bitmap?, startX: Int, startY: Int,
        drawSizeHor: Int, drawSizeVer: Int, renderAnnot: Boolean
    )

    private external fun nativeRenderPageBitmapWithMatrix(
        pagePtr: Long, bitmap: Bitmap?, matrix: FloatArray, clipRect: RectF, renderAnnot: Boolean
    )

    /**
     * Get page width in pixels.
     * @param screenDpi screen DPI (Dots Per Inch)
     * @return page width in pixels
     *  @throws IllegalStateException If the page or document is closed
     */
    fun getPageWidth(screenDpi: Int): Int {
        check(value = !isClosed && !doc.isClosed) { "Already closed" }
        synchronized(PdfiumCore.lock) {
            return nativeGetPageWidthPixel(pagePtr, screenDpi)
        }
    }

    /**
     * Get page height in pixels.
     * @param screenDpi screen DPI (Dots Per Inch)
     * @return page height in pixels
     *  @throws IllegalStateException If the page or document is closed
     */
    fun getPageHeight(screenDpi: Int): Int {
        check(value = !isClosed && !doc.isClosed) { "Already closed" }
        synchronized(PdfiumCore.lock) {
            return nativeGetPageHeightPixel(pagePtr, screenDpi)
        }
    }

    /**
     * Get page width in PostScript points (1/72th of an inch).
     * @return page width in points
     *  @throws IllegalStateException If the page or document is closed
     */
    fun getPageWidthPoint(): Int {
        check(value = !isClosed && !doc.isClosed) { "Already closed" }
        synchronized(PdfiumCore.lock) {
            return nativeGetPageWidthPoint(pagePtr)
        }
    }

    /**
     * Get page height in PostScript points (1/72th of an inch)
     * @return page height in points
     *  @throws IllegalStateException If the page or document is closed
     */
    fun getPageHeightPoint(): Int {
        check(value = !isClosed && !doc.isClosed) { "Already closed" }
        synchronized(PdfiumCore.lock) {
            return nativeGetPageHeightPoint(pagePtr)
        }
    }

    /**
     *  Get the page's crop box in PostScript points (1/72th of an inch)
     *  @return page crop box in points or RectF(-1, -1, -1, -1) if not present
     *  @throws IllegalStateException If the page or document is closed
     */
    fun getPageCropBox(): RectF {
        check(value = !isClosed && !doc.isClosed) { "Already closed" }
        synchronized(PdfiumCore.lock) {
            val pageBound = nativeGetPageCropBox(pagePtr)
            val rectF = RectF()
            rectF.left = pageBound[LEFT]
            rectF.top = pageBound[TOP]
            rectF.right = pageBound[RIGHT]
            rectF.bottom = pageBound[BOTTOM]
            return rectF
        }
    }

    /**
     *  Get the page's media box in PostScript points (1/72th of an inch)
     *  @return page media box in points or RectF(-1, -1, -1, -1) if not present
     *  @throws IllegalStateException If the page or document is closed
     */
    fun getPageMediaBox(): RectF {
        check(value = !isClosed && !doc.isClosed) { "Already closed" }
        synchronized(PdfiumCore.lock) {
            val pageBound = nativeGetPageMediaBox(pagePtr)
            val rectF = RectF()
            rectF.left = pageBound[LEFT]
            rectF.top = pageBound[TOP]
            rectF.right = pageBound[RIGHT]
            rectF.bottom = pageBound[BOTTOM]
            return rectF
        }
    }


    /**
     *  Get the page's bleed box in PostScript points (1/72th of an inch)
     *  @return page bleed box in pointsor RectF(-1, -1, -1, -1) if not present
     *  @throws IllegalStateException If the page or document is closed
     */
    fun getPageBleedBox(): RectF {
        check(value = !isClosed && !doc.isClosed) { "Already closed" }
        synchronized(PdfiumCore.lock) {
            val pageBound = nativeGetPageBleedBox(pagePtr)
            val rectF = RectF()
            rectF.left = pageBound[LEFT]
            rectF.top = pageBound[TOP]
            rectF.right = pageBound[RIGHT]
            rectF.bottom = pageBound[BOTTOM]
            return rectF
        }
    }

    /**
     *  Get the page's trim box in PostScript points (1/72th of an inch)
     *  @return page trim box in points or RectF(-1, -1, -1, -1) if not present
     *  @throws IllegalStateException If the page or document is closed
     */
    fun getPageTrimBox(): RectF {
        check(value = !isClosed && !doc.isClosed) { "Already closed" }
        synchronized(PdfiumCore.lock) {
            val pageBound = nativeGetPageTrimBox(pagePtr)
            val rectF = RectF()
            rectF.left = pageBound[LEFT]
            rectF.top = pageBound[TOP]
            rectF.right = pageBound[RIGHT]
            rectF.bottom = pageBound[BOTTOM]
            return rectF
        }
    }

    /**
     *  Get the page's art box in PostScript points (1/72th of an inch)
     *  @return page art box in points or RectF(-1, -1, -1, -1) if not present
     *  @throws IllegalStateException If the page or document is closed
     */
    fun getPageArtBox(): RectF {
        check(value = !isClosed && !doc.isClosed) { "Already closed" }
        synchronized(PdfiumCore.lock) {
            val pageBound = nativeGetPageArtBox(pagePtr)
            val rectF = RectF()
            rectF.left = pageBound[LEFT]
            rectF.top = pageBound[TOP]
            rectF.right = pageBound[RIGHT]
            rectF.bottom = pageBound[BOTTOM]
            return rectF
        }
    }

    /**
     *  Get the page's bounding box in PostScript points (1/72th of an inch)
     *  @return page bounding box in points or RectF(-1, -1, -1, -1) if not present
     *  @throws IllegalStateException If the page or document is closed
     */
    fun getPageBoundingBox(): RectF {
        check(value = !isClosed && !doc.isClosed) { "Already closed" }
        synchronized(PdfiumCore.lock) {
            val pageBound = nativeGetPageBoundingBox(pagePtr)
            val rectF = RectF()
            rectF.left = pageBound[LEFT]
            rectF.top = pageBound[TOP]
            rectF.right = pageBound[RIGHT]
            rectF.bottom = pageBound[BOTTOM]
            return rectF
        }
    }

    /**
     *  Get the page's size in pixels
     *  @return page size in pixels
     *  @throws IllegalStateException If the page or document is closed
     */
    fun getPageSize(screenDpi: Int): Size {
        check(value = !isClosed && !doc.isClosed) { "Already closed" }
        synchronized(PdfiumCore.lock) {
            return nativeGetPageSizeByIndex(doc.nativeDocPtr, pageIndex, screenDpi)
        }
    }

    /**
     * Render page fragment on [Surface].<br></br>
     * @param surface Surface on which to render page
     * @param startX left position of the page in the surface
     * @param startY top position of the page in the surface
     * @param drawSizeX horizontal size of the page on the surface
     * @param drawSizeY vertical size of the page on the surface
     * @param annot whether render annotation
     * @throws IllegalStateException If the page or document is closed
     */
    fun renderPage(
        surface: Surface?, startX: Int, startY: Int, drawSizeX: Int, drawSizeY: Int, annot: Boolean
    ) {
        check(value = !isClosed && !doc.isClosed) { "Already closed" }
        synchronized(PdfiumCore.lock) {
            try {
                // nativeRenderPage(doc.mNativePagesPtr.get(pageIndex), surface, mCurrentDpi)
                nativeRenderPage(pagePtr, surface, startX, startY, drawSizeX, drawSizeY, annot)
            } catch (e: NullPointerException) {
                Log.e(TAG, "context may be null", e)
            } catch (e: Exception) {
                Log.e(TAG, "Exception throw from native", e)
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
     * @param annot whether render annotation
     * @throws IllegalStateException If the page or document is closed
     *
     * Supported bitmap configurations:
     *
     *  * ARGB_8888 - best quality, high memory usage, higher possibility of OutOfMemoryError
     *  * RGB_565 - little worse quality, 1/2 the memory usage
     *
     */
    fun renderPageBitmap(
        bitmap: Bitmap?, startX: Int, startY: Int, drawSizeX: Int, drawSizeY: Int, annot: Boolean
    ) {
        check(value = !isClosed && !doc.isClosed) { "Already closed" }
        synchronized(PdfiumCore.lock) {
            nativeRenderPageBitmap(pagePtr, bitmap, startX, startY, drawSizeX, drawSizeY, annot)
        }
    }

    fun renderPageBitmap(bitmap: Bitmap?, matrix: Matrix, clipRect: RectF, renderAnnot: Boolean) {
        check(value = !isClosed && !doc.isClosed) { "Already closed" }
        val matrixValues = FloatArray(THREE_BY_THREE)
        matrix.getValues(matrixValues)
        synchronized(PdfiumCore.lock) {
            nativeRenderPageBitmapWithMatrix(
                pagePtr, bitmap,
                floatArrayOf(
                    matrixValues[Matrix.MSCALE_X],
                    matrixValues[Matrix.MSCALE_Y],
                    matrixValues[Matrix.MTRANS_X],
                    matrixValues[Matrix.MTRANS_Y]
                ),
                clipRect, renderAnnot
            )
        }
    }

    /**
     * Get all links from given page
     */
    fun getPageLinks(): List<PdfDocument.Link> {
        check(value = !isClosed && !doc.isClosed) { "Already closed" }
        synchronized(PdfiumCore.lock) {
            val links: MutableList<PdfDocument.Link> = ArrayList()
            val linkPtrs = nativeGetPageLinks(pagePtr)
            for (linkPtr in linkPtrs) {
                val index = nativeGetDestPageIndex(doc.nativeDocPtr, linkPtr)
                val uri = nativeGetLinkURI(doc.nativeDocPtr, linkPtr)
                val rect = nativeGetLinkRect(linkPtr)
                if (rect != null && (index != null || uri != null)) {
                    links.add(PdfDocument.Link(rect, index, uri))
                }
            }
            return links
        }
    }

    /**
     * Map page coordinates to device screen coordinates
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
        check(value = !isClosed && !doc.isClosed) { "Already closed" }
        return nativePageCoordsToDevice(pagePtr, startX, startY, sizeX, sizeY, rotate, pageX, pageY)
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
        check(value = !isClosed && !doc.isClosed) { "Already closed" }
        return nativeDeviceCoordsToPage(
            pagePtr, startX, startY, sizeX, sizeY, rotate, deviceX.toDouble(), deviceY.toDouble()
        )
    }

    /**
     * Maps a rectangle from device space to page space
     * @param startX    left pixel position of the display area in device coordinates
     * @param startY    top pixel position of the display area in device coordinates
     * @param sizeX     horizontal size (in pixels) for displaying the page
     * @param sizeY     vertical size (in pixels) for displaying the page
     * @param rotate    page orientation: 0 (normal), 1 (rotated 90 degrees clockwise),
     * 2 (rotated 180 degrees), 3 (rotated 90 degrees counter-clockwise)
     * @param coords    rectangle to map
     * @return mapped coordinates
     * @throws IllegalStateException If the page or document is closed
     */
    fun mapRectToPage(
        startX: Int, startY: Int, sizeX: Int, sizeY: Int, rotate: Int, coords: Rect
    ): RectF {
        check(value = !isClosed && !doc.isClosed) { "Already closed" }
        val leftTop = mapDeviceCoordsToPage(
            startX, startY, sizeX, sizeY, rotate, coords.left, coords.top
        )
        val rightBottom = mapDeviceCoordsToPage(
            startX, startY, sizeX, sizeY, rotate, coords.right, coords.bottom
        )
        return RectF(leftTop.x, leftTop.y, rightBottom.x, rightBottom.y)
    }

    /**
     * Close the page and release all resources
     */
    override fun close() {
        if (isClosed) return
        synchronized(PdfiumCore.lock) {
            pageMap[pageIndex]?.let {
                if (it.count > 1) {
                    it.count--
                    return
                }
            }
            pageMap.remove(pageIndex)
            isClosed = true
            nativeClosePage(pagePtr)
        }
    }

    companion object {
        private const val THREE_BY_THREE = 9
        private val TAG = PdfPage::class.java.name

        const val LEFT = 0
        const val TOP = 1
        const val RIGHT = 2
        const val BOTTOM = 3
    }
}