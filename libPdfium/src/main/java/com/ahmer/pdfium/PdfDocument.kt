package com.ahmer.pdfium

import android.graphics.RectF
import android.os.ParcelFileDescriptor
import android.util.Log
import android.view.Surface
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
    fun totalPages(): Int {
        synchronized(lock = PdfiumCore.lock) {
            return nativeGetPageCount(docPtr = nativePtr)
        }
    }

    /**
     * Retrieves character counts for all pages in the document.
     *
     * @return IntArray of character counts per page (empty if closed)
     */
    val pageCharCounts: IntArray
        get() {
            synchronized(lock = PdfiumCore.lock) {
                return nativeGetPageCharCounts(docPtr = nativePtr)
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
            if (pageCache.containsKey(key = pageIndex)) {
                pageCache[pageIndex]?.let {
                    it.count++
                    return it.pagePtr
                }
            }
            val pagePtr = nativeLoadPage(docPtr = nativePtr, pageIndex = pageIndex)
            pageCache[pageIndex] = PageCount(pagePtr = pagePtr, count = 1)
            return pagePtr
        }
    }

    /**
     * Opens a range of pages for batch processing.
     *
     * @param fromIndex Start page (inclusive)
     * @param toIndex End page (inclusive)
     * @return LongArray of native page pointers
     */
    fun openPageRange(fromIndex: Int, toIndex: Int): LongArray {
        synchronized(lock = PdfiumCore.lock) {
            val pagesPtr: LongArray =
                nativeLoadPages(docPtr = nativePtr, fromIndex = fromIndex, toIndex = toIndex)
            var pageIndex = fromIndex
            for (page in pagesPtr) {
                if (pageIndex > toIndex) break
                pageIndex++
            }
            return pagesPtr
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
                creationDate =
                    nativeGetDocumentMetaText(docPtr = nativePtr, tag = "CreationDate")
                modDate = nativeGetDocumentMetaText(docPtr = nativePtr, tag = "ModDate")
                totalPages = totalPages()
            }
        }
    }

    private fun recursiveGetBookmark(
        tree: MutableList<Bookmark>,
        bookmarkPtr: Long,
        level: Long,
    ) {
        val maxRecursion = 16
        var levelMutable = level
        val bookmark: Bookmark = Bookmark().apply {
            nativePtr = bookmarkPtr
            title = nativeGetBookmarkTitle(bookmarkPtr = bookmarkPtr)
            pageIndex = nativeGetBookmarkDestIndex(
                docPtr = this@PdfDocument.nativePtr,
                bookmarkPtr = bookmarkPtr
            )
            tree.add(this)
        }
        val child = nativeGetFirstChildBookmark(nativePtr, bookmarkPtr)
        if (child != 0L && levelMutable < maxRecursion) {
            recursiveGetBookmark(bookmark.children, child, levelMutable++)
        }
        val sibling = nativeGetSiblingBookmark(nativePtr, bookmarkPtr)
        if (sibling != 0L && levelMutable < maxRecursion) {
            recursiveGetBookmark(tree, sibling, levelMutable)
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
            val first = nativeGetFirstChildBookmark(docPtr = nativePtr, bookmarkPtr = 0L)
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
            if (textPageCache.containsKey(key = pageIndex)) {
                textPageCache[pageIndex]?.let {
                    it.count++
                    return PdfTextPage(
                        doc = this@PdfDocument,
                        pageIndex = pageIndex,
                        pagePtr = it.pagePtr,
                        pageMap = textPageCache
                    )
                }
            }
            val pagePtr = pageCache[pageIndex]?.pagePtr ?: 0L
            val textPagePtr = nativeLoadTextPage(docPtr = nativePtr, pagePtr = pagePtr)
            textPageCache[pageIndex] = PageCount(pagePtr = textPagePtr, count = 1)
            return PdfTextPage(
                doc = this@PdfDocument,
                pageIndex = pageIndex,
                pagePtr = textPagePtr,
                pageMap = textPageCache
            )
        }
    }

    /**
     * Opens a range of text pages for batch text processing
     *
     * @param fromIndex Starting page index (inclusive)
     * @param toIndex Ending page index (inclusive)
     * @return List of text page wrapper objects
     * @throws IllegalStateException If any page in the range isn't already loaded
     */
    fun openTextPages(fromIndex: Int, toIndex: Int): List<PdfTextPage> {
        require(value = fromIndex <= toIndex) { "Invalid page range: $fromIndex-$toIndex" }
        var textPagesPtr: LongArray
        synchronized(lock = PdfiumCore.lock) {
            textPagesPtr = nativeLoadPages(
                docPtr = nativePtr,
                fromIndex = fromIndex,
                toIndex = toIndex
            )
            return textPagesPtr.mapIndexed { index: Int, pagePtr: Long ->
                PdfTextPage(
                    doc = this@PdfDocument,
                    pageIndex = fromIndex + index,
                    pagePtr = pagePtr,
                    pageMap = textPageCache
                )
            }
        }
    }

    /**
     * Saves a copy of the document with optional modifications.
     *
     * @param callback Write callback for handling output
     * @param flags Modification flags (INCREMENTAL, NO_INCREMENTAL, REMOVE_SECURITY)
     * @return True if save operation succeeded
     */
    fun saveAsCopy(
        callback: PdfWriteCallback,
        flags: Int = FPDF_INCREMENTAL
    ): Boolean {
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
        var totalPages: Int = 0
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

        const val FPDF_INCREMENTAL = 1
        const val FPDF_NO_INCREMENTAL = 2
        const val FPDF_REMOVE_SECURITY = 3

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
        private external fun nativeRenderPagesSurfaceWithMatrix(
            pages: LongArray, surface: Surface, matrixFloats: FloatArray, clipFloats: FloatArray,
            annotation: Boolean, canvasColor: Int, pageBackgroundColor: Int,
        ): Boolean

        @JvmStatic
        private external fun nativeRenderPagesWithMatrix(
            pages: LongArray, bufferPtr: Long, drawSizeHor: Int, drawSizeVer: Int,
            matrixFloats: FloatArray, clipFloats: FloatArray, annotation: Boolean, canvasColor: Int,
            pageBackgroundColor: Int,
        )

        @JvmStatic
        private external fun nativeSaveAsCopy(
            docPtr: Long, callback: PdfWriteCallback, flags: Int,
        ): Boolean
    }
}