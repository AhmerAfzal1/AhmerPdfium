package com.ahmer.pdfium

import android.content.Context
import android.graphics.*
import android.os.ParcelFileDescriptor
import android.util.ArrayMap
import android.util.Log
import android.view.Surface
import com.ahmer.pdfium.bookmark.BookMarkNode
import com.ahmer.pdfium.util.Size
import java.io.File
import java.io.IOException


class PdfiumCore(context: Context, file: File, pdfPassword: String? = null) {
    companion object {
        private val TAG = PdfiumCore::class.java.name
        private var mCurrentDpi = 0
        private var mFileDescriptor: ParcelFileDescriptor? = null
        val mNativePagesPtr: MutableMap<Int, Long> = ArrayMap()
        val mNativeTextPagesPtr: MutableMap<Int, Long> = ArrayMap()
        var mNativeDocPtr: Long = 0L

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
        val fileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        newDocument(fd = fileDescriptor, password = pdfPassword)
        Log.d(TAG, "Starting AhmerPdfium...")
    }

    external fun nativeAppendAnnotPoints(
        pagePtr: Long, annotPtr: Long, left: Double, top: Double, right: Double,
        bottom: Double, width: Double, height: Double
    )

    external fun nativeBuildBookMarkTree(docPtr: Long, rootNode: BookMarkNode?): Int
    external fun nativeCloseAnnot(annotPtr: Long)
    external fun nativeCountAnnot(pagePtr: Long): Int
    external fun nativeCountAttachmentPoints(annotPtr: Long): Int
    external fun nativeCountRects(textPtr: Long, st: Int, ed: Int): Int
    external fun nativeCreateAnnot(pagePtr: Long, type: Int): Long
    external fun nativeFindAll(
        mNativeDocPtr: Long, pages: Int, key: String?, flag: Int, arr: ArrayList<SearchRecord?>?
    )

    external fun nativeFindPage(
        mNativeDocPtr: Long, key: String?, pageIdx: Int, flag: Int
    ): SearchRecord?

    external fun nativeFindTextPage(pagePtr: Long, key: String?, flag: Int): Int
    external fun nativeFindTextPageEnd(searchPtr: Long)
    external fun nativeFindTextPageNext(searchPtr: Long): Boolean
    external fun nativeFindTextPageStart(
        textPtr: Long, keyStr: Long, flag: Int, startIdx: Int
    ): Long

    external fun nativeGetAnnot(pagePtr: Long, index: Int): Long
    external fun nativeGetAnnotRect(pagePtr: Long, index: Int, width: Int, height: Int): RectF?
    external fun nativeGetAttachmentPoints(
        pagePtr: Long, annotPtr: Long, idx: Int, width: Int, height: Int,
        p1: PointF?, p2: PointF?, p3: PointF?, p4: PointF?
    ): Boolean

    external fun nativeGetCharIndexAtCoord(
        pagePtr: Long, width: Double, height: Double, textPtr: Long,
        posX: Double, posY: Double, tolX: Double, tolY: Double
    ): Int

    external fun nativeGetCharIndexAtPos(
        textPtr: Long, posX: Double, posY: Double, tolX: Double, tolY: Double
    ): Int

    external fun nativeGetCharPos(
        pagePtr: Long, offsetY: Int, offsetX: Int, width: Int, height: Int, pt: RectF?,
        tid: Long, index: Int, loose: Boolean
    ): Int

    external fun nativeGetFindIdx(searchPtr: Long): Int
    external fun nativeGetFindLength(searchPtr: Long): Int
    external fun nativeGetLinkRect(linkPtr: Long): RectF?
    external fun nativeGetMixedLooseCharPos(
        pagePtr: Long, offsetY: Int, offsetX: Int, width: Int, height: Int, pt: RectF?,
        tid: Long, index: Int, loose: Boolean
    ): Int

    external fun nativeGetRect(
        pagePtr: Long, offsetY: Int, offsetX: Int, width: Int,
        height: Int, textPtr: Long, rect: RectF?, idx: Int
    ): Boolean

    external fun nativeGetStringChars(key: String?): Long
    external fun nativeGetText(textPtr: Long): String?
    external fun nativeOpenAnnot(page: Long, idx: Int): Long
    external fun nativeReleaseStringChars(key: String?, keyStr: Long)
    external fun nativeSetAnnotColor(annotPtr: Long, R: Int, G: Int, B: Int, A: Int)
    external fun nativeSetAnnotRect(
        pagePtr: Long, annotPtr: Long, left: Float, top: Float, right: Float,
        bottom: Float, width: Double, height: Double
    )

