package com.ahmer.pdfium

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Point
import android.graphics.RectF
import android.os.ParcelFileDescriptor
import android.util.Log
import android.view.Surface
import com.ahmer.pdfium.util.Size
import java.io.IOException

class PdfiumCore(context: Context) {

    companion object {
        val lock = Any()
        private val TAG = PdfiumCore::class.java.name
        private var mCurrentDpi = 0

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

    external fun nativeCountRects(textPtr: Long, st: Int, ed: Int): Int
    external fun nativeFindTextPage(pagePtr: Long, key: String?, flag: Int): Int
    external fun nativeFindTextPageEnd(searchPtr: Long)
    external fun nativeFindTextPageNext(searchPtr: Long): Boolean
    external fun nativeFindTextPageStart(
        textPtr: Long, keyStr: Long, flag: Int, startIdx: Int
    ): Long

    external fun nativeGetCharIndexAtCoord(
        pagePtr: Long, width: Double, height: Double, textPtr: Long, posX: Double,
        posY: Double, tolX: Double, tolY: Double
    ): Int

    external fun nativeGetCharPos(
        pagePtr: Long, offsetY: Int, offsetX: Int, width: Int, height: Int,
        pt: RectF?, tid: Long, index: Int, loose: Boolean
    ): Int

    external fun nativeGetFindIdx(searchPtr: Long): Int
    external fun nativeGetFindLength(searchPtr: Long): Int
    external fun nativeGetLinkAtCoord(
        pagePtr: Long, width: Double, height: Double, posX: Double, posY: Double
    ): Long

    external fun nativeGetLinkTarget(docPtr: Long, linkPtr: Long): String?
    external fun nativeGetMixedLooseCharPos(
        pagePtr: Long, offsetY: Int, offsetX: Int, width: Int, height: Int,
        pt: RectF?, tid: Long, index: Int, loose: Boolean
    ): Int

    external fun nativeGetRect(
        pagePtr: Long, offsetY: Int, offsetX: Int, width: Int, height: Int,
        textPtr: Long, rect: RectF?, idx: Int
    ): Boolean

    external fun nativeGetStringChars(key: String?): Long
    external fun nativeGetText(textPtr: Long): String?
    external fun nativeLoadTextPage(pagePtr: Long): Long
    private external fun nativeCloseDocument(docPtr: Long)
    private external fun nativeClosePage(pagePtr: Long)
    private external fun nativeClosePages(pagesPtr: LongArray)
    private external fun nativeCountAndGetRects(
        pagePtr: Long, offsetY: Int, offsetX: Int, width: Int, height: Int,
        arr: ArrayList<RectF>, tid: Long, selSt: Int, selEd: Int
    ): Int

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
    private external fun nativeOpenDocument(fd: Int, password: String): Long
    private external fun nativeOpenMemDocument(data: ByteArray, password: String): Long
    private external fun nativePageCoordsToDevice(
        pagePtr: Long, startX: Int, startY: Int, sizeX: Int, sizeY: Int,
        rotate: Int, pageX: Double, pageY: Double
    ): Point?

    private external fun nativeRenderPage(
        pagePtr: Long, surface: Surface, startX: Int, startY: Int,
        drawSizeHor: Int, drawSizeVer: Int, annotation: Boolean
    )

    private external fun nativeRenderPageBitmap(
        docPtr: Long, pagePtr: Long, bitmap: Bitmap, startX: Int, startY: Int,
        drawSizeHor: Int, drawSizeVer: Int, annotation: Boolean
    )

    private external fun nativeRenderPageBitmap(
        pagePtr: Long, bitmap: Bitmap, startX: Int, startY: Int,
        drawSizeHor: Int, drawSizeVer: Int, annotation: Boolean
    )

    /**
     * Create new document from file
     */
    @Throws(IOException::class)
    fun newDocument(fd: ParcelFileDescriptor): PdfDocument {
        return newDocument(fd, null)
    }

    /**
     * Create new document from file with password
     */
    @Throws(IOException::class)
    fun newDocument(fd: ParcelFileDescriptor, password: String?): PdfDocument {
        val document = PdfDocument()
        document.parcelFileDescriptor = fd
        synchronized(lock) {
            document.mNativeDocPtr = password?.let { nativeOpenDocument(fd.fd, it) }!!
        }
        return document
    }

    /**
     * Create new document from bytearray
     */
    @Throws(IOException::class)
    fun newDocument(data: ByteArray): PdfDocument {
        return newDocument(data, null)
    }

    /**
     * Create new document from bytearray with password
     */
    @Throws(IOException::class)
    fun newDocument(data: ByteArray, password: String?): PdfDocument {
        val document = PdfDocument()
        synchronized(lock) {
            document.mNativeDocPtr = password?.let { nativeOpenMemDocument(data, it) }!!
        }
        return document
    }

    /**
     * Get total number of pages in document
     */
    fun getPageCount(doc: PdfDocument): Int {
        synchronized(lock) { return nativeGetPageCount(doc.mNativeDocPtr) }
    }

    /**
     * Open page
     */
    fun openPage(doc: PdfDocument, pageIndex: Int): Long {
        synchronized(lock) {
            val pagePtr: Long = nativeLoadPage(doc.mNativeDocPtr, pageIndex)
            doc.mNativePagesPtr[pageIndex] = pagePtr
            return pagePtr
        }
    }

    /**
     * Open range of pages
     */
    fun openPage(doc: PdfDocument, fromIndex: Int, toIndex: Int): LongArray {
        synchronized(lock) {
            val pagesPtr: LongArray = nativeLoadPages(doc.mNativeDocPtr, fromIndex, toIndex)
            var pageIndex = fromIndex
            for (page in pagesPtr) {
                if (pageIndex > toIndex) break
                doc.mNativePagesPtr[pageIndex] = page
                pageIndex++
            }
            return pagesPtr
        }
    }

    fun openText(pagePtr: Long): Long {
        synchronized(lock) { return nativeLoadTextPage(pagePtr) }
    }

    fun getTextRects(
        pagePtr: Long, offsetY: Int, offsetX: Int, size: Size, arr: ArrayList<RectF>,
        textPtr: Long, selSt: Int, selEd: Int
    ): Int {
        synchronized(lock) {
            return nativeCountAndGetRects(
                pagePtr, offsetY, offsetX, size.width, size.height, arr, textPtr, selSt, selEd
            )
        }
    }

    /**
     * Get page width in pixels.
     * This method requires page to be opened.
     */
    fun getPageWidth(doc: PdfDocument, index: Int): Int {
        synchronized(lock) {
            var pagePtr: Long = -1L
            return if (doc.mNativePagesPtr[index]?.also { pagePtr = it } != null) {
                nativeGetPageWidthPixel(pagePtr, mCurrentDpi)
            } else 0
        }
    }

    /**
     * Get page height in pixels.
     * This method requires page to be opened.
     */
    fun getPageHeight(doc: PdfDocument, index: Int): Int {
        synchronized(lock) {
            var pagePtr: Long = -1L
            return if (doc.mNativePagesPtr[index]?.also { pagePtr = it } != null) {
                nativeGetPageHeightPixel(pagePtr, mCurrentDpi)
            } else 0
        }
    }

    /**
     * Get page width in PostScript points (1/72th of an inch).
     * This method requires page to be opened.
     */
    fun getPageWidthPoint(doc: PdfDocument, index: Int): Int {
        synchronized(lock) {
            var pagePtr: Long = -1L
            return if (doc.mNativePagesPtr[index]?.also { pagePtr = it } != null) {
                nativeGetPageWidthPoint(pagePtr)
            } else 0
        }
    }

    /**
     * Get page height in PostScript points (1/72th of an inch).
     * This method requires page to be opened.
     */
    fun getPageHeightPoint(doc: PdfDocument, index: Int): Int {
        synchronized(lock) {
            var pagePtr: Long = -1L
            return if (doc.mNativePagesPtr[index]?.also { pagePtr = it } != null) {
                nativeGetPageHeightPoint(pagePtr)
            } else 0
        }
    }

    /**
     * Get size of page in pixels.
     * This method does not require given page to be opened.
     */
    fun getPageSize(doc: PdfDocument, index: Int): Size {
        synchronized(lock) {
            return nativeGetPageSizeByIndex(doc.mNativeDocPtr, index, mCurrentDpi)
        }
    }

    /**
     * Render page fragment on [Surface].
     * Page must be opened before rendering.
     */
    fun renderPage(
        doc: PdfDocument, surface: Surface, pageIndex: Int,
        startX: Int, startY: Int, drawSizeX: Int, drawSizeY: Int
    ) {
        renderPage(doc, surface, pageIndex, startX, startY, drawSizeX, drawSizeY, false)
    }

    /**
     * Render page fragment on [Surface]. This method allows to render annotations.
     * Page must be opened before rendering.
     */
    fun renderPage(
        doc: PdfDocument, surface: Surface, pageIndex: Int, startX: Int, startY: Int,
        drawSizeX: Int, drawSizeY: Int, annotation: Boolean
    ) {
        val mDoc: Long? = doc.mNativePagesPtr[pageIndex]
        if (mDoc != null) {
            synchronized(lock) {
                try {
                    nativeRenderPage(
                        mDoc, surface, startX, startY, drawSizeX, drawSizeY, annotation
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
    }

    /**
     * Render page fragment on [Bitmap].
     * Page must be opened before rendering.
     *
     * Supported bitmap configurations:
     *
     * ARGB_8888 - best quality, high memory usage, higher possibility of OutOfMemoryError
     * RGB_565 - little worse quality, twice less memory usage
     */
    fun renderPageBitmap(
        doc: PdfDocument, bitmap: Bitmap, pageIndex: Int, startX: Int,
        startY: Int, drawSizeX: Int, drawSizeY: Int
    ) {
        renderPageBitmap(doc, bitmap, pageIndex, startX, startY, drawSizeX, drawSizeY, false)
    }

    /**
     * Render page fragment on [Bitmap]. This method allows to render annotations.
     * Page must be opened before rendering.
     *
     *
     * For more info see {PdfiumCore#renderPageBitmap(Bitmap, int, int, int, int, int, boolean)}
     */
    fun renderPageBitmap(
        doc: PdfDocument, bitmap: Bitmap, pageIndex: Int, startX: Int,
        startY: Int, drawSizeX: Int, drawSizeY: Int, annotation: Boolean
    ) {
        val mDoc: Long? = doc.mNativePagesPtr[pageIndex]
        if (mDoc != null) {
            synchronized(lock) {
                try {
                    nativeRenderPageBitmap(
                        mDoc, bitmap, startX, startY, drawSizeX, drawSizeY, annotation
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
    }

    /**
     * Release native resources and opened file
     */
    fun closeDocument(doc: PdfDocument) {
        for (index in doc.mNativePagesPtr.keys) {
            doc.mNativePagesPtr[index]?.let { nativeClosePage(it) }
        }
        doc.mNativePagesPtr.clear()
        nativeCloseDocument(doc.mNativeDocPtr)
        if (doc.parcelFileDescriptor != null) {
            try {
                doc.parcelFileDescriptor?.close()
                doc.parcelFileDescriptor = null
            } catch (ignored: IOException) {
            } finally {
                doc.parcelFileDescriptor = null
            }
        }
    }

    /**
     * Get metadata for given document
     */
    fun getDocumentMeta(doc: PdfDocument): PdfDocument.Meta {
        synchronized(lock) {
            val meta = PdfDocument.Meta()
            meta.title = nativeGetDocumentMetaText(doc.mNativeDocPtr, "Title")
            meta.author = nativeGetDocumentMetaText(doc.mNativeDocPtr, "Author")
            meta.subject = nativeGetDocumentMetaText(doc.mNativeDocPtr, "Subject")
            meta.keywords = nativeGetDocumentMetaText(doc.mNativeDocPtr, "Keywords")
            meta.creator = nativeGetDocumentMetaText(doc.mNativeDocPtr, "Creator")
            meta.producer = nativeGetDocumentMetaText(doc.mNativeDocPtr, "Producer")
            meta.creationDate = nativeGetDocumentMetaText(doc.mNativeDocPtr, "CreationDate")
            meta.modDate = nativeGetDocumentMetaText(doc.mNativeDocPtr, "ModDate")
            meta.totalPages = getPageCount(doc)
            return meta
        }
    }

    /**
     * Get table of contents (bookmarks) for given document
     */
    fun getTableOfContents(doc: PdfDocument): List<PdfDocument.Bookmark> {
        synchronized(lock) {
            val topLevel: MutableList<PdfDocument.Bookmark> = ArrayList()
            val first = nativeGetFirstChildBookmark(doc.mNativeDocPtr, null)
            first?.let { recursiveGetBookmark(topLevel, doc, it) }
            return topLevel
        }
    }

    private fun recursiveGetBookmark(
        tree: MutableList<PdfDocument.Bookmark>, doc: PdfDocument, bookmarkPtr: Long
    ) {
        val bookmark = PdfDocument.Bookmark()
        bookmark.mNativePtr = bookmarkPtr
        bookmark.title = nativeGetBookmarkTitle(bookmarkPtr)
        bookmark.pageIdx = nativeGetBookmarkDestIndex(doc.mNativeDocPtr, bookmarkPtr)
        tree.add(bookmark)
        val child = nativeGetFirstChildBookmark(doc.mNativeDocPtr, bookmarkPtr)
        if (child != null) {
            recursiveGetBookmark(bookmark.children, doc, child)
        }
        val sibling = nativeGetSiblingBookmark(doc.mNativeDocPtr, bookmarkPtr)
        sibling?.let { recursiveGetBookmark(tree, doc, it) }
    }

    /**
     * Get all links from given page
     */
    fun getPageLinks(doc: PdfDocument, pageIndex: Int): List<PdfDocument.Link> {
        synchronized(lock) {
            val links: MutableList<PdfDocument.Link> = ArrayList()
            val nativePagePtr: Long = doc.mNativePagesPtr[pageIndex] ?: return links
            val linkPtrs = nativeGetPageLinks(nativePagePtr)
            for (linkPtr in linkPtrs) {
                val index = nativeGetDestPageIndex(doc.mNativeDocPtr, linkPtr)
                val uri = nativeGetLinkURI(doc.mNativeDocPtr, linkPtr)
                val rect = nativeGetLinkRect(linkPtr)
                if (rect != null && index != null && uri != null) {
                    links.add(PdfDocument.Link(rect, index, uri))
                }
            }
            return links
        }
    }

    /**
     * Map page coordinates to device screen coordinates
     *
     * @param doc       pdf document
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
        doc: PdfDocument, pageIndex: Int, startX: Int, startY: Int, sizeX: Int,
        sizeY: Int, rotate: Int, pageX: Double, pageY: Double
    ): Point? {
        val mDoc: Long? = doc.mNativePagesPtr[pageIndex]
        var point: Point? = null
        if (mDoc != null) {
            point =
                nativePageCoordsToDevice(mDoc, startX, startY, sizeX, sizeY, rotate, pageX, pageY)
        }
        return point
    }

    /**
     * @return mapped coordinates
     * @see PdfiumCore.mapPageCoordsToDevice
     */
    fun mapRectToDevice(
        doc: PdfDocument, pageIndex: Int, startX: Int, startY: Int, sizeX: Int, sizeY: Int,
        rotate: Int, coords: RectF
    ): RectF {
        val leftTop = mapPageCoordsToDevice(
            doc, pageIndex, startX, startY, sizeX, sizeY, rotate, coords.left.toDouble(),
            coords.top.toDouble()
        )
        val rightBottom = mapPageCoordsToDevice(
            doc, pageIndex, startX, startY, sizeX, sizeY, rotate, coords.right.toDouble(),
            coords.bottom.toDouble()
        )
        return RectF(
            leftTop!!.x.toFloat(), leftTop.y.toFloat(),
            rightBottom!!.x.toFloat(), rightBottom.y.toFloat()
        )
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

    fun getLinkAtCoordinate(
        doc: PdfDocument, pageIndex: Int, size: Size, posX: Float, posY: Float
    ): Long? {
        return doc.mNativePagesPtr[pageIndex]?.let {
            nativeGetLinkAtCoord(
                it, size.width.toDouble(), size.height.toDouble(), posX.toDouble(), posY.toDouble()
            )
        }
    }

    fun getLinkTarget(doc: PdfDocument, lnkPtr: Long): String? {
        return nativeGetLinkTarget(doc.mNativeDocPtr, lnkPtr)
    }
}