package com.ahmer.pdfium

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Point
import android.graphics.RectF
import android.os.ParcelFileDescriptor
import android.util.Log
import android.view.Surface
import com.ahmer.pdfium.util.Size
import com.ahmer.pdfium.util.SizeF
import java.io.IOException

class PdfiumCore(context: Context) {
    val getContext: Context = context

    private external fun nativeGetLinkAtCoord(
        pagePtr: Long, width: Int, height: Int, posX: Int, posY: Int
    ): Long

    private external fun nativeLoadTextPage(pagePtr: Long): Long
    private external fun nativeCloseDocument(docPtr: Long)
    private external fun nativeClosePage(pagePtr: Long)
    private external fun nativeClosePages(pagesPtr: LongArray)
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
    private external fun nativeOpenDocument(fd: Int, password: String?): Long
    private external fun nativeOpenMemDocument(data: ByteArray, password: String?): Long
    private external fun nativePageCoordsToDevice(
        pagePtr: Long, startX: Int, startY: Int, sizeX: Int, sizeY: Int,
        rotate: Int, pageX: Double, pageY: Double
    ): Point

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
     * @param parcelFileDescriptor opened file descriptor of file
     * @return PdfDocument
     */
    @Throws(IOException::class)
    fun newDocument(parcelFileDescriptor: ParcelFileDescriptor): PdfDocument {
        return newDocument(parcelFileDescriptor, null)
    }

    /**
     * Create new document from file with password
     * @param parcelFileDescriptor opened file descriptor of file
     * @param password password for decryption
     * @return PdfDocument
     */
    @Throws(IOException::class)
    fun newDocument(parcelFileDescriptor: ParcelFileDescriptor, password: String?): PdfDocument {
        synchronized(lock) {
            return PdfDocument(
                nativeDocPtr = nativeOpenDocument(parcelFileDescriptor.fd, password)
            ).also { document ->
                document.parcelFileDescriptor = parcelFileDescriptor
            }
        }
    }

    /**
     * Create new document from bytearray
     * @param data bytearray of pdf file
     * @return PdfDocument
     */
    @Throws(IOException::class)
    fun newDocument(data: ByteArray): PdfDocument {
        return newDocument(data, null)
    }

    /**
     * Create new document from bytearray with password
     * @param data bytearray of pdf file
     * @param password password for decryption
     * @return PdfDocument
     */
    @Throws(IOException::class)
    fun newDocument(data: ByteArray, password: String?): PdfDocument {
        synchronized(lock) {
            return PdfDocument(nativeOpenMemDocument(data, password)).also { document ->
                document.parcelFileDescriptor = null
            }
        }
    }

    /**
     * Get total number of pages in document
     */
    fun getPageCount(doc: PdfDocument): Int {
        synchronized(lock) { return nativeGetPageCount(doc.nativeDocPtr) }
    }

    /**
     * Open page
     */
    fun openPage(doc: PdfDocument, pageIndex: Int): Long {
        synchronized(lock) {
            val pagePtr: Long = nativeLoadPage(doc.nativeDocPtr, pageIndex)
            doc.nativePagesPtr[pageIndex] = pagePtr
            return pagePtr
        }
    }

    /**
     * Open range of pages
     */
    fun openPage(doc: PdfDocument, fromIndex: Int, toIndex: Int): LongArray {
        synchronized(lock) {
            val pagesPtr: LongArray = nativeLoadPages(doc.nativeDocPtr, fromIndex, toIndex)
            var pageIndex = fromIndex
            for (page in pagesPtr) {
                if (pageIndex > toIndex) break
                doc.nativePagesPtr[pageIndex] = page
                pageIndex++
            }
            return pagesPtr
        }
    }

    fun openText(pagePtr: Long): Long {
        synchronized(lock) { return nativeLoadTextPage(pagePtr) }
    }

