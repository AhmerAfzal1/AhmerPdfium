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
    fun getPageCount(): Int {
        check(value = !isClosed) { "Already closed" }
        synchronized(PdfiumCore.lock) {
            return nativeGetPageCount(nativeDocPtr)
        }
    }

    /**
     *  Get the page character counts for every page of the PDF document
     *  @return an array of character counts
     */
    fun getPageCharCounts(): IntArray {
        check(value = !isClosed) { "Already closed" }
        synchronized(PdfiumCore.lock) {
            return nativeGetPageCharCounts(nativeDocPtr)
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
        synchronized(PdfiumCore.lock) {
            if (pageMap.containsKey(pageIndex)) {
                pageMap[pageIndex]?.let {
                    it.count++
                    //Log.v(TAG, "from cache openPage: pageIndex: $pageIndex, count: ${it.count}")
                    return PdfPage(this, pageIndex, it.pagePtr, pageMap)
                }
            }
            //Log.v(TAG, "openPage: pageIndex: $pageIndex")
            val pagePtr = nativeLoadPage(nativeDocPtr, pageIndex)
            pageMap[pageIndex] = PageCount(pagePtr, 1)
            return PdfPage(this, pageIndex, pagePtr, pageMap)
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
        synchronized(PdfiumCore.lock) {
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
        check(!isClosed) { "Already closed" }
        synchronized(PdfiumCore.lock) {
            if (textPageMap.containsKey(page.pageIndex)) {
                textPageMap[page.pageIndex]?.let {
                    it.count++
                    //Log.v(TAG,"from cache openTextPage: pageIndex: ${page.pageIndex}, count: ${it.count}")
                    return PdfTextPage(this, page.pageIndex, it.pagePtr, textPageMap)
                }
            }
            //Log.v(TAG,"openTextPage: pageIndex: ${page.pageIndex}")
            val textPagePtr = nativeLoadTextPage(nativeDocPtr, page.pagePtr)
            textPageMap[page.pageIndex] = PageCount(textPagePtr, 1)
            return PdfTextPage(this, page.pageIndex, textPagePtr, textPageMap)
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
        synchronized(PdfiumCore.lock) {
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
    fun getTableOfContents(): List<Bookmark> {
        synchronized(PdfiumCore.lock) {
            val topLevel: MutableList<Bookmark> = ArrayList()
            val first = nativeGetFirstChildBookmark(nativeDocPtr, null)
            first?.let { recursiveGetBookmark(topLevel, it) }
            return topLevel
        }
    }

    /**
     * Get metadata for given document
     * @return the [Meta] data
     * @throws IllegalArgumentException if document is closed
     */
    fun getDocumentMeta(): Meta {
        check(value = !isClosed) { "Already closed" }
        synchronized(PdfiumCore.lock) {
            val meta = Meta()
            meta.title = nativeGetDocumentMetaText(nativeDocPtr, "Title")
            meta.author = nativeGetDocumentMetaText(nativeDocPtr, "Author")
            meta.subject = nativeGetDocumentMetaText(nativeDocPtr, "Subject")
            meta.keywords = nativeGetDocumentMetaText(nativeDocPtr, "Keywords")
            meta.creator = nativeGetDocumentMetaText(nativeDocPtr, "Creator")
            meta.producer = nativeGetDocumentMetaText(nativeDocPtr, "Producer")
            meta.creationDate = nativeGetDocumentMetaText(nativeDocPtr, "CreationDate")
            meta.modDate = nativeGetDocumentMetaText(nativeDocPtr, "ModDate")
            meta.totalPages = getPageCount()
            return meta
        }
    }

    /**
     * Close the document
     * @throws IllegalArgumentException if document is closed
     */
    override fun close() {
        check(!isClosed) { "Already closed" }
        Log.v(TAG, "PdfDocument.close")
        synchronized(PdfiumCore.lock) {
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