    external fun nativeTestAdd(t: Int): Int
    external fun nativeTestCallSetFields(rect: RectF?): Boolean
    external fun nativeTestLoopAdd(arr: ArrayList<Int?>?, size: Int)
    external fun nativeTestSetFields(rect: RectF?): Boolean
    private external fun nativeCloseDocument(docPtr: Long)
    private external fun nativeClosePage(pagePtr: Long)
    private external fun nativeClosePageAndText(pagePtr: Long, textPtr: Long)
    private external fun nativeClosePages(pagesPtr: LongArray)
    private external fun nativeCountAndGetRects(
        pagePtr: Long, offsetY: Int, offsetX: Int, width: Int, height: Int,
        arr: ArrayList<RectF?>?, tid: Long, selSt: Int, selEd: Int
    ): Int

    private external fun nativeDeviceCoordinateToPage(
        pagePtr: Long, startX: Int, startY: Int, sizeX: Int, sizeY: Int,
        rotate: Int, deviceX: Int, deviceY: Int
    ): PointF

    private external fun nativeGetBookmarkDestIndex(docPtr: Long, bookmarkPtr: Long): Long
    private external fun nativeGetBookmarkTitle(bookmarkPtr: Long): String?
    private external fun nativeGetDestPageIndex(docPtr: Long, linkPtr: Long): Int?
    private external fun nativeGetDocumentMetaText(docPtr: Long, tag: String): String?
    private external fun nativeGetFirstChildBookmark(docPtr: Long, bookmarkPtr: Long?): Long?
    private external fun nativeGetLinkAtCoord(
        pagePtr: Long, width: Double, height: Double, posX: Double, posY: Double
    ): Long

    private external fun nativeGetLinkTarget(docPtr: Long, linkPtr: Long): String?
    private external fun nativeGetLinkURI(docPtr: Long, linkPtr: Long): String?
    private external fun nativeGetPageCount(docPtr: Long): Int
    private external fun nativeGetPageHeightPixel(pagePtr: Long, dpi: Int): Int
    private external fun nativeGetPageHeightPoint(pagePtr: Long): Int
    private external fun nativeGetPageLinks(pagePtr: Long): LongArray?
    private external fun nativeGetPageSizeByIndex(docPtr: Long, pageIndex: Int, dpi: Int): Size
    private external fun nativeGetPageWidthPixel(pagePtr: Long, dpi: Int): Int
    private external fun nativeGetPageWidthPoint(pagePtr: Long): Int
    private external fun nativeGetPageRotation(pagePtr: Long): Int
    private external fun nativeGetSiblingBookmark(docPtr: Long, bookmarkPtr: Long): Long?
    private external fun nativeLoadPage(docPtr: Long, pageIndex: Int): Long
    private external fun nativeLoadPages(docPtr: Long, fromIndex: Int, toIndex: Int): LongArray
    private external fun nativeLoadTextPage(pagePtr: Long): Long
    private external fun nativeOpenDocument(fd: Int, password: String?): Long
    private external fun nativeOpenMemDocument(data: ByteArray, password: String?): Long
    private external fun nativePageCoordinateToDevice(
        pagePtr: Long, startX: Int, startY: Int, sizeX: Int, sizeY: Int,
        rotate: Int, pageX: Double, pageY: Double
    ): Point

    private external fun nativeRenderPage(
        pagePtr: Long, surface: Surface, startX: Int, startY: Int,
        drawSizeHor: Int, drawSizeVer: Int, annotation: Boolean
    )

    private external fun nativeRenderPageBitmap(
        pagePtr: Long, bitmap: Bitmap, startX: Int, startY: Int, drawSizeHor: Int,
        drawSizeVer: Int, annotation: Boolean
    )

    /**
     * Create new document from file with password
     * Create new document from file
     */
    @Synchronized
    @Throws(IOException::class)
    fun newDocument(fd: ParcelFileDescriptor, password: String? = null) {
        mFileDescriptor = fd
        mNativeDocPtr = nativeOpenDocument(fd.fd, password)
    }

    /**
     * Get total number of pages in document
     */
    val totalPagesCount: Int
        get() {
            return nativeGetPageCount(mNativeDocPtr)
        }

    /**
     * Open page and store native pointer
     */
    fun openPage(pageIndex: Int): Long {
        val pagePtr: Long = nativeLoadPage(mNativeDocPtr, pageIndex)
        mNativePagesPtr[pageIndex] = pagePtr
        return pagePtr
    }

    /**
     * Open range of pages and store native pointers
     */
    fun openPage(fromIndex: Int, toIndex: Int): LongArray {
        val pagesPtr: LongArray = nativeLoadPages(mNativeDocPtr, fromIndex, toIndex)
        var pageIndex = fromIndex
        for (page in pagesPtr) {
            if (pageIndex > toIndex) break
            mNativePagesPtr[pageIndex] = page
            pageIndex++
        }
        return pagesPtr
    }

