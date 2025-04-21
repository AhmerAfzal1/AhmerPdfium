package com.ahmer.pdfium

import android.graphics.RectF
import android.os.ParcelFileDescriptor
import android.util.Log
import android.view.Surface
import com.ahmer.pdfium.util.handleAlreadyClosed
import java.io.Closeable

private const val MAX_RECURSION = 16
private const val THREE_BY_THREE = 9

/**
 * PdfDocument represents a PDF file and allows you to load pages from it.
 */
class PdfDocument : Closeable {
    val pageMap: MutableMap<Int, PageCount> = mutableMapOf()
    val textPageMap: MutableMap<Int, PageCount> = mutableMapOf()
    var isClosed = false
        private set

    var nativeDocPtr: Long = -1

    var parcelFileDescriptor: ParcelFileDescriptor? = null

    private external fun nativeCloseDocument(docPtr: Long)
    private external fun nativeDeletePage(docPtr: Long, pageIndex: Int)
    private external fun nativeGetBookmarkDestIndex(docPtr: Long, bookmarkPtr: Long): Long
    private external fun nativeGetBookmarkTitle(bookmarkPtr: Long): String?
    private external fun nativeGetDocumentMetaText(docPtr: Long, tag: String): String?
    private external fun nativeGetFirstChildBookmark(docPtr: Long, bookmarkPtr: Long): Long
    private external fun nativeGetPageCharCounts(docPtr: Long): IntArray
    private external fun nativeGetPageCount(docPtr: Long): Int
    private external fun nativeGetSiblingBookmark(docPtr: Long, bookmarkPtr: Long): Long
    private external fun nativeLoadPage(docPtr: Long, pageIndex: Int): Long
    private external fun nativeLoadPages(docPtr: Long, fromIndex: Int, toIndex: Int): LongArray
    private external fun nativeLoadTextPage(docPtr: Long, pagePtr: Long): Long
    private external fun nativeRenderPagesSurfaceWithMatrix(
        pages: LongArray, surface: Surface, matrixFloats: FloatArray, clipFloats: FloatArray,
        annotation: Boolean, canvasColor: Int, pageBackgroundColor: Int,
    ): Boolean

    private external fun nativeRenderPagesWithMatrix(
        pages: LongArray, bufferPtr: Long, drawSizeHor: Int, drawSizeVer: Int,
        matrixFloats: FloatArray, clipFloats: FloatArray, annotation: Boolean, canvasColor: Int,
        pageBackgroundColor: Int,
    )

    private external fun nativeSaveAsCopy(
        docPtr: Long, callback: PdfWriteCallback, flags: Int,
    ): Boolean

    /**
     *  Get the page count of the PDF document
     *  @return the number of pages
     */
    fun getPageCount(): Int {
        if (handleAlreadyClosed(isClosed = isClosed)) return 0
        synchronized(lock = PdfiumCore.lock) {
            return nativeGetPageCount(docPtr = nativeDocPtr)
        }
    }

    /**
     *  Get the page character counts for every page of the PDF document
     *  @return an array of character counts
     */
    fun getPageCharCounts(): IntArray {
        if (handleAlreadyClosed(isClosed = isClosed)) return IntArray(0)
        synchronized(lock = PdfiumCore.lock) {
            return nativeGetPageCharCounts(docPtr = nativeDocPtr)
        }
    }

    /**
     * Open page and store native pointer in [PdfDocument]
     * @param pageIndex the page index
     * @return the opened page [Long]
     * @throws IllegalArgumentException if  document is closed or the page cannot be loaded
     */
    fun openPage(pageIndex: Int): Long {
        check(value = !isClosed) { "Already closed" }
        synchronized(lock = PdfiumCore.lock) {
            if (pageMap.containsKey(key = pageIndex)) {
                pageMap[pageIndex]?.let {
                    it.count++
                    //Log.v(TAG, "from cache openPage: pageIndex: $pageIndex, count: ${it.count}")
                    return it.pagePtr
                }
            }
            //Log.v(TAG, "openPage: pageIndex: $pageIndex")
            val pagePtr = nativeLoadPage(docPtr = nativeDocPtr, pageIndex = pageIndex)
            pageMap[pageIndex] = PageCount(pagePtr = pagePtr, count = 1)
            return pagePtr
        }
    }