    /**
     * Get page width in pixels.
     * This method requires page to be opened.
     */
    fun getPageWidth(doc: PdfDocument, index: Int): Int {
        synchronized(lock) {
            var pagePtr: Long = -1L
            return if (doc.nativePagesPtr[index]?.also { pagePtr = it } != null) {
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
            return if (doc.nativePagesPtr[index]?.also { pagePtr = it } != null) {
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
            return if (doc.nativePagesPtr[index]?.also { pagePtr = it } != null) {
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
            return if (doc.nativePagesPtr[index]?.also { pagePtr = it } != null) {
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
            return nativeGetPageSizeByIndex(doc.nativeDocPtr, index, mCurrentDpi)
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
        val mDoc: Long? = doc.nativePagesPtr[pageIndex]
        if (mDoc != null) {
            synchronized(lock) {
                try {
                    nativeRenderPage(
                        mDoc, surface, startX, startY, drawSizeX, drawSizeY, annotation
                    )
                } catch (e: NullPointerException) {
                    Log.e(TAG, "mContext may be null", e)
                    e.printStackTrace()
                } catch (e: Exception) {
                    Log.e(TAG, "Exception throw from native", e)
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
     * For more info see {PdfiumCore#renderPageBitmap(Bitmap, int, int, int, int, int, boolean)}
     */
    fun renderPageBitmap(
        doc: PdfDocument, bitmap: Bitmap, pageIndex: Int, startX: Int,
        startY: Int, drawSizeX: Int, drawSizeY: Int, annotation: Boolean
    ) {
        val mDoc: Long? = doc.nativePagesPtr[pageIndex]
        if (mDoc != null) {
            synchronized(lock) {
                try {
                    nativeRenderPageBitmap(
                        mDoc, bitmap, startX, startY, drawSizeX, drawSizeY, annotation
                    )
                } catch (e: NullPointerException) {
                    Log.e(TAG, "context may be null", e)
                    e.printStackTrace()
                } catch (e: Exception) {
                    Log.e(TAG, "Exception throw from native", e)
                    e.printStackTrace()
                }
            }
        }
    }

    /**
     * Release native resources and opened file
     */
    fun closeDocument(doc: PdfDocument) {
        for (index in doc.nativePagesPtr.keys) {
            doc.nativePagesPtr[index]?.let { nativeClosePage(it) }
        }
        doc.nativePagesPtr.clear()
        nativeCloseDocument(doc.nativeDocPtr)
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
            meta.title = nativeGetDocumentMetaText(doc.nativeDocPtr, "Title")
            meta.author = nativeGetDocumentMetaText(doc.nativeDocPtr, "Author")
            meta.subject = nativeGetDocumentMetaText(doc.nativeDocPtr, "Subject")
            meta.keywords = nativeGetDocumentMetaText(doc.nativeDocPtr, "Keywords")
            meta.creator = nativeGetDocumentMetaText(doc.nativeDocPtr, "Creator")
            meta.producer = nativeGetDocumentMetaText(doc.nativeDocPtr, "Producer")
            meta.creationDate = nativeGetDocumentMetaText(doc.nativeDocPtr, "CreationDate")
            meta.modDate = nativeGetDocumentMetaText(doc.nativeDocPtr, "ModDate")
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
            val first = nativeGetFirstChildBookmark(doc.nativeDocPtr, null)
            first?.let { recursiveGetBookmark(topLevel, doc, it) }
            return topLevel
        }
    }

    private fun recursiveGetBookmark(
        tree: MutableList<PdfDocument.Bookmark>, doc: PdfDocument, bookmarkPtr: Long
    ) {
        val bookmark = PdfDocument.Bookmark()
        bookmark.nativePtr = bookmarkPtr
        bookmark.title = nativeGetBookmarkTitle(bookmarkPtr)
        bookmark.pageIdx = nativeGetBookmarkDestIndex(doc.nativeDocPtr, bookmarkPtr)
        tree.add(bookmark)
        val child = nativeGetFirstChildBookmark(doc.nativeDocPtr, bookmarkPtr)
        if (child != null) recursiveGetBookmark(bookmark.children, doc, child)
        val sibling = nativeGetSiblingBookmark(doc.nativeDocPtr, bookmarkPtr)
        sibling?.let { recursiveGetBookmark(tree, doc, it) }
    }

    /**
     * Get all links from given page
     */
    fun getPageLinks(doc: PdfDocument, pageIndex: Int, size: SizeF, posX: Float, posY: Float):
            List<PdfDocument.Link> {
        synchronized(lock) {
            val mLinks: MutableList<PdfDocument.Link> = ArrayList()
            val mPagePtr: Long = doc.nativePagesPtr[pageIndex] ?: return mLinks
            val mPageLinks: LongArray = nativeGetPageLinks(mPagePtr)
            val mLinkAtCoordinate: Long = nativeGetLinkAtCoord(
                mPagePtr, size.width.toInt(), size.height.toInt(), posX.toInt(), posY.toInt()
            )
            for (linkPtr in mPageLinks) {
                val mIndex: Int? = nativeGetDestPageIndex(doc.nativeDocPtr, mLinkAtCoordinate)
                val mUri: String? = nativeGetLinkURI(doc.nativeDocPtr, linkPtr)
                val mBound: RectF? = nativeGetLinkRect(linkPtr)
                if (mUri == null) {
                    val uri: String? = nativeGetLinkURI(doc.nativeDocPtr, mLinkAtCoordinate)
                    mLinks.add(PdfDocument.Link(mBound, mIndex, uri))
                } else {
                    mLinks.add(PdfDocument.Link(mBound, mIndex, mUri))
                }
            }
            return mLinks
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
    ): Point {
        val mDoc: Long? = doc.nativePagesPtr[pageIndex]
        var p: Point? = null
        if (mDoc != null) {
            p = nativePageCoordsToDevice(mDoc, startX, startY, sizeX, sizeY, rotate, pageX, pageY)
        }
        return p!!
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
            leftTop.x.toFloat(), leftTop.y.toFloat(),
            rightBottom.x.toFloat(), rightBottom.y.toFloat()
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

    companion object {
        val lock = Any()
        private val TAG = PdfiumCore::class.java.name
        private var mCurrentDpi = 0

        init {
            System.loadLibrary("pdfsdk")
            System.loadLibrary("pdfsdk_jni")
        }
    }

    init {
        mCurrentDpi = context.resources.displayMetrics.densityDpi
        Log.d(TAG, "Starting AhmerPdfium...")
    }
}