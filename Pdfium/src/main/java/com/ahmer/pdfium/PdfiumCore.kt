package com.ahmer.pdfium

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Point
import android.graphics.PointF
import android.graphics.RectF
import android.os.ParcelFileDescriptor
import android.util.Log
import android.view.Surface
import com.ahmer.pdfium.search.FPDFTextSearchContext
import com.ahmer.pdfium.search.TextSearchContext
import com.ahmer.pdfium.util.Size
import java.io.FileDescriptor
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

class PdfiumCore(context: Context) {
    companion object {
        private var mCurrentDpi = 0
        private val mNativePagesPtr = HashMap<Int, Long>()
        private val mNativeTextPagesPtr = HashMap<Int, Long>()
        private val mNativeSearchHandlePtr = HashMap<Int, Long>()
        private var mNativeDocPtr: Long = 0
        private var mFileDescriptor: ParcelFileDescriptor? = null
        private val TAG = PdfiumCore::class.java.name

        private fun getNumFd(fdObj: ParcelFileDescriptor): Int {
            return try {
                val fdField = FileDescriptor::class.java.getDeclaredField("descriptor")
                fdField.isAccessible = true
                fdField.getInt(fdObj.fileDescriptor)
            } catch (e: ReflectiveOperationException) {
                e.printStackTrace()
                -1
            }
        }

        private fun validPtr(ptr: Long?): Boolean {
            return ptr != null && ptr != -1L
        }

        init {
            System.loadLibrary("pdfsdk")
            System.loadLibrary("pdfsdk_jni")
        }
    }

    /**
     * Context needed to get screen density
     */
    init {
        mCurrentDpi = context.resources.displayMetrics.densityDpi
        Log.d(TAG, "Starting AhmerPdfium...")
    }

    private external fun nativeAddTextAnnotation(docPtr: Long, pageIndex: Int, text: String, color: IntArray, bound: IntArray): Long
    private external fun nativeCloseDocument(docPtr: Long)
    private external fun nativeClosePage(pagePtr: Long)
    private external fun nativeClosePages(pagesPtr: LongArray)
    private external fun nativeCloseTextPage(pagePtr: Long)
    private external fun nativeCloseTextPages(pagesPtr: LongArray)
    private external fun nativeDeviceCoordinateToPage(pagePtr: Long, startX: Int, startY: Int, sizeX: Int, sizeY: Int, rotate: Int, deviceX: Int, deviceY: Int): PointF
    private external fun nativeGetBookmarkDestIndex(docPtr: Long, bookmarkPtr: Long): Long
    private external fun nativeGetBookmarkTitle(bookmarkPtr: Long): String?
    private external fun nativeGetDestPageIndex(docPtr: Long, linkPtr: Long): Int?
    private external fun nativeGetDocumentMetaText(docPtr: Long, tag: String): String?
    private external fun nativeGetFirstChildBookmark(docPtr: Long, bookmarkPtr: Long?): Long?
    private external fun nativeGetLinkRect(linkPtr: Long): RectF?
    private external fun nativeGetLinkURI(docPtr: Long, linkPtr: Long): String?
    private external fun nativeGetPageCount(docPtr: Long): Int
    private external fun nativeGetPageHeightPixel(pagePtr: Long, dpi: Int): Int
    private external fun nativeGetPageHeightPoint(pagePtr: Long): Int
    private external fun nativeGetPageLinks(pagePtr: Long): LongArray
    private external fun nativeGetPageRotation(pagePtr: Long): Int
    private external fun nativeGetPageSizeByIndex(docPtr: Long, pageIndex: Int, dpi: Int): Size
    private external fun nativeGetPageWidthPixel(pagePtr: Long, dpi: Int): Int
    private external fun nativeGetPageWidthPoint(pagePtr: Long): Int
    private external fun nativeGetSiblingBookmark(docPtr: Long, bookmarkPtr: Long): Long?
    private external fun nativeLoadPage(docPtr: Long, pageIndex: Int): Long
    private external fun nativeLoadPages(docPtr: Long, fromIndex: Int, toIndex: Int): LongArray
    private external fun nativeLoadTextPage(docPtr: Long, pageIndex: Int): Long
    private external fun nativeLoadTextPages(docPtr: Long, fromIndex: Int, toIndex: Int): LongArray
    private external fun nativeOpenDocument(fd: Int, password: String?): Long
    private external fun nativeOpenMemDocument(data: ByteArray, password: String?): Long
    private external fun nativePageCoordinateToDevice(pagePtr: Long, startX: Int, startY: Int, sizeX: Int, sizeY: Int, rotate: Int, pageX: Double, pageY: Double): Point
    private external fun nativeRenderPage(pagePtr: Long, surface: Surface, dpi: Int, startX: Int, startY: Int, drawSizeHor: Int, drawSizeVer: Int, renderAnnot: Boolean)
    private external fun nativeRenderPageBitmap(pagePtr: Long, bitmap: Bitmap, dpi: Int, startX: Int, startY: Int, drawSizeHor: Int, drawSizeVer: Int, renderAnnot: Boolean)
    private external fun nativeTextCountChars(textPagePtr: Long): Int
    private external fun nativeTextCountRects(textPagePtr: Long, start_index: Int, count: Int): Int
    private external fun nativeTextGetBoundedText(textPagePtr: Long, left: Double, top: Double, right: Double, bottom: Double, arr: ShortArray): Int
    private external fun nativeTextGetBoundedTextLength(textPagePtr: Long, left: Double, top: Double, right: Double, bottom: Double): Int
    private external fun nativeTextGetCharBox(textPagePtr: Long, index: Int): DoubleArray
    private external fun nativeTextGetCharIndexAtPos(textPagePtr: Long, x: Double, y: Double, xTolerance: Double, yTolerance: Double): Int
    private external fun nativeTextGetRect(textPagePtr: Long, rect_index: Int): DoubleArray
    private external fun nativeTextGetText(textPagePtr: Long, start_index: Int, count: Int, result: ShortArray): Int
    private external fun nativeTextGetUnicode(textPagePtr: Long, index: Int): Int