    /**
     * Open range of pages and store native pointers in [PdfDocument]
     * @param fromIndex the start index of the range
     * @param toIndex the end index of the range
     * @return the opened pages [LongArray]
     * @throws IllegalArgumentException if document is closed or the pages cannot be loaded
     */
    fun openPages(fromIndex: Int, toIndex: Int): LongArray {
        if (handleAlreadyClosed(isClosed = isClosed)) return LongArray(size = 0)
        var pagesPtr: LongArray
        synchronized(lock = PdfiumCore.lock) {
            pagesPtr =
                nativeLoadPages(docPtr = nativeDocPtr, fromIndex = fromIndex, toIndex = toIndex)
            var pageIndex = fromIndex
            for (page in pagesPtr) {
                if (pageIndex > toIndex) break
                pageIndex++
            }
            return pagesPtr
        }
    }

    /**
     * Delete page
     * @param pageIndex the page index
     * @throws IllegalArgumentException if document is closed
     */
    fun deletePage(pageIndex: Int) {
        if (handleAlreadyClosed(isClosed = isClosed)) return
        synchronized(lock = PdfiumCore.lock) {
            nativeDeletePage(docPtr = nativeDocPtr, pageIndex = pageIndex)
        }
    }

    /**
     * Get metadata for given document
     * @return the [Meta] data
     * @throws IllegalArgumentException if document is closed
     */
    fun getDocumentMeta(): Meta {
        if (handleAlreadyClosed(isClosed = isClosed)) return Meta()
        synchronized(lock = PdfiumCore.lock) {
            return Meta().apply {
                title = nativeGetDocumentMetaText(docPtr = nativeDocPtr, tag = "Title")
                author = nativeGetDocumentMetaText(docPtr = nativeDocPtr, tag = "Author")
                subject = nativeGetDocumentMetaText(docPtr = nativeDocPtr, tag = "Subject")
                keywords = nativeGetDocumentMetaText(docPtr = nativeDocPtr, tag = "Keywords")
                creator = nativeGetDocumentMetaText(docPtr = nativeDocPtr, tag = "Creator")
                producer = nativeGetDocumentMetaText(docPtr = nativeDocPtr, tag = "Producer")
                creationDate =
                    nativeGetDocumentMetaText(docPtr = nativeDocPtr, tag = "CreationDate")
                modDate = nativeGetDocumentMetaText(docPtr = nativeDocPtr, tag = "ModDate")
                totalPages = getPageCount()
            }
        }
    }

    private fun recursiveGetBookmark(
        tree: MutableList<Bookmark>,
        bookmarkPtr: Long,
        level: Long,
    ) {
        if (handleAlreadyClosed(isClosed = isClosed)) return
        var levelMutable = level
        val bookmark: Bookmark = Bookmark().apply {
            nativePtr = bookmarkPtr
            title = nativeGetBookmarkTitle(bookmarkPtr = bookmarkPtr)
            pageIndex = nativeGetBookmarkDestIndex(docPtr = nativeDocPtr, bookmarkPtr = bookmarkPtr)
            tree.add(this)
        }
        val child = nativeGetFirstChildBookmark(nativeDocPtr, bookmarkPtr)
        if (child != 0L && levelMutable < MAX_RECURSION) {
            recursiveGetBookmark(bookmark.children, child, levelMutable++)
        }
        val sibling = nativeGetSiblingBookmark(nativeDocPtr, bookmarkPtr)
        if (sibling != 0L && levelMutable < MAX_RECURSION) {
            recursiveGetBookmark(tree, sibling, levelMutable)
        }
    }

