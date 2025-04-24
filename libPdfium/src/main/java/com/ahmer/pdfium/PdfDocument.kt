package com.ahmer.pdfium

import android.graphics.Matrix
import android.graphics.RectF
import android.os.ParcelFileDescriptor
import android.util.Log
import android.view.Surface
import com.ahmer.pdfium.util.handleAlreadyClosed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.util.Collections

/**
 * Represents a PDF document and provides methods for interacting with its contents.
 *
 * @property pageMap Cache for storing loaded page references
 * @property textPageMap Cache for storing loaded text page references
 * @property isClosed Flag indicating if the document is closed
 * @property nativeDocPtr Native pointer to the PDF document
 * @property parcelFileDescriptor File descriptor for the PDF document
 */
class PdfDocument : Closeable {
    val pageMap: MutableMap<Int, PageCount> = mutableMapOf()
    val textPageMap: MutableMap<Int, PageCount> = mutableMapOf()
    var isClosed = false
        private set

    var nativeDocPtr: Long = -1

    var parcelFileDescriptor: ParcelFileDescriptor? = null

    /**
     * Retrieves the total number of pages in the document.
     *
     * @return Number of pages or 0 if document is closed
     */
    suspend fun getPageCount(): Int = withContext(context = Dispatchers.IO) {
        if (handleAlreadyClosed(isClosed = isClosed)) return@withContext 0
        synchronized(lock = PdfiumCore.lock) {
            return@withContext nativeGetPageCount(docPtr = nativeDocPtr)
        }
    }

    /**
     * Retrieves character counts for all pages in the document.
     *
     * @return Array of character counts per page
     */
    suspend fun getPageCharCounts(): IntArray = withContext(context = Dispatchers.IO) {
        if (handleAlreadyClosed(isClosed = isClosed)) return@withContext IntArray(size = 0)
        synchronized(lock = PdfiumCore.lock) {
            return@withContext nativeGetPageCharCounts(docPtr = nativeDocPtr)
        }
    }

    /**
     * Opens a page and maintains reference count.
     *
     * @param pageIndex Zero-based page index
     * @return Native pointer to the loaded page
     * @throws IllegalStateException If document is closed
     */
    suspend fun openPage(pageIndex: Int): Long = withContext(context = Dispatchers.IO) {
        check(value = !isClosed) { "Already closed" }
        synchronized(lock = PdfiumCore.lock) {
            pageMap[pageIndex]?.let {
                it.count++
                return@withContext it.pagePtr
            }

            val pagePtr = nativeLoadPage(docPtr = nativeDocPtr, pageIndex = pageIndex)
            pageMap[pageIndex] = PageCount(pagePtr = pagePtr, count = 1)
            return@withContext pagePtr
        }
    }

    /**
     * Opens a range of pages for batch processing.
     *
     * @param fromIndex Starting page index (inclusive)
     * @param toIndex Ending page index (inclusive)
     * @return Array of native page pointers
     */
    suspend fun openPages(fromIndex: Int, toIndex: Int): LongArray =
        withContext(context = Dispatchers.IO) {
            if (handleAlreadyClosed(isClosed = isClosed)) return@withContext LongArray(size = 0)
            synchronized(lock = PdfiumCore.lock) {
                return@withContext nativeLoadPages(
                    docPtr = nativeDocPtr,
                    fromIndex = fromIndex,
                    toIndex = toIndex
                )
            }
        }

    /**
     * Deletes a page from the document.
     *
     * @param pageIndex Zero-based index of page to delete
     */
    suspend fun deletePage(pageIndex: Int) = withContext(context = Dispatchers.IO) {
        if (handleAlreadyClosed(isClosed = isClosed)) return@withContext
        synchronized(lock = PdfiumCore.lock) {
            return@withContext nativeDeletePage(docPtr = nativeDocPtr, pageIndex = pageIndex)
        }
    }

    /**
     * Retrieves document metadata.
     *
     * @return Meta object containing document information
     */
    suspend fun getDocumentMeta(): Meta = withContext(context = Dispatchers.IO) {
        if (handleAlreadyClosed(isClosed = isClosed)) return@withContext Meta()
        synchronized(lock = PdfiumCore.lock) {
            return@withContext Meta(
                title = nativeGetDocumentMetaText(docPtr = nativeDocPtr, tag = "Title"),
                author = nativeGetDocumentMetaText(docPtr = nativeDocPtr, tag = "Author"),
                subject = nativeGetDocumentMetaText(docPtr = nativeDocPtr, tag = "Subject"),
                keywords = nativeGetDocumentMetaText(docPtr = nativeDocPtr, tag = "Keywords"),
                creator = nativeGetDocumentMetaText(docPtr = nativeDocPtr, tag = "Creator"),
                producer = nativeGetDocumentMetaText(docPtr = nativeDocPtr, tag = "Producer"),
                creationDate = nativeGetDocumentMetaText(
                    docPtr = nativeDocPtr, tag = "CreationDate"
                ),
                modDate = nativeGetDocumentMetaText(docPtr = nativeDocPtr, tag = "ModDate"),
                totalPages = nativeGetPageCount(docPtr = nativeDocPtr)
            )
        }
    }

