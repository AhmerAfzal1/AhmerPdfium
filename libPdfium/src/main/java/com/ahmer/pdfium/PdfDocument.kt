package com.ahmer.pdfium

import android.graphics.RectF
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.Closeable

/**
 * PdfDocument represents a PDF file and allows you to load pages from it.
 */
class PdfDocument(val nativeDocPtr: Long) : Closeable {
    private val pageMap = mutableMapOf<Int, PageCount>()
    private val textPageMap = mutableMapOf<Int, PageCount>()
    var isClosed = false
        private set
    var parcelFileDescriptor: ParcelFileDescriptor? = null

    private external fun nativeCloseDocument(docPtr: Long)
    private external fun nativeGetBookmarkDestIndex(docPtr: Long, bookmarkPtr: Long): Long
    private external fun nativeGetBookmarkTitle(bookmarkPtr: Long): String?
    private external fun nativeGetDocumentMetaText(docPtr: Long, tag: String): String?
    private external fun nativeGetFirstChildBookmark(docPtr: Long, bookmarkPtr: Long?): Long?
    private external fun nativeGetPageCharCounts(docPtr: Long): IntArray
    private external fun nativeGetPageCount(docPtr: Long): Int
    private external fun nativeGetSiblingBookmark(docPtr: Long, bookmarkPtr: Long): Long?
    private external fun nativeLoadPage(docPtr: Long, pageIndex: Int): Long
    private external fun nativeLoadPages(docPtr: Long, fromIndex: Int, toIndex: Int): LongArray
    private external fun nativeLoadTextPage(docPtr: Long, pagePtr: Long): Long
    private external fun nativeSaveAsCopy(docPtr: Long, callback: PdfWriteCallback): Boolean

    fun hasPage(pageIndex: Int): Boolean {
        return pageMap.containsKey(pageIndex)
    }

    fun hasTextPage(pageIndex: Int): Boolean {
        return textPageMap.containsKey(pageIndex)
    }

    /**
     *  Get the page count of the PDF document
     *  @return the number of pages
     */
    val getPageCount: Int by lazy {
        check(value = !isClosed) { "Already closed" }
        synchronized(lock = PdfiumCore.lock) {
            nativeGetPageCount(nativeDocPtr)
        }
    }

    /**
     *  Get the page character counts for every page of the PDF document
     *  @return an array of character counts
     */
    val getPageCharCounts: IntArray by lazy {
        check(value = !isClosed) { "Already closed" }
        synchronized(lock = PdfiumCore.lock) {
            nativeGetPageCharCounts(nativeDocPtr)
        }
    }

    /**
     * Open page and store native pointer in [PdfDocument]
     * @param pageIndex the page index
     * @return the opened page [PdfPage]
     * @throws IllegalArgumentException if  document is closed or the page cannot be loaded
     */
    fun openPage(pageIndex: Int): PdfPage {
        check(value = !isClosed) { "Already closed" }
        synchronized(lock = PdfiumCore.lock) {
            if (pageMap.containsKey(pageIndex)) {
                pageMap[pageIndex]?.let {
                    it.count++
                    //Log.v(TAG, "from cache openPage: pageIndex: $pageIndex, count: ${it.count}")
                    return PdfPage(document = this, pageIndex, it.pagePtr, pageMap)
                }
            }
            //Log.v(TAG, "openPage: pageIndex: $pageIndex")
            val pagePtr = nativeLoadPage(nativeDocPtr, pageIndex)
            pageMap[pageIndex] = PageCount(pagePtr = pagePtr, count = 1)
            return PdfPage(document = this, pageIndex, pagePtr, pageMap)
        }
    }

    /**
     * Open range of pages and store native pointers in [PdfDocument]
     * @param fromIndex the start index of the range
     * @param toIndex the end index of the range
     * @return the opened pages [PdfPage]
     * @throws IllegalArgumentException if document is closed or the pages cannot be loaded
     */
    fun openPage(fromIndex: Int, toIndex: Int): LongArray {
        check(value = !isClosed) { "Already closed" }
        var pagesPtr: LongArray
        synchronized(lock = PdfiumCore.lock) {
            pagesPtr = nativeLoadPages(nativeDocPtr, fromIndex, toIndex)
            var pageIndex = fromIndex
            for (page in pagesPtr) {
                if (pageIndex > toIndex) break
                pageIndex++
            }
            return pagesPtr
        }
    }

    /**
     * Open a text page
     * @param page the [PdfPage]
     * @return the opened [PdfTextPage]
     * @throws IllegalArgumentException if document is closed or the page cannot be loaded
     */
    fun openTextPage(page: PdfPage): PdfTextPage {
        check(value = !isClosed) { "Already closed" }
        synchronized(lock = PdfiumCore.lock) {
            if (textPageMap.containsKey(page.pageIndex)) {
                textPageMap[page.pageIndex]?.let {
                    it.count++
                    //Log.v(TAG,"from cache openTextPage: pageIndex: ${page.pageIndex}, count: ${it.count}")
                    return PdfTextPage(document = this, page.pageIndex, it.pagePtr, textPageMap)
                }
            }
            //Log.v(TAG,"openTextPage: pageIndex: ${page.pageIndex}")
            val textPagePtr = nativeLoadTextPage(nativeDocPtr, page.pagePtr)
            textPageMap[page.pageIndex] = PageCount(textPagePtr, count = 1)
            return PdfTextPage(document = this, page.pageIndex, textPagePtr, textPageMap)
        }
    }