    fun openText(pagePtr: Long): Long {
        return nativeLoadTextPage(pagePtr)
    }

    fun getTextRects(
        pagePtr: Long, offsetY: Int, offsetX: Int, size: Size, arr: ArrayList<RectF?>?,
        textPtr: Long, selSt: Int, selEd: Int
    ): Int {
        return nativeCountAndGetRects(
            pagePtr, offsetY, offsetX, size.width, size.height, arr, textPtr, selSt, selEd
        )
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

    fun closePageAndText(pagePtr: Long, textPtr: Long) {
        nativeClosePageAndText(pagePtr, textPtr)
    }

    /**
     * Get page width in pixels.
     * This method requires page to be opened.
     */
    fun getPageWidth(index: Int): Int {
        var pagePtr: Long = -1L
        return if (mNativePagesPtr[index]?.also { pagePtr = it } != null) {
            nativeGetPageWidthPixel(pagePtr, mCurrentDpi)
        } else 0
    }

    /**
     * Get page height in pixels.
     * This method requires page to be opened.
     */
    fun getPageHeight(index: Int): Int {
        var pagePtr: Long = -1L
        return if (mNativePagesPtr[index]?.also { pagePtr = it } != null) {
            nativeGetPageHeightPixel(pagePtr, mCurrentDpi)
        } else 0
    }

    /**
     * Get page width in PostScript points (1/72th of an inch).
     * This method requires page to be opened.
     */
    fun getPageWidthPoint(index: Int): Int {
        var pagePtr: Long = -1L
        return if (mNativePagesPtr[index]?.also { pagePtr = it } != null) {
            nativeGetPageWidthPoint(pagePtr)
        } else 0
    }

    /**
     * Get page height in PostScript points (1/72th of an inch).
     * This method requires page to be opened.
     */
    fun getPageHeightPoint(index: Int): Int {
        var pagePtr: Long = -1L
        return if (mNativePagesPtr[index]?.also { pagePtr = it } != null) {
            nativeGetPageHeightPoint(pagePtr)
        } else 0
    }

    /**
     * Get size of page in pixels.
     * This method does not require given page to be opened.
     */
    fun getPageSize(index: Int): Size {
        return nativeGetPageSizeByIndex(mNativeDocPtr, index, mCurrentDpi)
    }

    /**
     * Get the rotation of page.
     */
    fun getPageRotation(index: Int): Int {
        var pagePtr: Long = -1L
        return if (mNativePagesPtr[index]?.also { pagePtr = it } != null) {
            nativeGetPageRotation(pagePtr)
        } else 0
    }

    /**
     * Render page fragment on [Surface]. This method allows to render annotations.
     * Page must be opened before rendering.
     * Render page fragment on [Surface].
     * Page must be opened before rendering.
     */
    @JvmOverloads
    fun renderPage(
        surface: Surface, pageIndex: Int, startX: Int, startY: Int,
        drawSizeX: Int, drawSizeY: Int, renderAnnot: Boolean = false
    ) {
        try {
            mNativePagesPtr[pageIndex]?.let {
                nativeRenderPage(it, surface, startX, startY, drawSizeX, drawSizeY, renderAnnot)
            }
        } catch (e: NullPointerException) {
            Log.e(TAG, "mContext may be null")
            e.printStackTrace()
        } catch (e: Exception) {
            Log.e(TAG, "Exception throw from native")
            e.printStackTrace()
        }
    }

    /**
     * Render page fragment on [Bitmap]. This method allows to render annotations.
     * Page must be opened before rendering.
     * For more info see [PdfiumSDK.renderPageBitmap]
     * Render page fragment on [Bitmap].
     * Page must be opened before rendering.
     * Supported bitmap configurations:
     * ARGB_8888 - best quality, high memory usage, higher possibility of OutOfMemoryError
     * RGB_565 - little worse quality, twice less memory usage
     */
    @JvmOverloads
    fun renderPageBitmap(
        bitmap: Bitmap, pageIndex: Int, startX: Int, startY: Int, drawSizeX: Int, drawSizeY: Int,
        renderAnnot: Boolean = true
    ) {
        try {
            mNativePagesPtr[pageIndex]?.let { i ->
                nativeRenderPageBitmap(i, bitmap, startX, startY, drawSizeX, drawSizeY, renderAnnot)
            }
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
        var pageIndex = 0L
        var textPageIndex = 0L
        for (index in mNativePagesPtr.keys) {
            pageIndex = mNativePagesPtr[index]!!
        }
        for (ptr in mNativeTextPagesPtr.keys) {
            textPageIndex = mNativeTextPagesPtr[ptr]!!
        }
        closePageAndText(pageIndex, textPageIndex)
        mNativePagesPtr.clear()
        mNativeTextPagesPtr.clear()
        nativeCloseDocument(mNativeDocPtr)
        if (mFileDescriptor != null) {
            try {
                mFileDescriptor?.close()
                mFileDescriptor = null
            } catch (ignored: IOException) {
            } finally {
                mFileDescriptor = null
            }
        }
    }

    /**
     * Get metadata for given document
     */
    val metaData: Meta
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
            meta.totalPages = totalPagesCount
            return meta
        }

    /**
     * Get table of contents (bookmarks) for given document
     */
    val bookmarks: List<Bookmark>
        get() {
            val topLevel: MutableList<Bookmark> = ArrayList()
            val first = nativeGetFirstChildBookmark(mNativeDocPtr, null)
            first?.let {
                recursiveGetBookmark(topLevel, it)
            }
            return topLevel
        }

    private fun recursiveGetBookmark(tree: MutableList<Bookmark>, bookmarkPtr: Long) {
        val bookmark = Bookmark(bookmarkPtr)
        bookmark.title = nativeGetBookmarkTitle(bookmarkPtr)
        bookmark.pageIdx = nativeGetBookmarkDestIndex(mNativeDocPtr, bookmarkPtr)
        tree.add(bookmark)
        val child = nativeGetFirstChildBookmark(mNativeDocPtr, bookmarkPtr)
        if (child != null) {
            recursiveGetBookmark(bookmark.children, child)
        }
        val sibling = nativeGetSiblingBookmark(mNativeDocPtr, bookmarkPtr)
        sibling?.let {
            recursiveGetBookmark(tree, it)
        }
    }

    /**
     * Get all links from given page
     */
    fun getPageLinks(pageIndex: Int): List<Link> {
        val links: MutableList<Link> = ArrayList()
        val nativePagePtr = mNativePagesPtr[pageIndex] ?: return links
        val linkPtrs = nativeGetPageLinks(nativePagePtr)
        if (linkPtrs != null) {
            for (linkPtr in linkPtrs) {
                val uri = nativeGetLinkURI(mNativeDocPtr, linkPtr)
                nativeGetLinkRect(linkPtr)?.let { rect ->
                    nativeGetDestPageIndex(mNativeDocPtr, linkPtr)?.let { index ->
                        links.add(Link(rect, index, uri))
                    }
                }
            }
        }
        return links
    }

    fun getLinkAtCoordinate(pageIndex: Int, size: Size, posX: Float, posY: Float): Long? {
        return mNativePagesPtr[pageIndex]?.let {
            nativeGetLinkAtCoord(
                it, size.width.toDouble(), size.height.toDouble(), posX.toDouble(), posY.toDouble()
            )
        }
    }

    fun getLinkTarget(lnkPtr: Long): String? {
        return nativeGetLinkTarget(mNativeDocPtr, lnkPtr)
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
        pageIndex: Int, startX: Int, startY: Int, sizeX: Int,
        sizeY: Int, rotate: Int, pageX: Double, pageY: Double
    ): Point? {
        val pagePtr = mNativePagesPtr[pageIndex]
        return pagePtr?.let {
            nativePageCoordinateToDevice(it, startX, startY, sizeX, sizeY, rotate, pageX, pageY)
        }
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
    fun mapDeviceCoordinateToPage(
        pageIndex: Int, startX: Int, startY: Int, sizeX: Int,
        sizeY: Int, rotate: Int, deviceX: Int, deviceY: Int
    ): PointF? {
        val pagePtr = mNativePagesPtr[pageIndex]
        return pagePtr?.let {
            nativeDeviceCoordinateToPage(it, startX, startY, sizeX, sizeY, rotate, deviceX, deviceY)
        }
    }

    /**
     * @return mapped coordinates
     */
    fun mapPageCoordinateToDevice(
        pageIndex: Int, startX: Int, startY: Int, sizeX: Int, sizeY: Int,
        rotate: Int, coords: RectF
    ): RectF? {
        val leftTop = mapPageCoordsToDevice(
            pageIndex, startX, startY, sizeX, sizeY, rotate,
            coords.left.toDouble(), coords.top.toDouble()
        )
        val rightBottom = mapPageCoordsToDevice(
            pageIndex, startX, startY, sizeX, sizeY,
            rotate, coords.right.toDouble(), coords.bottom.toDouble()
        )

        return leftTop?.let { lt ->
            rightBottom?.let { rb ->
                RectF(lt.x.toFloat(), lt.y.toFloat(), rb.x.toFloat(), rb.y.toFloat())
            }
        }
    }

    fun hasPage(index: Int): Boolean {
        return mNativePagesPtr.containsKey(index)
    }

    fun hasTextPage(index: Int): Boolean {
        return mNativeTextPagesPtr.containsKey(index)
    }
}