    /**
     * Get table of contents (bookmarks) for given document
     * @return the [Bookmark] list
     * @throws IllegalArgumentException if document is closed
     */
    fun getTableOfContents(): List<Bookmark> {
        if (handleAlreadyClosed(isClosed = isClosed)) return emptyList()
        synchronized(lock = PdfiumCore.lock) {
            val topLevel: MutableList<Bookmark> = ArrayList()
            val first = nativeGetFirstChildBookmark(docPtr = nativeDocPtr, bookmarkPtr = 0)
            if (first != 0L) {
                recursiveGetBookmark(tree = topLevel, bookmarkPtr = first, level = 1)
            }
            return topLevel
        }
    }

    fun hasPage(pageIndex: Int): Boolean {
        return pageMap.containsKey(key = pageIndex)
    }

    fun hasTextPage(pageIndex: Int): Boolean {
        return textPageMap.containsKey(key = pageIndex)
    }

    /**
     * Open a text page
     * @param pageIndex the page index
     * @return the opened [PdfTextPage]
     * @throws IllegalArgumentException if document is closed or the page cannot be loaded
     */
    fun openTextPage(pageIndex: Int): PdfTextPage {
        check(value = !isClosed) { "Already closed" }
        synchronized(lock = PdfiumCore.lock) {
            if (textPageMap.containsKey(key = pageIndex)) {
                textPageMap[pageIndex]?.let {
                    it.count++
                    //Log.v(TAG,"from cache openTextPage: pageIndex: ${page.pageIndex}, count: ${it.count}")
                    return PdfTextPage(
                        doc = this,
                        pageIndex = pageIndex,
                        pagePtr = it.pagePtr,
                        pageMap = textPageMap
                    )
                }
            }
            //Log.v(TAG,"openTextPage: pageIndex: ${page.pageIndex}")
            val pagePtr = pageMap[pageIndex]?.pagePtr ?: 0L
            val textPagePtr = nativeLoadTextPage(docPtr = nativeDocPtr, pagePtr = pagePtr)
            textPageMap[pageIndex] = PageCount(pagePtr = textPagePtr, count = 1)
            return PdfTextPage(
                doc = this,
                pageIndex = pageIndex,
                pagePtr = textPagePtr,
                pageMap = textPageMap
            )
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
        if (handleAlreadyClosed(isClosed = isClosed)) return emptyList()
        var textPagesPtr: LongArray
        synchronized(lock = PdfiumCore.lock) {
            textPagesPtr = nativeLoadPages(
                docPtr = nativeDocPtr,
                fromIndex = fromIndex,
                toIndex = toIndex
            )
            return textPagesPtr.mapIndexed { index: Int, pagePtr: Long ->
                PdfTextPage(
                    doc = this,
                    pageIndex = fromIndex + index,
                    pagePtr = pagePtr,
                    pageMap = textPageMap
                )
            }
        }
    }

    /**
     * Save document as a copy
     * @param callback the [PdfWriteCallback] to be called with the data
     * @param flags must be one of [FPDF_INCREMENTAL], [FPDF_NO_INCREMENTAL] or [FPDF_REMOVE_SECURITY]
     * @return true if the document was successfully saved
     * @throws IllegalArgumentException if document is closed
     */
    fun saveAsCopy(
        callback: PdfWriteCallback,
        flags: Int = FPDF_INCREMENTAL
    ): Boolean {
        if (handleAlreadyClosed(isClosed = isClosed)) return false
        return nativeSaveAsCopy(docPtr = nativeDocPtr, callback = callback, flags = flags)
    }

    /**
     * Close the document
     * @throws IllegalArgumentException if document is closed
     */
    override fun close() {
        if (handleAlreadyClosed(isClosed = isClosed)) return
        Log.v(TAG, "PdfDocument.close")
        synchronized(lock = PdfiumCore.lock) {
            isClosed = true
            nativeCloseDocument(docPtr = nativeDocPtr)
            parcelFileDescriptor?.close()
            parcelFileDescriptor = null
        }
    }

    /*
        /**
         * Render page fragment on [Surface].<br></br>
         * @param bufferPtr Surface's buffer on which to render page
         * @param pages The pages to render
         * @param matrices The matrices to map the pages to the surface
         * @param clipRects The rectangles to clip the pages to
         * @param annotation whether render annotation
         * @param textMask whether to render text as image mask - currently ignored
         * @param canvasColor The color to fill the canvas with. Use 0 to not fill the canvas.
         * @param pageBackgroundColor The color for the page background. Use 0 to not fill the background.
         * You almost always want this to be white (the default)
         * @throws IllegalStateException If the page or document is closed
         */

        fun renderPages(
            bufferPtr: Long,
            drawSizeX: Int,
            drawSizeY: Int,
            pages: List<PdfPage>,
            matrices: List<Matrix>,
            clipRects: List<RectF>,
            annotation: Boolean = false,
            canvasColor: Int = 0xFF848484.toInt(),
            pageBackgroundColor: Int = 0xFFFFFFFF.toInt(),
        ) {
            if (handleAlreadyClosed(isClosed = isClosed || pages.any { it.isClosed })) return
            val matrixFloats = matrices.flatMap { matrix ->
                val matrixValues = FloatArray(size = THREE_BY_THREE)
                matrix.getValues(matrixValues)
                listOf(
                    matrixValues[Matrix.MSCALE_X],
                    matrixValues[Matrix.MTRANS_X],
                    matrixValues[Matrix.MTRANS_Y],
                )
            }.toFloatArray()
            val clipFloats = clipRects.flatMap { rect ->
                listOf(
                    rect.left,
                    rect.top,
                    rect.right,
                    rect.bottom,
                )
            }.toFloatArray()
            synchronized(lock = PdfiumCore.lock) {
                nativeRenderPagesWithMatrix(
                    pages = pages.map { it.pagePtr }.toLongArray(),
                    bufferPtr = bufferPtr,
                    drawSizeHor = drawSizeX,
                    drawSizeVer = drawSizeY,
                    matrixFloats = matrixFloats,
                    clipFloats = clipFloats,
                    annotation = annotation,
                    canvasColor = canvasColor,
                    pageBackgroundColor = pageBackgroundColor,
                )
            }
        }

        fun renderPages(
            surface: Surface,
            pages: List<PdfPage>,
            matrices: List<Matrix>,
            clipRects: List<RectF>,
            annotation: Boolean = false,
            canvasColor: Int = 0xFF848484.toInt(),
            pageBackgroundColor: Int = 0xFFFFFFFF.toInt(),
        ): Boolean {
            if (handleAlreadyClosed(isClosed = isClosed || pages.any { it.isClosed })) return false
            val matrixFloats = matrices.flatMap { matrix ->
                val matrixValues = FloatArray(size = THREE_BY_THREE)
                matrix.getValues(matrixValues)
                listOf(
                    matrixValues[Matrix.MSCALE_X],
                    matrixValues[Matrix.MTRANS_X],
                    matrixValues[Matrix.MTRANS_Y],
                )
            }.toFloatArray()
            val clipFloats = clipRects.flatMap { rect ->
                listOf(
                    rect.left,
                    rect.top,
                    rect.right,
                    rect.bottom,
                )
            }.toFloatArray()
            synchronized(lock = PdfiumCore.lock) {
                return nativeRenderPagesSurfaceWithMatrix(
                    pages = pages.map { it.pagePtr }.toLongArray(),
                    surface = surface,
                    matrixFloats = matrixFloats,
                    clipFloats = clipFloats,
                    annotation = annotation,
                    canvasColor = canvasColor,
                    pageBackgroundColor = pageBackgroundColor,
                )
            }
        }
    */

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
        val children: MutableList<Bookmark> = ArrayList(),
        var nativePtr: Long = 0L,
        var pageIndex: Long = 0L,
        var title: String? = null,
    ) {
        fun hasChildren(): Boolean {
            return children.isNotEmpty()
        }
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
    }
}