    /**
     * Open a range of text pages
     * @param fromIndex the start index of the range
     * @param toIndex the end index of the range
     * @return the opened [PdfTextPage] list
     * @throws IllegalArgumentException if document is closed or the pages cannot be loaded
     */
    fun openTextPages(fromIndex: Int, toIndex: Int): List<PdfTextPage> {
        check(value = !isClosed) { "Already closed" }
        var textPagesPtr: LongArray
        synchronized(lock = PdfiumCore.lock) {
            textPagesPtr = nativeLoadPages(nativeDocPtr, fromIndex, toIndex)
            return textPagesPtr.mapIndexed { index: Int, pagePtr: Long ->
                PdfTextPage(this, fromIndex + index, pagePtr, textPageMap)
            }
        }
    }

    /**
     * Save document as a copy
     * @param callback the [PdfWriteCallback] to be called with the data
     * @return true if the document was successfully saved
     * @throws IllegalArgumentException if document is closed
     */
    fun saveAsCopy(callback: PdfWriteCallback): Boolean {
        check(value = !isClosed) { "Already closed" }
        return nativeSaveAsCopy(nativeDocPtr, callback)
    }

    private fun recursiveGetBookmark(tree: MutableList<Bookmark>, bookmarkPtr: Long) {
        check(value = !isClosed) { "Already closed" }
        val bookmark = Bookmark()
        bookmark.nativePtr = bookmarkPtr
        bookmark.title = nativeGetBookmarkTitle(bookmarkPtr)
        bookmark.pageIdx = nativeGetBookmarkDestIndex(nativeDocPtr, bookmarkPtr)
        tree.add(bookmark)
        val child = nativeGetFirstChildBookmark(nativeDocPtr, bookmarkPtr)
        if (child != null) recursiveGetBookmark(bookmark.children, child)
        val sibling = nativeGetSiblingBookmark(nativeDocPtr, bookmarkPtr)
        sibling?.let { recursiveGetBookmark(tree, it) }
    }

    /**
     * Get table of contents (bookmarks) for given document
     * @return the [Bookmark] list
     * @throws IllegalArgumentException if document is closed
     */
    val getTableOfContents: List<Bookmark> by lazy {
        synchronized(lock = PdfiumCore.lock) {
            ArrayList<Bookmark>().apply {
                nativeGetFirstChildBookmark(docPtr = nativeDocPtr, bookmarkPtr = null)?.let {
                    recursiveGetBookmark(tree = this, bookmarkPtr = it)
                }
            }
        }
    }

    /**
     * Get metadata for given document
     * @return the [Meta] data
     * @throws IllegalArgumentException if document is closed
     */
    val getDocumentMeta: Meta by lazy {
        check(value = !isClosed) { "Already closed" }
        synchronized(lock = PdfiumCore.lock) {
            Meta().apply {
                title = nativeGetDocumentMetaText(nativeDocPtr, "Title")
                author = nativeGetDocumentMetaText(nativeDocPtr, "Author")
                subject = nativeGetDocumentMetaText(nativeDocPtr, "Subject")
                keywords = nativeGetDocumentMetaText(nativeDocPtr, "Keywords")
                creator = nativeGetDocumentMetaText(nativeDocPtr, "Creator")
                producer = nativeGetDocumentMetaText(nativeDocPtr, "Producer")
                creationDate = nativeGetDocumentMetaText(nativeDocPtr, "CreationDate")
                modDate = nativeGetDocumentMetaText(nativeDocPtr, "ModDate")
                totalPages = getPageCount
            }
        }
    }

    /**
     * Close the document
     * @throws IllegalArgumentException if document is closed
     */
    override fun close() {
        check(value = !isClosed) { "Already closed" }
        Log.v(TAG, "PdfDocument.close")
        synchronized(lock = PdfiumCore.lock) {
            isClosed = true
            nativeCloseDocument(nativeDocPtr)
            parcelFileDescriptor?.close()
            parcelFileDescriptor = null
        }
    }

    data class Bookmark(
        val children: MutableList<Bookmark> = ArrayList(),
        var nativePtr: Long = 0L,
        var pageIdx: Long = 0L,
        var title: String? = null
    ) {
        fun hasChildren(): Boolean {
            return children.isNotEmpty()
        }
    }

    data class Link(val bounds: RectF?, val destPageIndex: Int?, val uri: String?)

    data class PageCount(val pagePtr: Long, var count: Int)

    data class Meta(
        var title: String? = null,
        var author: String? = null,
        var subject: String? = null,
        var keywords: String? = null,
        var creator: String? = null,
        var producer: String? = null,
        var creationDate: String? = null,
        var modDate: String? = null,
        var totalPages: Int = 0
    )

    companion object {
        private val TAG = PdfDocument::class.java.name
    }
}