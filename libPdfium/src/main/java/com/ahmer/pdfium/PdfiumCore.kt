package com.ahmer.pdfium

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Point
import android.graphics.PointF
import android.graphics.RectF
import android.os.ParcelFileDescriptor
import android.util.Log
import android.view.Surface
import com.ahmer.pdfium.util.Size
import com.ahmer.pdfium.util.SizeF
import java.io.Closeable
import java.io.IOException

class PdfiumCore : Closeable {
    private val document: PdfDocument = PdfDocument()
    private var isClosed: Boolean = false

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
        pagePtr: Long, surface: Surface, startX: Int, startY: Int,
        sizeHor: Int, sizeVer: Int, annotation: Boolean
    )

    private external fun nativeRenderPageBitmap(
        pagePtr: Long, bitmap: Bitmap?, startX: Int, startY: Int,
        sizeHor: Int, sizeVer: Int, annotation: Boolean
    )

    private external fun nativeRenderPageBitmapWithMatrix(
        pagePtr: Long, bitmap: Bitmap?, matrix: FloatArray, clipRect: RectF, annotation: Boolean
    )

    private external fun nativeOpenDocument(parcelFileDescriptor: Int, password: String?): Long
    private external fun nativeOpenMemDocument(data: ByteArray, password: String?): Long

    /**
     * Create new document from file
     * @param parcelFileDescriptor opened file descriptor of file
     * @return PdfDocument
     */
    @Throws(IOException::class)
    fun newDocument(parcelFileDescriptor: ParcelFileDescriptor): PdfDocument {
        return newDocument(parcelFileDescriptor = parcelFileDescriptor, password = null)
    }

    /**
     * Create new document from file with password
     * @param parcelFileDescriptor opened file descriptor of file
     * @param password password for decryption
     * @return PdfDocument
     */
    @Throws(IOException::class)
    fun newDocument(parcelFileDescriptor: ParcelFileDescriptor, password: String?): PdfDocument {
        document.parcelFileDescriptor = parcelFileDescriptor
        synchronized(lock) {
            document.nativeDocPtr = nativeOpenDocument(
                parcelFileDescriptor = parcelFileDescriptor.fd, password = password
            )
        }
        return document
    }

    /**
     * Create new document from bytearray
     * @param data bytearray of pdf file
     * @return PdfDocument
     */
    @Throws(IOException::class)
    fun newDocument(data: ByteArray): PdfDocument {
        return newDocument(data = data, password = null)
    }

    /**
     * Create new document from bytearray with password
     * @param data bytearray of pdf file
     * @param password password for decryption
     * @return PdfDocument
     */
    @Throws(IOException::class)
    fun newDocument(data: ByteArray, password: String?): PdfDocument {
        synchronized(lock = lock) {
            synchronized(lock) {
                document.nativeDocPtr = nativeOpenMemDocument(data = data, password = password)
            }
            return document
        }
    }

    /**
     * Open a text page
     * @return the opened [PdfTextPage]
     * @throws IllegalArgumentException if document is closed or the page cannot be loaded
     */
    fun openTextPage(pageIndex: Int): PdfTextPage = document.openTextPage(pageIndex = pageIndex)

    /**
     * Get page height in pixels.
     * @param pageIndex the page index
     * @return page height in pixels
     * @throws IllegalStateException If the page or document is closed
     */
    fun getPageHeight(pageIndex: Int): Int {
        check(value = !isClosed && !document.isClosed) { "Already closed" }
        synchronized(lock = lock) {
            return nativeGetPageHeightPixel(pagePtr = pagePtr(index = pageIndex), dpi = screenDpi)
        }
    }

    /**
     * Get page width in pixels.
     * @param pageIndex the page index
     * @return page width in pixels
     * @throws IllegalStateException If the page or document is closed
     */
    fun getPageWidth(pageIndex: Int): Int {
        check(value = !isClosed && !document.isClosed) { "Already closed" }
        synchronized(lock = lock) {
            return nativeGetPageWidthPixel(pagePtr = pagePtr(index = pageIndex), dpi = screenDpi)
        }
    }

    /**
     * Get page height in PostScript points (1/72th of an inch)
     * @param pageIndex the page index
     * @return page height in points
     * @throws IllegalStateException If the page or document is closed
     */
    fun getPageHeightPoint(pageIndex: Int): Int {
        check(value = !isClosed && !document.isClosed) { "Already closed" }
        synchronized(lock = lock) {
            return nativeGetPageHeightPoint(pagePtr = pagePtr(index = pageIndex))
        }
    }

    /**
     * Get page width in PostScript points (1/72th of an inch).
     * @param pageIndex the page index
     * @return page width in points
     * @throws IllegalStateException If the page or document is closed
     */
    fun getPageWidthPoint(pageIndex: Int): Int {
        check(value = !isClosed && !document.isClosed) { "Already closed" }
        synchronized(lock = lock) {
            return nativeGetPageWidthPoint(pagePtr = pagePtr(index = pageIndex))
        }
    }

    /**
     *  Get the page's size in pixels
     *  @param pageIndex the page index
     *  @return page size in pixels
     *  @throws IllegalStateException If the page or document is closed
     */
    fun getPageSize(pageIndex: Int): Size {
        check(value = !isClosed && !document.isClosed) { "Already closed" }
        synchronized(lock = lock) {
            return nativeGetPageSizeByIndex(
                docPtr = document.nativeDocPtr, pageIndex = pageIndex, dpi = screenDpi
            )
        }
    }

    /**
     * Get page rotation in degrees
     * @param pageIndex the page index
     * @return page rotation
     */
    fun getPageRotation(pageIndex: Int): Int {
        check(value = !isClosed && !document.isClosed) { "Already closed" }
        return nativeGetPageRotation(pagePtr = pagePtr(index = pageIndex))
    }

    /**
     * Render page fragment on [Surface].<br></br>
     * @param pageIndex the page index
     * @param surface Surface on which to render page
     * @param startX left position of the page in the surface
     * @param startY top position of the page in the surface
     * @param sizeX horizontal size of the page on the surface
     * @param sizeY vertical size of the page on the surface
     * @param annotation whether render annotation
     * @throws IllegalStateException If the page or document is closed
     */
    fun renderPage(
        pageIndex: Int, surface: Surface, startX: Int, startY: Int,
        sizeX: Int, sizeY: Int, annotation: Boolean
    ) {
        check(value = !isClosed && !document.isClosed) { "Already closed" }
        synchronized(lock = lock) {
            try {
                nativeRenderPage(
                    pagePtr = pagePtr(index = pageIndex), surface = surface, startX = startX,
                    startY = startY, sizeHor = sizeX, sizeVer = sizeY, annotation = annotation
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
     * @param pageIndex the page index
     * @param bitmap Bitmap on which to render page
     * @param startX left position of the page in the bitmap
     * @param startY top position of the page in the bitmap
     * @param sizeX horizontal size of the page on the bitmap
     * @param sizeY vertical size of the page on the bitmap
     * @param annotation whether render annotation
     * @throws IllegalStateException If the page or document is closed
     *
     * Supported bitmap configurations:
     *
     *  * ARGB_8888 - best quality, high memory usage, higher possibility of OutOfMemoryError
     *  * RGB_565 - little worse quality, 1/2 the memory usage
     */
    fun renderPageBitmap(
        pageIndex: Int, bitmap: Bitmap?, startX: Int, startY: Int,
        sizeX: Int, sizeY: Int, annotation: Boolean
    ) {
        check(value = !isClosed && !document.isClosed) { "Already closed" }
        synchronized(lock = lock) {
            nativeRenderPageBitmap(
                pagePtr = pagePtr(index = pageIndex), bitmap = bitmap, startX = startX,
                startY = startY, sizeHor = sizeX, sizeVer = sizeY, annotation = annotation
            )
        }
    }

    /**
     * Render page fragment on [Bitmap] with no annotation.<br></br>
     * @param pageIndex the page index
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
        pageIndex: Int, bitmap: Bitmap?, startX: Int, startY: Int, drawSizeX: Int, drawSizeY: Int
    ) {
        check(value = !isClosed && !document.isClosed) { "Already closed" }
        synchronized(lock = lock) {
            renderPageBitmap(
                pageIndex = pageIndex, bitmap = bitmap, startX = startX, startY = startY,
                sizeX = drawSizeX, sizeY = drawSizeY, annotation = false
            )
        }
    }

    fun renderPageBitmap(
        pageIndex: Int, bitmap: Bitmap?, matrix: Matrix, clipRect: RectF, annotation: Boolean
    ) {
        check(value = !isClosed && !document.isClosed) { "Already closed" }
        val matrixValues = FloatArray(size = THREE_BY_THREE)
        matrix.getValues(matrixValues)
        synchronized(lock = lock) {
            nativeRenderPageBitmapWithMatrix(
                pagePtr = pagePtr(index = pageIndex), bitmap = bitmap, matrix = floatArrayOf(
                    matrixValues[Matrix.MSCALE_X], matrixValues[Matrix.MSCALE_Y],
                    matrixValues[Matrix.MTRANS_X], matrixValues[Matrix.MTRANS_Y]
                ), clipRect = clipRect, annotation = annotation
            )
        }
    }

    /**
     * Get all links from given page
     * @param pageIndex the page index
     * @return list of [PdfDocument.Link]
     */
    fun getPageLinks(
        pageIndex: Int,
        size: SizeF,
        posX: Float,
        posY: Float
    ): List<PdfDocument.Link> {
        check(value = !isClosed && !document.isClosed) { "Already closed" }
        synchronized(lock = lock) {
            val pagePtr: Long = pagePtr(index = pageIndex)
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
     * @param pageIndex the page index
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
        pageIndex: Int, startX: Int, startY: Int, sizeX: Int,
        sizeY: Int, rotate: Int, pageX: Double, pageY: Double
    ): Point {
        check(value = !isClosed && !document.isClosed) { "Already closed" }
        return nativePageCoordsToDevice(
            pagePtr = pagePtr(index = pageIndex), startX = startX, startY = startY,
            sizeX = sizeX, sizeY = sizeY, rotate = rotate, pageX = pageX, pageY = pageY
        )
    }

    /**
     * Maps a rectangle from page space to device space
     * @param pageIndex the page index
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
        pageIndex: Int, startX: Int, startY: Int, sizeX: Int, sizeY: Int, rotate: Int, coords: RectF
    ): RectF {
        check(value = !isClosed && !document.isClosed) { "Already closed" }
        val leftTop = mapPageCoordsToDevice(
            pageIndex = pageIndex, startX = startX, startY = startY, sizeX = sizeX, sizeY = sizeY,
            rotate = rotate, pageX = coords.left.toDouble(), pageY = coords.top.toDouble()
        )
        val rightBottom = mapPageCoordsToDevice(
            pageIndex = pageIndex, startX = startX, startY = startY, sizeX = sizeX, sizeY = sizeY,
            rotate = rotate, pageX = coords.right.toDouble(), pageY = coords.bottom.toDouble()
        )
        return RectF(
            leftTop.x.toFloat(), leftTop.y.toFloat(),
            rightBottom.x.toFloat(), rightBottom.y.toFloat()
        )
    }

    /**
     * Map device screen coordinates to page coordinates
     * @param pageIndex the page index
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
        pageIndex: Int, startX: Int, startY: Int, sizeX: Int,
        sizeY: Int, rotate: Int, deviceX: Int, deviceY: Int
    ): PointF {
        check(value = !isClosed && !document.isClosed) { "Already closed" }
        return nativeDeviceCoordsToPage(
            pagePtr = pagePtr(index = pageIndex), startX = startX, startY = startY,
            sizeX = sizeX, sizeY = sizeY, rotate = rotate, deviceX = deviceX, deviceY = deviceY
        )
    }

    /**
     *  Get the page's art box in PostScript points (1/72th of an inch)
     *  @param pageIndex the page index
     *  @return page art box in points or RectF(-1, -1, -1, -1) if not present
     *  @throws IllegalStateException If the page or document is closed
     */
    fun getPageArtBox(pageIndex: Int): RectF {
        check(value = !isClosed && !document.isClosed) { "Already closed" }
        synchronized(lock = lock) {
            val bound = nativeGetPageArtBox(pagePtr = pagePtr(index = pageIndex))
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
     *  @param pageIndex the page index
     *  @return page bleed box in points or RectF(-1, -1, -1, -1) if not present
     *  @throws IllegalStateException If the page or document is closed
     */
    fun getPageBleedBox(pageIndex: Int): RectF {
        check(value = !isClosed && !document.isClosed) { "Already closed" }
        synchronized(lock = lock) {
            val bound = nativeGetPageBleedBox(pagePtr = pagePtr(index = pageIndex))
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
     *  @param pageIndex the page index
     *  @return page bounding box in points or RectF(-1, -1, -1, -1) if not present
     *  @throws IllegalStateException If the page or document is closed
     */
    fun getPageBoundingBox(pageIndex: Int): RectF {
        check(value = !isClosed && !document.isClosed) { "Already closed" }
        synchronized(lock = lock) {
            val bound = nativeGetPageBoundingBox(pagePtr = pagePtr(index = pageIndex))
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
     *  @param pageIndex the page index
     *  @return page crop box in points or RectF(-1, -1, -1, -1) if not present
     *  @throws IllegalStateException If the page or document is closed
     */
    fun getPageCropBox(pageIndex: Int): RectF {
        check(value = !isClosed && !document.isClosed) { "Already closed" }
        synchronized(lock = lock) {
            val bound = nativeGetPageCropBox(pagePtr = pagePtr(index = pageIndex))
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
     *  @param pageIndex the page index
     *  @return page media box in points or RectF(-1, -1, -1, -1) if not present
     *  @throws IllegalStateException If the page or document is closed
     */
    fun getPageMediaBox(pageIndex: Int): RectF {
        check(value = !isClosed && !document.isClosed) { "Already closed" }
        synchronized(lock = lock) {
            val bound = nativeGetPageMediaBox(pagePtr = pagePtr(index = pageIndex))
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
     *  @param pageIndex the page index
     *  @return page trim box in points or RectF(-1, -1, -1, -1) if not present
     *  @throws IllegalStateException If the page or document is closed
     */
    fun getPageTrimBox(pageIndex: Int): RectF {
        check(value = !isClosed && !document.isClosed) { "Already closed" }
        synchronized(lock = lock) {
            val bound = nativeGetPageTrimBox(pagePtr = pagePtr(index = pageIndex))
            return RectF().apply {
                left = bound[LEFT]
                top = bound[TOP]
                right = bound[RIGHT]
                bottom = bound[BOTTOM]
            }
        }
    }

    private fun pagePtr(index: Int): Long {
        return document.pageMap[index]?.pagePtr ?: -1
    }

    /**
     * Close the page and release all resources
     */
    override fun close() {
        if (isClosed) return
        synchronized(lock = lock) {
            isClosed = true
            for (index in document.pageMap.keys) {
                document.pageMap[index]?.let {
                    if (it.count > 1) {
                        it.count--
                        return
                    }
                }
                document.pageMap.remove(key = index)
                document.pageMap[index]?.let { nativeClosePage(pagePtr = it.pagePtr) }
            }
            document.pageMap.clear()
            document.close()
        }
    }

    companion object {
        const val LEFT = 0
        const val TOP = 1
        const val RIGHT = 2
        const val BOTTOM = 3
        private const val THREE_BY_THREE = 9
        private val TAG: String = PdfiumCore::class.java.name
        val lock: Any = Any()
        val screenDpi: Int by lazy { Resources.getSystem().displayMetrics.densityDpi }

        init {
            Log.v(TAG, "Starting AhmerPdfium...")
            try {
                System.loadLibrary("pdfsdk")
                System.loadLibrary("pdfsdk_jni")
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