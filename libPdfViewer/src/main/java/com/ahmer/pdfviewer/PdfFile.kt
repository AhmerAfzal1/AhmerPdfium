package com.ahmer.pdfviewer

import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.RectF
import android.util.SparseBooleanArray
import com.ahmer.pdfium.PdfDocument
import com.ahmer.pdfium.PdfiumCore
import com.ahmer.pdfium.util.Size
import com.ahmer.pdfium.util.SizeF
import com.ahmer.pdfviewer.exception.PageRenderingException
import com.ahmer.pdfviewer.util.FitPolicy
import com.ahmer.pdfviewer.util.PageSizeCalculator

class PdfFile(
    private val pdfDocument: PdfDocument,
    private val pdfiumCore: PdfiumCore,
    private val fitPolicy: FitPolicy,
    size: Size,
    /**
     * The pages the user want to display in order
     * (ex: 0, 2, 2, 8, 8, 1, 1, 1)
     */
    private var userPages: IntArray = intArrayOf(),
    /**
     * True if scrolling is vertical, else it's horizontal
     */
    private val isVertical: Boolean,
    /**
     * Fixed spacing between pages in pixels
     */
    private val spacingPx: Int,
    /**
     * Calculate spacing automatically so each page fits on it's own in the center of the view
     */
    private val autoSpacing: Boolean,
    /**
     * True if every page should fit separately according to the FitPolicy,
     * else the largest page fits and other pages scale relatively
     */
    private val fitEachPage: Boolean
) {

    /**
     * Opened pages with indicator whether opening was successful
     */
    private val mOpenedPages = SparseBooleanArray()

    /**
     * Original page sizes
     */
    private val mOriginalPageSizes: MutableList<Size> = ArrayList()

    /**
     * Calculated offsets for pages
     */
    private val mPageOffsets: MutableList<Float> = ArrayList()

    /**
     * Calculated auto spacing for pages
     */
    private val mPageSpacing: MutableList<Float> = ArrayList()

    /**
     * Scaled page sizes
     */
    private val mScaledPageSizes: MutableList<SizeF> = ArrayList()

    /**
     * Calculated document length (width or height, depending on swipe mode)
     */
    private var mDocumentLength = 0f

    /**
     * Scaled page with maximum height
     */
    private var mMaxHeightPageSize: SizeF = SizeF(0f, 0f)

    /**
     * Scaled page with maximum width
     */
    private var mMaxWidthPageSize: SizeF = SizeF(0f, 0f)

    /**
     * Page with maximum width
     */
    private var mOriginalMaxWidthPageSize: Size = Size(0, 0)

    /**
     * Page with maximum height
     */
    private var mOriginalMaxHeightPageSize: Size = Size(0, 0)

    /**
     * Get page size with biggest dimension (width in vertical mode and height in horizontal mode)
     *
     * @return size of page
     */
    private val maxPageSize: SizeF
        get() = if (isVertical) mMaxWidthPageSize else mMaxHeightPageSize

    val maxPageHeight: Float
        get() = maxPageSize.height

    val maxPageWidth: Float
        get() = maxPageSize.width

    var pagesCount = 0
        private set

    /**
     * Given the UserPage number, this method restrict it
     * to be sure it's an existing page. It takes care of
     * using the user defined pages if any.
     *
     * @param userPage A page number.
     * @return A restricted valid page number (example : -2 => 0)
     */
    fun determineValidPageNumberFrom(userPage: Int): Int {
        if (userPage <= 0) {
            return 0
        }
        if (userPages.isNotEmpty()) {
            if (userPage >= userPages.size) return userPages.size - 1
        } else {
            if (userPage >= pagesCount) return pagesCount - 1
        }
        return userPage
    }

    /**
     * Release native resources and opened file.
     */
    fun dispose() {
        pdfDocument.close()
        userPages = intArrayOf()
    }

    fun documentPage(userPage: Int): Int {
        var mDocPage = userPage
        if (userPages.isNotEmpty()) {
            mDocPage =
                if (userPage < 0 || userPage >= userPages.size) return -1 else userPages[userPage]
        }
        return if (mDocPage < 0 || userPage >= pagesCount) -1 else mDocPage
    }

    /**
     * Get table of contents (bookmarks) for given document.
     */
    fun bookmarks(): List<PdfDocument.Bookmark> {
        return pdfDocument.tableOfContents
    }

    fun getDocLen(zoom: Float): Float {
        return mDocumentLength * zoom
    }

    /**
     * Get metadata for given document.
     */
    fun metaData(): PdfDocument.Meta {
        return pdfDocument.documentMeta
    }

    fun getPageAtOffset(offset: Float, zoom: Float): Int {
        var mCurrentPage = 0
        for (i in 0 until pagesCount) {
            val mOffset = mPageOffsets[i] * zoom - getPageSpacing(i, zoom) / 2f
            if (mOffset >= offset) break
            mCurrentPage++
        }
        return if (--mCurrentPage >= 0) mCurrentPage else 0
    }

    /**
     * Get the page's height if swiping vertical, or width if swiping horizontal.
     */
    fun getPageLength(pageIndex: Int, zoom: Float): Float {
        val mSize: SizeF = getPageSize(pageIndex)
        return (if (isVertical) mSize.height else mSize.width) * zoom
    }

    /**
     * @return All links from given page.
     */
    fun getPageLinks(
        pageIndex: Int,
        mSize: SizeF,
        posX: Float,
        posY: Float
    ): List<PdfDocument.Link> {
        return pdfiumCore.getPageLinks(pageIndex, mSize, posX, posY)
    }

    /**
     * Get primary page offset, that is Y for vertical scroll and X for horizontal scroll
     */
    fun getPageOffset(pageIndex: Int, zoom: Float): Float {
        return if (documentPage(pageIndex) < 0) 0f else mPageOffsets[pageIndex] * zoom
    }

    /**
     * Get page rotation in degrees.
     */
    fun getPageRotation(pageIndex: Int): Int {
        return pdfiumCore.getPageRotation(pageIndex)
    }

    /**
     * Get page size.
     */
    fun getPageSize(pageIndex: Int): SizeF {
        return if (documentPage(pageIndex) < 0) SizeF(0f, 0f) else mScaledPageSizes[pageIndex]
    }

    /**
     * Get native size of page in pixels.
     */
    fun getPageSizeNative(pageIndex: Int): Size {
        return pdfiumCore.getPageSize(pageIndex)
    }

    fun getPageSpacing(pageIndex: Int, zoom: Float): Float {
        return (if (autoSpacing) mPageSpacing[pageIndex] else spacingPx.toFloat()) * zoom
    }

    fun getScaledPageSize(pageIndex: Int, zoom: Float): SizeF {
        val mSize: SizeF = getPageSize(pageIndex)
        return SizeF(mSize.width * zoom, mSize.height * zoom)
    }

    /**
     * Get secondary page offset, that is X for vertical scroll and Y for horizontal scroll
     */
    fun getSecondaryPageOffset(pageIndex: Int, zoom: Float): Float {
        val mPageSize: SizeF = getPageSize(pageIndex)
        return if (isVertical) {
            zoom * (maxPageWidth - mPageSize.width) / 2 //x
        } else {
            zoom * (maxPageHeight - mPageSize.height) / 2 //y
        }
    }

    /**
     * Get total number of pages in document
     */
    fun totalPagesCount(): Int {
        return pdfDocument.pageCount
    }

    /**
     * @return mapped coordinates
     * @see PdfiumCore.mapPageCoordsToDevice
     */
    fun mapRectToDevice(
        pageIndex: Int,
        startX: Int,
        startY: Int,
        sizeX: Int,
        sizeY: Int,
        rect: RectF
    ): RectF {
        return pdfiumCore.mapRectToDevice(pageIndex, startX, startY, sizeX, sizeY, 0, rect)
    }

    @Throws(PageRenderingException::class)
    fun openPage(pageIndex: Int): Boolean {
        val mDocPage = documentPage(pageIndex)
        if (mDocPage < 0) return false
        synchronized(lock = lock) {
            if (mOpenedPages.indexOfKey(mDocPage) < 0) {
                try {
                    pdfDocument.openPage(mDocPage)
                    mOpenedPages.put(mDocPage, true)
                    return true
                } catch (e: Exception) {
                    mOpenedPages.put(mDocPage, false)
                    throw PageRenderingException(pageIndex, e)
                }
            }
            return false
        }
    }

    fun pageHasError(pageIndex: Int): Boolean {
        return !mOpenedPages[documentPage(pageIndex), false]
    }

    private fun prepareAutoSpacing(viewSize: Size) {
        mPageSpacing.clear()
        for (i in 0 until pagesCount) {
            val mPageSize: SizeF = mScaledPageSizes[i]
            var mSpacing: Float =
                0f.coerceAtLeast(if (isVertical) viewSize.height - mPageSize.height else viewSize.width - mPageSize.width)
            if (i < pagesCount - 1) mSpacing += spacingPx.toFloat()
            mPageSpacing.add(mSpacing)
        }
    }

    private fun prepareDocLen() {
        var mLength = 0f
        for (i in 0 until pagesCount) {
            val mPageSize: SizeF = mScaledPageSizes[i]
            mLength += if (isVertical) mPageSize.height else mPageSize.width
            if (autoSpacing) mLength += mPageSpacing[i] else if (i < pagesCount - 1) mLength += spacingPx.toFloat()
        }
        mDocumentLength = mLength
    }

    private fun preparePagesOffset() {
        mPageOffsets.clear()
        var mOffset = 0f
        for (i in 0 until pagesCount) {
            val mPageSize: SizeF = mScaledPageSizes[i]
            val mSize: Float = if (isVertical) mPageSize.height else mPageSize.width
            if (autoSpacing) {
                mOffset += mPageSpacing[i] / 2f
                if (i == 0) mOffset -= spacingPx / 2f else if (i == pagesCount - 1) mOffset += spacingPx / 2f
                mPageOffsets.add(mOffset)
                mOffset += mSize + mPageSpacing[i] / 2f
            } else {
                mPageOffsets.add(mOffset)
                mOffset += mSize + spacingPx
            }
        }
    }

    /**
     * Call after view size change to recalculate page sizes, offsets and document length
     * @param viewSize new size of changed view
     */
    fun recalculatePageSizes(viewSize: Size) {
        mScaledPageSizes.clear()
        val calculator = PageSizeCalculator(
            fitPolicy, mOriginalMaxWidthPageSize, mOriginalMaxHeightPageSize, viewSize, fitEachPage
        )
        mMaxWidthPageSize = calculator.mOptimalWidth!!
        mMaxHeightPageSize = calculator.mOptimalHeight!!
        for (size in mOriginalPageSizes) mScaledPageSizes.add(calculator.calculate(size))
        if (autoSpacing) prepareAutoSpacing(viewSize)
        prepareDocLen()
        preparePagesOffset()
    }

    /**
     * Render page fragment on [Bitmap]. This method allows to render annotations.
     * Page must be opened before rendering.
     *
     * @see PdfiumCore.renderPageBitmap
     */
    fun renderPageBitmap(
        pageIndex: Int,
        bitmap: Bitmap,
        bounds: Rect,
        annotation: Boolean
    ) {
        pdfiumCore.renderPageBitmap(
            pageIndex, bitmap, bounds.left, bounds.top, bounds.width(), bounds.height(), annotation
        )
    }

    private fun setup(viewSize: Size) {
        pagesCount = if (userPages.isNotEmpty()) userPages.size else totalPagesCount()
        for (i in 0 until pagesCount) {
            val pageSize: Size = getPageSizeNative(i)
            if (pageSize.width > mOriginalMaxWidthPageSize.width) {
                mOriginalMaxWidthPageSize = pageSize
            }
            if (pageSize.height > mOriginalMaxHeightPageSize.height) {
                mOriginalMaxHeightPageSize = pageSize
            }
            mOriginalPageSizes.add(pageSize)
        }
        recalculatePageSizes(viewSize)
    }

    companion object {
        private val lock = Any()
        fun create(
            pdfDocument: PdfDocument,
            pdfiumCore: PdfiumCore,
            fitPolicy: FitPolicy,
            size: Size,
            userPages: IntArray = intArrayOf(),
            isVertical: Boolean,
            spacingPx: Int,
            autoSpacing: Boolean,
            fitEachPage: Boolean
        ): PdfFile {
            val pdfFile = PdfFile(
                pdfDocument, pdfiumCore, fitPolicy, size, userPages,
                isVertical, spacingPx, autoSpacing, fitEachPage
            )
            pdfFile.setup(size)
            return pdfFile
        }
    }
}