    /**
     * API PDF Search
     */
    private external fun nativeCountSearchResult(searchHandlePtr: Long): Int
    private external fun nativeGetCharIndexOfSearchResult(searchHandlePtr: Long): Int
    private external fun nativeSearchNext(searchHandlePtr: Long): Boolean
    private external fun nativeSearchPrev(searchHandlePtr: Long): Boolean
    private external fun nativeSearchStart(textPagePtr: Long, query: String, matchCase: Boolean, matchWholeWord: Boolean): Long
    private external fun nativeSearchStop(searchHandlePtr: Long)

    /**
     * PDF Native Callbacks
     */
    private fun onAnnotationAdded(pageIndex: Int, pageNewPtr: Long) {}
    private fun onAnnotationRemoved(pageIndex: Int, pageNewPtr: Long) {}
    private fun onAnnotationUpdated(pageIndex: Int, pageNewPtr: Long) {}

    /**
     * Create new document from file with password
     * Create new document from file
     */
    @JvmOverloads
    @Throws(IOException::class)
    fun newDocument(parcelFileDescriptor: ParcelFileDescriptor, password: String? = null) {
        mFileDescriptor = parcelFileDescriptor
        mNativeDocPtr = nativeOpenDocument(getNumFd(parcelFileDescriptor), password)

    }

    /**
     * Create new document from byteArray with password
     * Create new document from byteArray
     */
    @JvmOverloads
    @Throws(IOException::class)
    fun newDocument(data: ByteArray, password: String? = null) {
        mNativeDocPtr = nativeOpenMemDocument(data, password)
    }

    /**
     * Get total number of pages in document
     */
    fun pageCount(): Int {
        return nativeGetPageCount(mNativeDocPtr)
    }

    /**
     * Open page
     */
    fun openPage(pageIndex: Int): Long {
        val pagePtr: Long = nativeLoadPage(mNativeDocPtr, pageIndex)
        mNativePagesPtr[pageIndex] = pagePtr
        prepareTextInfo(pageIndex)
        return pagePtr

    }

    /**
     * Open range of pages
     */
    fun openPage(fromIndex: Int, toIndex: Int): LongArray {
        val pagesPtr: LongArray = nativeLoadPages(mNativeDocPtr, fromIndex, toIndex)
        var pageIndex = fromIndex
        for (page in pagesPtr) {
            if (pageIndex > toIndex) {
                break
            }
            mNativePagesPtr[pageIndex] = page
            pageIndex++
        }
        return pagesPtr

    }

    /**
     * Close page
     */
    fun closePage(pageIndex: Int) {
        val pagePtr = mNativePagesPtr[pageIndex]
        if (pagePtr != null) {
            nativeClosePage(pagePtr)
            mNativePagesPtr.remove(pageIndex)
        }
    }

    /**
     * Get page width in pixels.
     * This method requires page to be opened.
     */
    fun getPageWidth(pageIndex: Int): Int {
        val pagePtr = mNativePagesPtr[pageIndex]
        return if (pagePtr != null) {
            nativeGetPageWidthPixel(pagePtr, mCurrentDpi);
        } else {
            0
        }
    }

    /**
     * Get page height in pixels.
     * This method requires page to be opened.
     */
    fun getPageHeight(pageIndex: Int): Int {
        val pagePtr = mNativePagesPtr[pageIndex]
        return if (pagePtr != null) {
            nativeGetPageHeightPixel(pagePtr, mCurrentDpi);
        } else {
            0
        }

    }

    /**
     * Get page width in PostScript points (1/72th of an inch).
     * This method requires page to be opened.
     */
    fun getPageWidthPoint(pageIndex: Int): Int {
        val pagePtr = mNativePagesPtr[pageIndex]
        return if (pagePtr != null) {
            nativeGetPageWidthPoint(pagePtr);
        } else {
            0
        }
    }

