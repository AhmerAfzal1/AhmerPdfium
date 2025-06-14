package com.ahmer.pdfium

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Point
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.os.ParcelFileDescriptor
import android.util.Log
import android.view.Surface
import com.ahmer.pdfium.util.Size
import com.ahmer.pdfium.util.SizeF
import dalvik.annotation.optimization.FastNative
import java.io.Closeable
import java.io.IOException

/**
 * Core PDF processing class handling document operations, rendering, and coordinate transformations.
 *
 * @property context Android context for resource access
 */
class PdfiumCore(
    val context: Context
) : Closeable {
    private val doc: PdfDocument = PdfDocument()
    private var currentDpi: Int = 0

    init {
        currentDpi = context.resources?.displayMetrics?.densityDpi ?: -1
        Log.d(TAG, "Starting AhmerPdfium...")
    }

    private fun pagePtr(index: Int): Long {
        return doc.pageCache[index]?.pagePtr ?: -1
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
    fun newDocument(parcelFileDescriptor: ParcelFileDescriptor, password: String? = null): PdfDocument {
        doc.fileDescriptor = parcelFileDescriptor
        synchronized(lock = lock) {
            doc.nativePtr = nativeOpenDocument(parcelFileDescriptor = parcelFileDescriptor.fd, password = password)
        }
        return doc
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
    fun newDocument(data: ByteArray, password: String? = null): PdfDocument {
        synchronized(lock = lock) {
            doc.nativePtr = nativeOpenMemDocument(data = data, password = password)
        }
        return doc
    }

    /**
     * Opens a text page for text extraction and operations
     *
     * @param pageIndex Index of the page to open
     * @return [PdfTextPage] instance for the specified page
     * @throws IllegalStateException If the document is closed
     */
    fun openTextPage(pageIndex: Int): PdfTextPage = doc.openTextPage(pageIndex = pageIndex)

    /**
     * Gets page width in pixels at current DPI
     *
     * @param pageIndex Index of the page
     * @return Page width in pixels or -1 if closed
     */
    fun getPageWidthPixel(pageIndex: Int): Int {
        synchronized(lock = lock) {
            return nativeGetPageWidthPixel(pagePtr = pagePtr(index = pageIndex), dpi = currentDpi)
        }
    }

    /**
     * Gets page height in pixels at current DPI
     *
     * @param pageIndex Index of the page
     * @return Page height in pixels or -1 if closed
     */
    fun getPageHeightPixel(pageIndex: Int): Int {
        synchronized(lock = lock) {
            return nativeGetPageHeightPixel(pagePtr = pagePtr(index = pageIndex), dpi = currentDpi)
        }
    }

    /**
     * Gets page width in PDF points (1/72 inch)
     *
     * @param pageIndex Index of the page
     * @return Page width in points or -1 if closed
     */
    fun getPageWidthPoint(pageIndex: Int): Int {
        synchronized(lock = lock) {
            return nativeGetPageWidthPoint(pagePtr = pagePtr(index = pageIndex))
        }
    }

    /**
     * Gets page height in pixels at current DPI
     *
     * @param pageIndex Index of the page
     * @return Page height in pixels or -1 if closed
     */
    fun getPageHeightPoint(pageIndex: Int): Int {
        synchronized(lock = lock) {
            return nativeGetPageHeightPoint(pagePtr = pagePtr(index = pageIndex))
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
    fun pageRotation(pageIndex: Int): Int {
        synchronized(lock = lock) {
            return nativeGetPageRotation(pagePtr = pagePtr(index = pageIndex))
        }
    }

    /**
     * Gets page size in pixels.
     *
     * @param pageIndex Index of the page.
     * @return [Size] representing page dimensions.
     * @throws IllegalStateException If document is closed.
     */
    fun getPageSize(pageIndex: Int): Size {
        synchronized(lock = lock) {
            return nativeGetPageSizeByIndex(docPtr = doc.nativePtr, pageIndex = pageIndex, dpi = currentDpi)
        }
    }

    fun renderPage(
        surface: Surface,
        pageIndex: Int,
        startX: Int,
        startY: Int,
        drawSizeX: Int,
        drawSizeY: Int,
        annotation: Boolean = false,
    ) {
        synchronized(lock) {
            try {
                nativeRenderPage(
                    pagePtr = pagePtr(index = pageIndex),
                    surface = surface,
                    startX = startX,
                    startY = startY,
                    drawSizeHor = drawSizeX,
                    drawSizeVer = drawSizeY,
                    annotation = annotation
                )
            } catch (e: NullPointerException) {
                Log.e(TAG, "mContext may be null")
                e.printStackTrace()
            } catch (e: Exception) {
                Log.e(TAG, "Exception throw from native")
                e.printStackTrace()
            }
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
     */
    fun renderPageBitmap(
        pageIndex: Int,
        bitmap: Bitmap?,
        startX: Int,
        startY: Int,
        drawSizeX: Int,
        drawSizeY: Int,
        annotation: Boolean = false,
    ) {
        synchronized(lock = lock) {
            return nativeRenderPageBitmap(
                docPtr = doc.nativePtr,
                pagePtr = pagePtr(index = pageIndex),
                bitmap = bitmap,
                startX = startX,
                startY = startY,
                drawSizeHor = drawSizeX,
                drawSizeVer = drawSizeY,
                annotation = annotation,
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
    fun getPageLinks(pageIndex: Int, size: SizeF, posX: Float, posY: Float): List<PdfDocument.Link> {
        synchronized(lock = lock) {
            val pagePtr: Long = pagePtr(index = pageIndex)
            return nativeGetPageLinks(pagePtr = pagePtr)
                .toList()
                .mapNotNull { linkPtr ->
                    try {
                        val rect: RectF = nativeGetLinkRect(linkPtr = linkPtr)
                        val index: Int = nativeGetDestPageIndex(docPtr = doc.nativePtr, linkPtr = linkPtr)
                        val uri: String? =
                            nativeGetLinkURI(docPtr = doc.nativePtr, linkPtr = linkPtr) ?: nativeGetLinkURI(
                                docPtr = doc.nativePtr,
                                linkPtr = nativeGetLinkAtCoord(
                                    pagePtr = pagePtr,
                                    width = size.width.toInt(),
                                    height = size.height.toInt(),
                                    posX = posX.toInt(),
                                    posY = posY.toInt()
                                )
                            )
                        PdfDocument.Link(bounds = rect, destPage = index, uri = uri)
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
    fun mapRectToDevice(
        pageIndex: Int,
        startX: Int,
        startY: Int,
        sizeX: Int,
        sizeY: Int,
        rotate: Int,
        coords: RectF,
    ): RectF {
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
    fun mapRectToPage(
        pageIndex: Int,
        startX: Int,
        startY: Int,
        sizeX: Int,
        sizeY: Int,
        rotate: Int,
        coords: Rect,
    ): RectF {
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
        Log.v(TAG, "Closing PdfDocument")
        doc.pageCache.keys.forEach { index ->
            doc.pageCache[index]?.let { page ->
                if (page.count > 1) {
                    page.count--
                    return
                }
                doc.pageCache.remove(key = index)
                doc.pageCache[index]?.let {
                    nativeClosePage(pagePtr = it.pagePtr)
                }
            }
        }
        doc.close()
    }

    companion object {
        val lock: Any = Any()
        val TAG: String = PdfiumCore::class.java.name

        @JvmStatic
        private external fun nativeClosePage(pagePtr: Long)

        @JvmStatic
        private external fun nativeClosePages(pagesPtr: LongArray)

        @JvmStatic
        @FastNative
        private external fun nativeDeviceCoordsToPage(
            pagePtr: Long, startX: Int, startY: Int, sizeX: Int, sizeY: Int, rotate: Int, deviceX: Int, deviceY: Int,
        ): PointF

        @JvmStatic
        private external fun nativeGetDestPageIndex(docPtr: Long, linkPtr: Long): Int

        @JvmStatic
        private external fun nativeGetLinkAtCoord(pagePtr: Long, width: Int, height: Int, posX: Int, posY: Int): Long

        @JvmStatic
        private external fun nativeGetLinkRect(linkPtr: Long): RectF

        @JvmStatic
        private external fun nativeGetLinkURI(docPtr: Long, linkPtr: Long): String?

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
        private external fun nativeGetPageRotation(pagePtr: Long): Int

        @JvmStatic
        private external fun nativeGetPageSizeByIndex(docPtr: Long, pageIndex: Int, dpi: Int): Size

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
            pagePtr: Long, startX: Int, startY: Int, sizeX: Int, sizeY: Int, rotate: Int, pageX: Double, pageY: Double,
        ): Point

        @JvmStatic
        private external fun nativeRenderPageBitmap(
            docPtr: Long, pagePtr: Long, bitmap: Bitmap?, startX: Int, startY: Int,
            drawSizeHor: Int, drawSizeVer: Int, annotation: Boolean
        )

        @JvmStatic
        private external fun nativeRenderPage(
            pagePtr: Long, surface: Surface, startX: Int, startY: Int,
            drawSizeHor: Int, drawSizeVer: Int, annotation: Boolean
        )

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