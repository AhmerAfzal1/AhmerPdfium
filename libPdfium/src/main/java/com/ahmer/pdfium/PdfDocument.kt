package com.ahmer.pdfium

import android.graphics.RectF
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.Closeable

/**
 * Represents a PDF document with thread-safe operations using coroutine mutex.
 * All document operations are protected by an internal mutex to ensure atomic state changes.
 *
 * @property pageCache Cache for loaded page references with access counts
 * @property textPageCache Cache for loaded text page references with access counts
 * @property nativePtr Native pointer to PDF document (JNI reference)
 * @property fileDescriptor File descriptor for PDF content
 */
class PdfDocument : Closeable {
    private val textPageCache: MutableMap<Int, PageCount> = mutableMapOf()
    val pageCache: MutableMap<Int, PageCount> = mutableMapOf()
    var nativePtr: Long = -1
    var fileDescriptor: ParcelFileDescriptor? = null

    /**
     * Retrieves the total number of pages in the document.
     *
     * @return Number of pages or 0 if closed
     */
    val totalPages: Int
        get() = synchronized(lock = PdfiumCore.lock) {
            nativeGetPageCount(docPtr = nativePtr)
        }

    /**
     * Get the page character counts for every page of the PDF document
     *
     * @return IntArray of character counts per page (empty if closed)
     */
    val pageCharCounts: IntArray by lazy {
        synchronized(lock = PdfiumCore.lock) {
            nativeGetPageCharCounts(docPtr = nativePtr)
        }
    }

    /**
     * Opens a page and maintains reference count.
     *
     * @param pageIndex Zero-based page index
     * @return Native page pointer
     * @throws IllegalStateException If document closed
     */
    fun openPage(pageIndex: Int): Long {
        synchronized(lock = PdfiumCore.lock) {
            if (hasPage(pageIndex = pageIndex)) {
                pageCache[pageIndex]?.let {
                    it.count++
                    return it.pagePtr
                }
            }
            val pagePtr: Long = nativeLoadPage(docPtr = nativePtr, pageIndex = pageIndex)
            pageCache[pageIndex] = PageCount(pagePtr = pagePtr, count = 1)
            return pagePtr
        }
    }

    /**
     * Opens a range of pages for batch processing.
     *
     * @param start Start page (inclusive)
     * @param end End page (inclusive)
     * @return LongArray of native page pointers
     */
    fun openPages(start: Int, end: Int): LongArray {
        synchronized(lock = PdfiumCore.lock) {
            return nativeLoadPages(docPtr = nativePtr, fromIndex = start, toIndex = end)
        }
    }

    /**
     * Deletes a page from the document.
     *
     * @param pageIndex Page index to delete
     */
    fun deletePage(pageIndex: Int) {
        synchronized(lock = PdfiumCore.lock) {
            nativeDeletePage(docPtr = nativePtr, pageIndex = pageIndex)
        }
    }

    /**
     * Retrieves document metadata.
     *
     * @return Meta object containing document information
     */
    val metaData: Meta by lazy {
        synchronized(lock = PdfiumCore.lock) {
            Meta().apply {
                title = nativeGetDocumentMetaText(docPtr = nativePtr, tag = "Title")
                author = nativeGetDocumentMetaText(docPtr = nativePtr, tag = "Author")
                subject = nativeGetDocumentMetaText(docPtr = nativePtr, tag = "Subject")
                keywords = nativeGetDocumentMetaText(docPtr = nativePtr, tag = "Keywords")
                creator = nativeGetDocumentMetaText(docPtr = nativePtr, tag = "Creator")
                producer = nativeGetDocumentMetaText(docPtr = nativePtr, tag = "Producer")
                creationDate = nativeGetDocumentMetaText(docPtr = nativePtr, tag = "CreationDate")
                modDate = nativeGetDocumentMetaText(docPtr = nativePtr, tag = "ModDate")
            }
        }
    }

    private fun recursiveGetBookmark(
        tree: MutableList<Bookmark>,
        bookmarkPtr: Long,
        level: Long,
    ) {
        val maxRecursion = 16
        var levelMutable: Long = level
        val bookmark: Bookmark = Bookmark().apply {
            nativePtr = bookmarkPtr
            title = nativeGetBookmarkTitle(bookmarkPtr = bookmarkPtr)
            pageIndex = nativeGetBookmarkDestIndex(docPtr = this@PdfDocument.nativePtr, bookmarkPtr = bookmarkPtr)
            tree.add(this)
        }
        val child: Long = nativeGetFirstChildBookmark(docPtr = nativePtr, bookmarkPtr = bookmarkPtr)
        if (child != 0L && levelMutable < maxRecursion) {
            recursiveGetBookmark(tree = bookmark.children, bookmarkPtr = child, level = levelMutable++)
        }
        val sibling: Long = nativeGetSiblingBookmark(docPtr = nativePtr, bookmarkPtr = bookmarkPtr)
        if (sibling != 0L && levelMutable < maxRecursion) {
            recursiveGetBookmark(tree = tree, bookmarkPtr = sibling, level = levelMutable)
        }
    }

    /**
     * Retrieves the document's table of contents.
     *
     * @return Hierarchical list of bookmarks
     */
    val bookmarks: List<Bookmark> by lazy {
        synchronized(lock = PdfiumCore.lock) {
            val topLevel: MutableList<Bookmark> = mutableListOf()
            val first: Long = nativeGetFirstChildBookmark(docPtr = nativePtr, bookmarkPtr = 0L)
            if (first != 0L) {
                recursiveGetBookmark(tree = topLevel, bookmarkPtr = first, level = 1)
            }
            topLevel
        }
    }