    /**
     * Get page height in PostScript points (1/72th of an inch).
     * This method requires page to be opened.
     */
    fun getPageHeightPoint(pageIndex: Int): Int {
        val pagePtr = mNativePagesPtr[pageIndex]
        return if (pagePtr != null) {
            nativeGetPageHeightPoint(pagePtr);
        } else {
            0
        }
    }

    /**
     * Get size of page in pixels.
     * This method does not require given page to be opened.
     */
    fun getPageSize(pageIndex: Int): Size {
        return nativeGetPageSizeByIndex(mNativeDocPtr, pageIndex, mCurrentDpi)
    }

    /**
     * Render page fragment on [Surface]. This method allows to render annotations.
     * Page must be opened before rendering.
     */
    /**
     * Render page fragment on [Surface].
     * Page must be opened before rendering.
     */
    @JvmOverloads
    fun renderPage(
        surface: Surface,
        pageIndex: Int,
        startX: Int,
        startY: Int,
        drawSizeX: Int,
        drawSizeY: Int,
        renderAnnot: Boolean = false
    ) {
        try {
            nativeRenderPage(
                mNativePagesPtr[pageIndex]!!,
                surface,
                mCurrentDpi,
                startX,
                startY,
                drawSizeX,
                drawSizeY,
                renderAnnot
            )
        } catch (e: NullPointerException) {
            Log.e(TAG, "Context may be null")
            e.printStackTrace()
        } catch (e: Exception) {
            Log.e(TAG, "Exception throw from native")
            e.printStackTrace()
        }

    }

    /**
     * Render page fragment on [Bitmap]. This method allows to render annotations.
     * Page must be opened before rendering.
     * For more info see {PdfiumCore#renderPageBitmap(Bitmap, int, int, int, int, int, boolean)}
     * Render page fragment on [Bitmap].
     * Page must be opened before rendering.
     * Supported bitmap configurations:
     * ARGB_8888 - best quality, high memory usage, higher possibility of OutOfMemoryError
     * RGB_565 - little worse quality, twice less memory usage
     */
    @JvmOverloads
    fun renderPageBitmap(
        bitmap: Bitmap,
        pageIndex: Int,
        startX: Int,
        startY: Int,
        drawSizeX: Int,
        drawSizeY: Int,
        renderAnnot: Boolean = false
    ) {
        try {
            nativeRenderPageBitmap(
                mNativePagesPtr[pageIndex]!!,
                bitmap,
                mCurrentDpi,
                startX,
                startY,
                drawSizeX,
                drawSizeY,
                renderAnnot
            )
        } catch (e: NullPointerException) {
            Log.e(TAG, "Context may be null")
            e.printStackTrace()
        } catch (e: Exception) {
            Log.e(TAG, "Exception throw from native")
            e.printStackTrace()
        }
    }