    private fun recursiveGetBookmark(
        tree: MutableList<Bookmark>,
        bookmarkPtr: Long,
        level: Long,
    ) {
        if (handleAlreadyClosed(isClosed = isClosed)) return
        if (level > 16) return

        val bookmark = Bookmark(
            nativePtr = bookmarkPtr,
            title = nativeGetBookmarkTitle(bookmarkPtr = bookmarkPtr),
            pageIndex = nativeGetBookmarkDestIndex(
                docPtr = nativeDocPtr,
                bookmarkPtr = bookmarkPtr
            ),
            children = mutableListOf()
        )

        tree.add(bookmark)

        val child = nativeGetFirstChildBookmark(docPtr = nativeDocPtr, bookmarkPtr = bookmarkPtr)
        if (child != 0L) {
            recursiveGetBookmark(tree = bookmark.children, bookmarkPtr = child, level = level + 1)
        }

        val sibling = nativeGetSiblingBookmark(docPtr = nativeDocPtr, bookmarkPtr = bookmarkPtr)
        if (sibling != 0L) {
            recursiveGetBookmark(tree = tree, bookmarkPtr = sibling, level = level)
        }
    }

    /**
     * Retrieves the document's table of contents.
     *
     * @return Hierarchical list of bookmarks
     */
    suspend fun getTableOfContents(): List<Bookmark> = withContext(context = Dispatchers.IO) {
        if (handleAlreadyClosed(isClosed = isClosed)) return@withContext emptyList()
        synchronized(lock = PdfiumCore.lock) {
            val bookmarks: MutableList<Bookmark> = mutableListOf()
            val first: Long = nativeGetFirstChildBookmark(docPtr = nativeDocPtr, bookmarkPtr = 0L)
            if (first != 0L) {
                recursiveGetBookmark(tree = bookmarks, bookmarkPtr = first, level = 1)
            }
            return@withContext Collections.unmodifiableList(bookmarks)
        }
    }

    fun hasPage(pageIndex: Int): Boolean {
        return pageMap.containsKey(key = pageIndex)
    }

    fun hasTextPage(pageIndex: Int): Boolean {
        return textPageMap.containsKey(key = pageIndex)
    }

    /**
     * Opens a text page for text extraction.
     *
     * @param pageIndex Zero-based page index
     * @return Text page wrapper object
     */
    suspend fun openTextPage(pageIndex: Int): PdfTextPage = withContext(context = Dispatchers.IO) {
        check(value = !isClosed) { "Already closed" }
        synchronized(lock = PdfiumCore.lock) {
            textPageMap[pageIndex]?.let {
                it.count++
                return@withContext PdfTextPage(
                    doc = this@PdfDocument,
                    pageIndex = pageIndex,
                    pagePtr = it.pagePtr,
                    pageMap = textPageMap
                )
            }

            val pagePtr: Long =
                pageMap[pageIndex]?.pagePtr ?: throw IllegalStateException("Page not loaded")
            val textPagePtr: Long = nativeLoadTextPage(docPtr = nativeDocPtr, pagePtr = pagePtr)
            textPageMap[pageIndex] = PageCount(pagePtr = textPagePtr, count = 1)
            return@withContext PdfTextPage(
                doc = this@PdfDocument,
                pageIndex = pageIndex,
                pagePtr = textPagePtr,
                pageMap = textPageMap
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
    suspend fun openTextPages(fromIndex: Int, toIndex: Int): List<PdfTextPage> =
        withContext(context = Dispatchers.IO) {
            if (handleAlreadyClosed(isClosed = isClosed)) return@withContext emptyList()
            require(value = fromIndex <= toIndex) { "Invalid page range: $fromIndex-$toIndex" }
            synchronized(lock = PdfiumCore.lock) {
                return@withContext nativeLoadPages(
                    docPtr = nativeDocPtr,
                    fromIndex = fromIndex,
                    toIndex = toIndex
                ).mapIndexed { index: Int, pagePtr: Long ->
                    PdfTextPage(
                        doc = this@PdfDocument,
                        pageIndex = fromIndex + index,
                        pagePtr = pagePtr,
                        pageMap = textPageMap
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
    suspend fun saveAsCopy(
        callback: PdfWriteCallback,
        flags: Int = FPDF_INCREMENTAL
    ): Boolean = withContext(context = Dispatchers.IO) {
        if (handleAlreadyClosed(isClosed = isClosed)) return@withContext false
        synchronized(PdfiumCore.lock) {
            return@withContext nativeSaveAsCopy(
                docPtr = nativeDocPtr,
                callback = callback,
                flags = flags
            )
        }
    }

    /**
     * Close the document
     * @throws IllegalArgumentException if document is closed
     */
    override fun close() {
        if (handleAlreadyClosed(isClosed = isClosed)) return
        Log.v(TAG, "Closing PDF document")
        synchronized(lock = PdfiumCore.lock) {
            isClosed = true
            nativeCloseDocument(docPtr = nativeDocPtr)
            parcelFileDescriptor?.close()
            parcelFileDescriptor = null
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
        fun hasChildren() = children.isNotEmpty()
    }

    data class Link(
        val bounds: RectF,
        val destPageIndex: Int?,
        val uri: String?,
    )

    data class PageCount(
        val pagePtr: Long,
        var count: Int
    )

    companion object {
        private val TAG = PdfDocument::class.java.name

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