    fun hasPage(pageIndex: Int): Boolean {
        return pageCache.containsKey(key = pageIndex)
    }

    fun hasTextPage(pageIndex: Int): Boolean {
        return textPageCache.containsKey(key = pageIndex)
    }

    /**
     * Opens a text page for text extraction.
     *
     * @param pageIndex Page index to open
     * @return PdfTextPage instance
     * @throws IllegalStateException If document closed
     */
    fun openTextPage(pageIndex: Int): PdfTextPage {
        synchronized(lock = PdfiumCore.lock) {
            if (hasTextPage(pageIndex = pageIndex)) {
                textPageCache[pageIndex]?.let {
                    it.count++
                    return PdfTextPage(
                        doc = this@PdfDocument,
                        pageIndex = pageIndex,
                        textPagePtr = it.pagePtr,
                        pageMap = textPageCache
                    )
                }
            }
            val pagePtr: Long = pageCache[pageIndex]?.pagePtr ?: 0L
            val textPagePtr: Long = nativeLoadTextPage(docPtr = nativePtr, pagePtr = pagePtr)
            textPageCache[pageIndex] = PageCount(pagePtr = textPagePtr, count = 1)
            return PdfTextPage(
                doc = this@PdfDocument,
                pageIndex = pageIndex,
                textPagePtr = textPagePtr,
                pageMap = textPageCache
            )
        }
    }

    /**
     * Opens a range of text pages for batch text processing
     *
     * @param start Starting page index (inclusive)
     * @param end Ending page index (inclusive)
     * @return List of text page wrapper objects
     * @throws IllegalStateException If any page in the range isn't already loaded
     */
    fun openTextPages(start: Int, end: Int): List<PdfTextPage> {
        require(value = start <= end) { "Invalid page range: $start-$end" }
        var textPagesPtr: LongArray
        synchronized(lock = PdfiumCore.lock) {
            textPagesPtr = nativeLoadPages(docPtr = nativePtr, fromIndex = start, toIndex = end)
            return textPagesPtr.mapIndexed { index: Int, pagePtr: Long ->
                PdfTextPage(
                    doc = this@PdfDocument,
                    pageIndex = start + index,
                    textPagePtr = pagePtr,
                    pageMap = textPageCache
                )
            }
        }
    }

    /**
     * Saves a copy of the document with optional modifications.
     *
     * @param callback The write callback to handle the output stream.
     * @param flags Save option flags, which can be one of the following:
     *  - FPDF_INCREMENTAL: Saves changes incrementally, preserving existing data.
     *  - FPDF_NO_INCREMENTAL: Saves a full copy, overwriting the original structure.
     *  - FPDF_REMOVE_SECURITY: Removes password protection or encryption while saving.
     *
     * @return true if the save operation succeeded, false otherwise.
     */
    fun saveAsCopy(callback: PdfWriteCallback, flags: Int): Boolean {
        return nativeSaveAsCopy(docPtr = nativePtr, callback = callback, flags = flags)
    }

    /**
     * Close the document
     * @throws IllegalArgumentException if document is closed
     */
    override fun close() {
        Log.v(TAG, "PdfDocument.close")
        synchronized(lock = PdfiumCore.lock) {
            nativeCloseDocument(docPtr = nativePtr)
            fileDescriptor?.close()
            fileDescriptor = null
            pageCache.clear()
            textPageCache.clear()
        }
    }

    data class Meta(
        var title: String? = null,
        var author: String? = null,
        var subject: String? = null,
        var keywords: String? = null,
        var creator: String? = null,
        var producer: String? = null,
        var creationDate: String? = null,
        var modDate: String? = null,
    )

    data class Bookmark(
        var nativePtr: Long = 0L,
        var title: String? = null,
        var pageIndex: Long = 0L,
        val children: MutableList<Bookmark> = mutableListOf(),
    ) {
        val hasChildren = children.isNotEmpty()
    }

    data class Link(
        val bounds: RectF,
        val destPage: Int?,
        val uri: String?,
    )

    data class PageCount(
        val pagePtr: Long,
        var count: Int
    )

    companion object {
        private val TAG: String? = PdfDocument::class.java.name

        @JvmStatic
        private external fun nativeCloseDocument(docPtr: Long)

        @JvmStatic
        private external fun nativeDeletePage(docPtr: Long, pageIndex: Int)

        @JvmStatic
        private external fun nativeGetBookmarkDestIndex(docPtr: Long, bookmarkPtr: Long): Long

        @JvmStatic
        private external fun nativeGetBookmarkTitle(bookmarkPtr: Long): String?

        @JvmStatic
        private external fun nativeGetDocumentMetaText(docPtr: Long, tag: String): String?

        @JvmStatic
        private external fun nativeGetFirstChildBookmark(docPtr: Long, bookmarkPtr: Long): Long

        @JvmStatic
        private external fun nativeGetPageCharCounts(docPtr: Long): IntArray

        @JvmStatic
        private external fun nativeGetPageCount(docPtr: Long): Int

        @JvmStatic
        private external fun nativeGetSiblingBookmark(docPtr: Long, bookmarkPtr: Long): Long

        @JvmStatic
        private external fun nativeLoadPage(docPtr: Long, pageIndex: Int): Long

        @JvmStatic
        private external fun nativeLoadPages(docPtr: Long, fromIndex: Int, toIndex: Int): LongArray

        @JvmStatic
        private external fun nativeLoadTextPage(docPtr: Long, pagePtr: Long): Long

        @JvmStatic
        private external fun nativeSaveAsCopy(docPtr: Long, callback: PdfWriteCallback, flags: Int): Boolean
    }
}