    /**
     * Release native resources and opened file
     */
    fun closeDocument() {
        for (index in mNativePagesPtr.keys) {
            mNativePagesPtr[index]?.let { nativeClosePage(it) }
        }
        mNativePagesPtr.clear()
        nativeCloseDocument(mNativeDocPtr)
        if (mFileDescriptor != null) { //if document was loaded from file
            try {
                mFileDescriptor!!.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
            mFileDescriptor = null
        }

    }

    /**
     * Get metadata for given document
     */
    val documentMeta: Meta
        get() {
            val meta = Meta()
            meta.title = nativeGetDocumentMetaText(mNativeDocPtr, "Title")
            meta.author = nativeGetDocumentMetaText(mNativeDocPtr, "Author")
            meta.subject = nativeGetDocumentMetaText(mNativeDocPtr, "Subject")
            meta.keywords = nativeGetDocumentMetaText(mNativeDocPtr, "Keywords")
            meta.creator = nativeGetDocumentMetaText(mNativeDocPtr, "Creator")
            meta.producer = nativeGetDocumentMetaText(mNativeDocPtr, "Producer")
            meta.creationDate = nativeGetDocumentMetaText(mNativeDocPtr, "CreationDate")
            meta.modDate = nativeGetDocumentMetaText(mNativeDocPtr, "ModDate")
            meta.totalPages = pageCount()
            return meta
        }

    /**
     * Get table of contents (bookmarks) for given document
     */
    fun tableOfContents(): List<Bookmark> {
        val topLevel: List<Bookmark> = ArrayList()
        val first = nativeGetFirstChildBookmark(mNativeDocPtr, null)
        first?.let { recursiveGetBookmark(topLevel as ArrayList<Bookmark>, it) }
        return topLevel
    }

    private fun recursiveGetBookmark(tree: ArrayList<Bookmark>, bookmarkPtr: Long) {
        val bookmark = Bookmark()
        bookmark.mNativePtr = bookmarkPtr
        bookmark.title = nativeGetBookmarkTitle(bookmarkPtr)
        bookmark.pageIdx = nativeGetBookmarkDestIndex(mNativeDocPtr, bookmarkPtr)
        tree.add(bookmark)
        val child = nativeGetFirstChildBookmark(mNativeDocPtr, bookmarkPtr)
        if (child != null) {
            recursiveGetBookmark(bookmark.children as ArrayList<Bookmark>, child)
        }
        val sibling = nativeGetSiblingBookmark(mNativeDocPtr, bookmarkPtr)
        sibling?.let { recursiveGetBookmark(tree, it) }
    }

    /**
     * Get all links from given page
     */
    fun getPageLinks(pageIndex: Int): List<Link> {
        val links: MutableList<Link> =
            ArrayList()
        val nativePagePtr = mNativePagesPtr[pageIndex] ?: return links
        val linkPtrs = nativeGetPageLinks(nativePagePtr)
        for (linkPtr in linkPtrs) {
            val index = nativeGetDestPageIndex(mNativeDocPtr, linkPtr)
            val uri = nativeGetLinkURI(mNativeDocPtr, linkPtr)
            val rect = nativeGetLinkRect(linkPtr)
            if (rect != null && (index != null || uri != null)) {
                links.add(Link(rect, index!!, uri!!))
            }
        }
        return links
    }


    /**
     * Map page coordinates to device screen coordinates
     *
     * @param pageIndex index of page
     * @param startX    left pixel position of the display area in device coordinates
     * @param startY    top pixel position of the display area in device coordinates
     * @param sizeX     horizontal size (in pixels) for displaying the page
     * @param sizeY     vertical size (in pixels) for displaying the page
     * @param rotate    page orientation: 0 (normal), 1 (rotated 90 degrees clockwise),
     * 2 (rotated 180 degrees), 3 (rotated 90 degrees counter-clockwise)
     * @param pageX     X value in page coordinates
     * @param pageY     Y value in page coordinate
     * @return mapped coordinates
     */
    fun mapPageCoordsToDevice(
        pageIndex: Int,
        startX: Int,
        startY: Int,
        sizeX: Int,
        sizeY: Int,
        rotate: Int,
        pageX: Double,
        pageY: Double
    ): Point {
        val pagePtr: Long? = mNativePagesPtr[pageIndex]
        return nativePageCoordinateToDevice(
            pagePtr!!,
            startX,
            startY,
            sizeX,
            sizeY,
            rotate,
            pageX,
            pageY
        )
    }

    /**
     * Convert the screen coordinates of a point to page coordinates.
     *
     *
     * The page coordinate system has its origin at the left-bottom corner
     * of the page, with the X-axis on the bottom going to the right, and
     * the Y-axis on the left side going up.
     *
     *
     * NOTE: this coordinate system can be altered when you zoom, scroll,
     * or rotate a page, however, a point on the page should always have
     * the same coordinate values in the page coordinate system.
     *
     *
     * The device coordinate system is device dependent. For screen device,
     * its origin is at the left-top corner of the window. However this
     * origin can be altered by the Windows coordinate transformation
     * utilities.
     *
     *
     * You must make sure the start_x, start_y, size_x, size_y
     * and rotate parameters have exactly same values as you used in
     * the FPDF_RenderPage() function call.
     *
     * @param pageIndex index of page
     * @param startX    Left pixel position of the display area in device coordinates.
     * @param startY    Top pixel position of the display area in device coordinates.
     * @param sizeX     Horizontal size (in pixels) for displaying the page.
     * @param sizeY     Vertical size (in pixels) for displaying the page.
     * @param rotate    Page orientation:
     * 0 (normal)
     * 1 (rotated 90 degrees clockwise)
     * 2 (rotated 180 degrees)
     * 3 (rotated 90 degrees counter-clockwise)
     * @param deviceX   X value in device coordinates to be converted.
     * @param deviceY   Y value in device coordinates to be converted.
     * @return mapped coordinates
     */
    fun mapDeviceCoordsToPage(
        pageIndex: Int,
        startX: Int,
        startY: Int,
        sizeX: Int,
        sizeY: Int,
        rotate: Int,
        deviceX: Int,
        deviceY: Int
    ): PointF {
        val pagePtr = mNativePagesPtr[pageIndex]
        return nativeDeviceCoordinateToPage(
            pagePtr!!,
            startX,
            startY,
            sizeX,
            sizeY,
            rotate,
            deviceX,
            deviceY
        )
    }

    /**
     * @return mapped coordinates
     * @see PdfiumCore.mapPageCoordsToDevice
     */
    fun mapRectToDevice(
        pageIndex: Int, startX: Int, startY: Int, sizeX: Int, sizeY: Int,
        rotate: Int, coords: RectF
    ): RectF {
        val leftTop = mapPageCoordsToDevice(
            pageIndex, startX, startY, sizeX, sizeY, rotate,
            coords.left.toDouble(), coords.top.toDouble()
        )
        val rightBottom = mapPageCoordsToDevice(
            pageIndex, startX, startY, sizeX, sizeY,
            rotate, coords.right.toDouble(), coords.bottom.toDouble()
        )
        return RectF(
            leftTop.x.toFloat(), leftTop.y.toFloat(), rightBottom.x.toFloat(),
            rightBottom.y.toFloat()
        )
    }

    /**
     * @return mapped coordinates
     * @see PdfiumCore.mapDeviceCoordsToPage
     */
    fun mapRectToPage(
        pageIndex: Int, startX: Int, startY: Int, sizeX: Int,
        sizeY: Int, rotate: Int, coords: RectF
    ): RectF {
        val leftTop = mapDeviceCoordsToPage(
            pageIndex, startX, startY, sizeX, sizeY, rotate,
            coords.left.toInt(), coords.top.toInt()
        )
        val rightBottom = mapDeviceCoordsToPage(
            pageIndex, startX, startY, sizeX, sizeY, rotate,
            coords.right.toInt(), coords.bottom.toInt()
        )
        return RectF(leftTop.x, leftTop.y, rightBottom.x, rightBottom.y)
    }

    fun openTextPage(pageIndex: Int): Long {
        val page = openPage(pageIndex)
        var textPagePtr = mNativeTextPagesPtr[pageIndex]
        if (textPagePtr == null) {
            textPagePtr = nativeLoadTextPage(mNativeDocPtr, page.toInt())
            mNativeTextPagesPtr[pageIndex] = textPagePtr
        }
        return textPagePtr
    }

    fun closeTextPage(pageIndex: Int) {
        val nativeLoadTextPage = mNativeTextPagesPtr[pageIndex]
        if (nativeLoadTextPage != null) {
            nativeCloseTextPage(nativeLoadTextPage)
            mNativeTextPagesPtr.remove(pageIndex)
        }

    }

    fun openTextPage(fromIndex: Int, toIndex: Int): LongArray {
        val textPagesPtr: LongArray = nativeLoadPages(mNativeDocPtr, fromIndex, toIndex)
        var pageIndex = fromIndex
        for (page in textPagesPtr) {
            if (pageIndex > toIndex) {
                break
            }
            mNativeTextPagesPtr[pageIndex] = page
            pageIndex++
        }
        return textPagesPtr
    }

    fun textPageCountChars(textPageIndex: Int): Int {
        try {
            return nativeTextCountChars(mNativeTextPagesPtr[textPageIndex]!!)
        } catch (e: NullPointerException) {
            Log.e(TAG, "Context may be null")
            e.printStackTrace()
        } catch (e: Exception) {
            Log.e(TAG, "Exception throw from native")
            e.printStackTrace()
        }
        return -1
    }

    fun textPageGetText(textPageIndex: Int, startIndex: Int, length: Int): String? {
        try {
            val buf = ShortArray(length + 1)
            val r = nativeTextGetText(mNativeTextPagesPtr[textPageIndex]!!, startIndex, length, buf)
            val bytes = ByteArray((r - 1) * 2)
            val bb = ByteBuffer.wrap(bytes)
            bb.order(ByteOrder.LITTLE_ENDIAN)
            for (i in 0 until r - 1) {
                val s = buf[i]
                bb.putShort(s)
            }
            return String(bytes, StandardCharsets.UTF_16LE)
        } catch (e: NullPointerException) {
            Log.e(TAG, "Context may be null")
            e.printStackTrace()
        } catch (e: Exception) {
            Log.e(TAG, "Exception throw from native")
            e.printStackTrace()
        }
        return null

    }

    fun textPageGetUnicode(textPageIndex: Int, index: Int): Char {
        try {
            return nativeTextGetUnicode(mNativeTextPagesPtr[textPageIndex]!!, index).toChar()
        } catch (e: NullPointerException) {
            Log.e(TAG, "Context may be null")
            e.printStackTrace()
        } catch (e: Exception) {
            Log.e(TAG, "Exception throw from native")
            e.printStackTrace()
        }
        return 0.toChar()
    }

    fun textPageGetCharBox(textPageIndex: Int, index: Int): RectF? {
        try {
            val o = nativeTextGetCharBox(mNativeTextPagesPtr[textPageIndex]!!, index)
            val r = RectF()
            r.left = o[0].toFloat()
            r.right = o[1].toFloat()
            r.bottom = o[2].toFloat()
            r.top = o[3].toFloat()
            return r
        } catch (e: NullPointerException) {
            Log.e(TAG, "Context may be null")
            e.printStackTrace()
        } catch (e: Exception) {
            Log.e(TAG, "Exception throw from native")
            e.printStackTrace()
        }
        return null
    }

    fun textPageGetCharIndexAtPos(
        textPageIndex: Int, x: Double, y: Double,
        xTolerance: Double, yTolerance: Double
    ): Int {
        try {
            return nativeTextGetCharIndexAtPos(
                mNativeTextPagesPtr[textPageIndex]!!, x, y, xTolerance, yTolerance
            )
        } catch (e: NullPointerException) {
            Log.e(TAG, "Context may be null")
            e.printStackTrace()
        } catch (e: Exception) {
            Log.e(TAG, "Exception throw from native")
            e.printStackTrace()
        }
        return -1
    }

    fun textPageCountRects(textPageIndex: Int, start_index: Int, count: Int): Int {
        try {
            return nativeTextCountRects(mNativeTextPagesPtr[textPageIndex]!!, start_index, count)
        } catch (e: NullPointerException) {
            Log.e(TAG, "Context may be null")
            e.printStackTrace()
        } catch (e: Exception) {
            Log.e(TAG, "Exception throw from native")
            e.printStackTrace()
        }
        return -1
    }

    fun textPageGetRect(textPageIndex: Int, rect_index: Int): RectF? {
        try {
            val o = nativeTextGetRect(mNativeTextPagesPtr[textPageIndex]!!, rect_index)
            val r = RectF()
            r.left = o[0].toFloat()
            r.top = o[1].toFloat()
            r.right = o[2].toFloat()
            r.bottom = o[3].toFloat()
            return r
        } catch (e: NullPointerException) {
            Log.e(TAG, "Context may be null")
            e.printStackTrace()
        } catch (e: Exception) {
            Log.e(TAG, "Exception throw from native")
            e.printStackTrace()
        }
        return null
    }

    fun textPageGetBoundedText(textPageIndex: Int, rect: RectF, length: Int): String? {
        try {
            val buf = ShortArray(length + 1)
            val r = nativeTextGetBoundedText(
                mNativeTextPagesPtr[textPageIndex]!!,
                rect.left.toDouble(),
                rect.top.toDouble(),
                rect.right.toDouble(),
                rect.bottom.toDouble(),
                buf
            )
            val bytes = ByteArray((r - 1) * 2)
            val bb = ByteBuffer.wrap(bytes)
            bb.order(ByteOrder.LITTLE_ENDIAN)
            for (i in 0 until r - 1) {
                val s = buf[i]
                bb.putShort(s)
            }
            return String(bytes, StandardCharsets.UTF_16LE)
        } catch (e: NullPointerException) {
            Log.e(TAG, "Context may be null")
            e.printStackTrace()
        } catch (e: Exception) {
            Log.e(TAG, "Exception throw from native")
            e.printStackTrace()
        }
        return null
    }

    /**
     * Prepare information about all characters in a page.
     * Application must call FPDFText_ClosePage to release the text page information.
     *
     * @param pageIndex index of page.
     * @return A handle to the text page information structure. NULL if something goes wrong.
     */
    fun prepareTextInfo(pageIndex: Int): Long {
        val textPagePtr: Long = nativeLoadTextPage(mNativeDocPtr, pageIndex)
        if (validPtr(textPagePtr)) {
            mNativeTextPagesPtr[pageIndex] = textPagePtr
        }
        return textPagePtr
    }

    /**
     * Release all resources allocated for a text page information structure.
     *
     * @param pageIndex index of page.
     */
    fun releaseTextInfo(pageIndex: Int) {
        val textPagePtr: Long = mNativeTextPagesPtr[pageIndex]!!
        if (validPtr(textPagePtr)) {
            nativeCloseTextPage(textPagePtr)
        }
    }

    /**
     * Prepare information about all characters in a range of pages.
     * Application must call FPDFText_ClosePage to release the text page information.
     *
     * @param fromIndex start index of page.
     * @param toIndex   end index of page.
     * @return list of handles to the text page information structure. NULL if something goes wrong.
     */
    fun prepareTextInfo(fromIndex: Int, toIndex: Int): LongArray {
        val textPagesPtr: LongArray = nativeLoadTextPages(mNativeDocPtr, fromIndex, toIndex)
        var pageIndex = fromIndex
        for (page in textPagesPtr) {
            if (pageIndex > toIndex) {
                break
            }
            if (validPtr(page)) {
                mNativeTextPagesPtr[pageIndex] = page
            }
            pageIndex++
        }
        return textPagesPtr
    }

    /**
     * Release all resources allocated for a text page information structure.
     *
     * @param fromIndex start index of page.
     * @param toIndex   end index of page.
     */
    fun releaseTextInfo(fromIndex: Int, toIndex: Int) {
        var textPagesPtr: Long
        for (i in fromIndex until toIndex + 1) {
            textPagesPtr = mNativeTextPagesPtr[i]!!
            if (validPtr(textPagesPtr)) {
                nativeCloseTextPage(textPagesPtr)
            }
        }
    }

    private fun ensureTextPage(pageIndex: Int): Long? {
        val ptr = mNativeTextPagesPtr[pageIndex]
        return if (!validPtr(ptr)) {
            prepareTextInfo(pageIndex)
        } else ptr
    }

    fun countCharactersOnPage(pageIndex: Int): Int {
        return try {
            val ptr = ensureTextPage(pageIndex)
            if (validPtr(ptr)) {
                nativeTextCountChars(ptr!!)
            } else {
                0
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception throw from native")
            e.printStackTrace()
            0
        }
    }

    /**
     * Extract unicode text string from the page.
     *
     * @param pageIndex  index of page.
     * @param startIndex Index for the start characters.
     * @param length     Number of characters to be extracted.
     * @return Number of characters written into the result buffer, including the trailing terminator.
     */
    fun extractCharacters(pageIndex: Int, startIndex: Int, length: Int): String? {
        return try {
            val ptr = ensureTextPage(pageIndex)
            if (!validPtr(ptr)) {
                return null
            }
            val buf = ShortArray(length + 1)
            val r = nativeTextGetText(ptr!!, startIndex, length, buf)
            val bytes = ByteArray((r - 1) * 2)
            val bb = ByteBuffer.wrap(bytes)
            bb.order(ByteOrder.LITTLE_ENDIAN)
            for (i in 0 until r - 1) {
                val s = buf[i]
                bb.putShort(s)
            }
            String(bytes, StandardCharsets.UTF_16LE)
        } catch (e: Exception) {
            Log.e(TAG, "Exception throw from native")
            e.printStackTrace()
            null
        }
    }

    /**
     * Get page rotation in degrees
     *
     * @param pageIndex the page index
     * @return page rotation
     */
    fun getPageRotation(pageIndex: Int): Int {
        return nativeGetPageRotation(pageIndex.toLong())
    }

    /**
     * Get Unicode of a character in a page.
     *
     * @param pageIndex index of page.
     * @param index     Zero-based index of the character.
     * @return The Unicode of the particular character. If a character is not encoded in Unicode, the return value will be zero.
     */
    fun extractCharacter(pageIndex: Int, index: Int): Char {
        return try {
            val ptr = ensureTextPage(pageIndex)
            if (validPtr(ptr)) nativeTextGetUnicode(ptr!!, index).toChar() else 0.toChar()
        } catch (e: Exception) {
            Log.e(TAG, "Exception throw from native")
            e.printStackTrace()
            0.toChar()
        }
    }

    /**
     * Get bounding box of a particular character.
     *
     * @param pageIndex index of page.
     * @param index     Zero-based index of the character.
     * @return the character position measured in PDF "user space".
     */
    fun measureCharacterBox(pageIndex: Int, index: Int): RectF? {
        return try {
            val ptr = ensureTextPage(pageIndex)
            if (!validPtr(ptr)) {
                return null
            }
            val o = nativeTextGetCharBox(ptr!!, index)
            val r = RectF()
            r.left = o[0].toFloat()
            r.right = o[1].toFloat()
            r.bottom = o[2].toFloat()
            r.top = o[3].toFloat()
            r
        } catch (e: Exception) {
            Log.e(TAG, "Exception throw from native")
            e.printStackTrace()
            null
        }
    }

    /**
     * Get the index of a character at or nearby a certain position on the page
     *
     * @param pageIndex  index of page.
     * @param x          X position in PDF "user space".
     * @param y          Y position in PDF "user space".
     * @param xTolerance An x-axis tolerance value for character hit detection, in point unit.
     * @param yTolerance A y-axis tolerance value for character hit detection, in point unit.
     * @return The zero-based index of the character at, or nearby the point (x,y). If there is no character at or nearby the point, return value will be -1. If an error occurs, -3 will be returned.
     */
    fun getCharacterIndex(
        pageIndex: Int, x: Double, y: Double,
        xTolerance: Double, yTolerance: Double
    ): Int {
        return try {
            val ptr = ensureTextPage(pageIndex)
            if (validPtr(ptr)) {
                nativeTextGetCharIndexAtPos(ptr!!, x, y, xTolerance, yTolerance)
            } else {
                -1
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception throw from native")
            e.printStackTrace()
            -1
        }
    }

    /**
     * Count number of rectangular areas occupied by a segment of texts.
     *
     *
     * This function, along with FPDFText_GetRect can be used by applications to detect the position
     * on the page for a text segment, so proper areas can be highlighted or something.
     * FPDFTEXT will automatically merge small character boxes into bigger one if those characters
     * are on the same line and use same font settings.
     *
     * @param pageIndex index of page.
     * @param charIndex Index for the start characters.
     * @param count     Number of characters.
     * @return texts areas count.
     */
    fun countTextRect(pageIndex: Int, charIndex: Int, count: Int): Int {
        return try {
            val ptr = ensureTextPage(pageIndex)
            if (validPtr(ptr)) {
                nativeTextCountRects(ptr!!, charIndex, count)
            } else {
                -1
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception throw from native")
            e.printStackTrace()
            -1
        }
    }

    /**
     * Get a rectangular area from the result generated by FPDFText_CountRects.
     *
     * @param pageIndex index of page.
     * @param rectIndex Zero-based index for the rectangle.
     * @return the text rectangle.
     */
    fun getTextRect(pageIndex: Int, rectIndex: Int): RectF? {
        return try {
            val ptr = ensureTextPage(pageIndex)
            if (!validPtr(ptr)) {
                return null
            }
            val o = nativeTextGetRect(ptr!!, rectIndex)
            val r = RectF()
            r.left = o[0].toFloat()
            r.top = o[1].toFloat()
            r.right = o[2].toFloat()
            r.bottom = o[3].toFloat()
            r
        } catch (e: Exception) {
            Log.e(TAG, "Exception throw from native")
            e.printStackTrace()
            null
        }
    }

    /**
     * Extract unicode text within a rectangular boundary on the page.
     * If the buffer is too small, as much text as will fit is copied into it.
     *
     * @param pageIndex index of page.
     * @param rect      the text rectangle to extract.
     * @return If buffer is NULL or buflen is zero, return number of characters (not bytes) of text
     * present within the rectangle, excluding a terminating NUL.
     *
     *
     * Generally you should pass a buffer at least one larger than this if you want a terminating NUL,
     * which will be provided if space is available. Otherwise, return number of characters copied
     * into the buffer, including the terminating NUL  when space for it is available.
     */
    fun extractText(pageIndex: Int, rect: RectF): String? {
        return try {
            val ptr = ensureTextPage(pageIndex)
            if (!validPtr(ptr)) {
                return null
            }
            val length = nativeTextGetBoundedTextLength(
                ptr!!,
                rect.left.toDouble(),
                rect.top.toDouble(),
                rect.right.toDouble(),
                rect.bottom.toDouble()
            )
            if (length <= 0) {
                return null
            }
            val buf = ShortArray(length + 1)
            val r = nativeTextGetBoundedText(
                ptr,
                rect.left.toDouble(),
                rect.top.toDouble(),
                rect.right.toDouble(),
                rect.bottom.toDouble(),
                buf
            )
            val bytes = ByteArray((r - 1) * 2)
            val bb = ByteBuffer.wrap(bytes)
            bb.order(ByteOrder.LITTLE_ENDIAN)
            for (i in 0 until r - 1) {
                val s = buf[i]
                bb.putShort(s)
            }
            String(bytes, StandardCharsets.UTF_16LE)
        } catch (e: Exception) {
            Log.e(TAG, "Exception throw from native")
            e.printStackTrace()
            null
        }
    }

    /**
     * A handle class for the search context. stopSearch must be called to release this handle.
     *
     * @param pageIndex      index of page.
     * @param query          A unicode match pattern.
     * @param matchCase      match case
     * @param matchWholeWord match the whole word
     * @return A handle for the search context.
     */
    fun newPageSearch(
        pageIndex: Int,
        query: String,
        matchCase: Boolean,
        matchWholeWord: Boolean
    ): TextSearchContext {
        return object : FPDFTextSearchContext(pageIndex, query, matchCase, matchWholeWord) {
            private var mSearchHandlePtr: Long? = null
            override fun prepareSearch() {
                val textPage = prepareTextInfo(pageIndex)
                if (hasSearchHandle(pageIndex)) {
                    val sPtr = mNativeSearchHandlePtr[pageIndex]!!
                    nativeSearchStop(sPtr)
                }
                mSearchHandlePtr = nativeSearchStart(textPage, query, matchCase, matchWholeWord)
            }

            override fun countResult(): Int {
                return if (validPtr(mSearchHandlePtr)) {
                    nativeCountSearchResult(mSearchHandlePtr!!)
                } else -1
            }

            override fun searchNext(): RectF? {
                if (validPtr(mSearchHandlePtr)) {
                    mHasNext = nativeSearchNext(mSearchHandlePtr!!)
                    if (mHasNext) {
                        val index = nativeGetCharIndexOfSearchResult(mSearchHandlePtr!!)
                        if (index > -1) {
                            return measureCharacterBox(this.pageIndex, index)
                        }
                    }
                }
                mHasNext = false
                return null
            }

            override fun searchPrev(): RectF? {
                if (validPtr(mSearchHandlePtr)) {
                    mHasPrev = nativeSearchPrev(mSearchHandlePtr!!)
                    if (mHasPrev) {
                        val index = nativeGetCharIndexOfSearchResult(mSearchHandlePtr!!)
                        if (index > -1) {
                            return measureCharacterBox(this.pageIndex, index)
                        }
                    }
                }
                mHasPrev = false
                return null
            }

            override fun stopSearch() {
                super.stopSearch()
                if (validPtr(mSearchHandlePtr)) {
                    nativeSearchStop(mSearchHandlePtr!!)
                    mNativeSearchHandlePtr.remove(pageIndex)
                }
            }
        }
    }

    fun hasSearchHandle(index: Int): Boolean {
        return mNativeSearchHandlePtr.containsKey(index)
    }

    fun hasPage(index: Int): Boolean {
        return mNativePagesPtr.containsKey(index)
    }

    fun hasTextPage(index: Int): Boolean {
        return mNativeTextPagesPtr.containsKey(index)
    }
}