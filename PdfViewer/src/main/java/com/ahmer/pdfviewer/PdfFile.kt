package com.ahmer.pdfviewer

import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.RectF
import android.util.SparseBooleanArray
import com.ahmer.pdfium.Bookmark
import com.ahmer.pdfium.Link
import com.ahmer.pdfium.Meta
import com.ahmer.pdfium.PdfiumCore
import com.ahmer.pdfium.util.Size
import com.ahmer.pdfium.util.SizeF
import com.ahmer.pdfviewer.exception.PageRenderingException
import com.ahmer.pdfviewer.util.FitPolicy
import com.ahmer.pdfviewer.util.PageSizeCalculator
import com.ahmer.pdfviewer.util.PdfConstants
import java.util.*


class PdfFile(
    private val pdfiumCore: PdfiumCore?, private val pageFitPolicy: FitPolicy, viewSize: Size,
    /**
     * The pages the user want to display in order
     * (ex: 0, 2, 2, 8, 8, 1, 1, 1)
     */
    private var originalUserPages: IntArray?,
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
    private val openedPageQueue: Queue<Int>?

    /**
     * Original page sizes
     */
    private val originalPageSizes: MutableList<Size> = ArrayList<Size>()

    /**
     * Scaled page sizes
     */
    private val pageSizes: MutableList<SizeF> = ArrayList<SizeF>()

    /**
     * Opened pages with indicator whether opening was successful
     */
    private val openedPages = SparseBooleanArray()

    /**
     * Calculated offsets for pages
     */
    private val pageOffsets: MutableList<Float> = ArrayList()

    /**
     * Calculated auto spacing for pages
     */
    private val pageSpacing: MutableList<Float> = ArrayList()
    var pagesCount = 0
        private set

    /**
     * Page with maximum width
     */
    private var originalMaxWidthPageSize: Size = Size(0, 0)

    /**
     * Page with maximum height
     */
    private var originalMaxHeightPageSize: Size = Size(0, 0)

    /**
     * Scaled page with maximum height
     */
    private var maxHeightPageSize: SizeF? = SizeF(0F, 0F)

    /**
     * Scaled page with maximum width
     */
    private var maxWidthPageSize: SizeF? = SizeF(0F, 0F)

    /**
     * Calculated document length (width or height, depending on swipe mode)
     */
    private var documentLength = 0f
    private fun setup(viewSize: Size) {
        if (originalUserPages != null) {
            pagesCount = originalUserPages!!.size
        } else {
            if (pdfiumCore != null) {
                pagesCount = pdfiumCore.pageCount()
            }
        }
        for (i in 0 until pagesCount) {
            val pageSize: Size = pdfiumCore!!.getPageSize(documentPage(i))
            if (pageSize.width > originalMaxWidthPageSize.width) {
                originalMaxWidthPageSize = pageSize
            }
            if (pageSize.height > originalMaxHeightPageSize.height) {
                originalMaxHeightPageSize = pageSize
            }
            originalPageSizes.add(pageSize)
        }
        recalculatePageSizes(viewSize)
    }

    /**
     * Call after view size change to recalculate page sizes, offsets and document length
     *
     * @param viewSize new size of changed view
     */
    fun recalculatePageSizes(viewSize: Size) {
        pageSizes.clear()
        val calculator = PageSizeCalculator(
            pageFitPolicy,
            originalMaxWidthPageSize, originalMaxHeightPageSize, viewSize, fitEachPage
        )
        maxWidthPageSize = calculator.getOptimalMaxWidthPageSize()
        maxHeightPageSize = calculator.getOptimalMaxHeightPageSize()
        for (size in originalPageSizes) {
            pageSizes.add(calculator.calculate(size))
        }
        if (autoSpacing) {
            prepareAutoSpacing(viewSize)
        }
        prepareDocLen()
        preparePagesOffset()
    }

    fun getPageSize(pageIndex: Int): SizeF {
        val docPage = documentPage(pageIndex)
        return if (docPage < 0) {
            SizeF(0F, 0F)
        } else pageSizes[pageIndex]
    }

    fun getScaledPageSize(pageIndex: Int, zoom: Float): SizeF {
        val size: SizeF = getPageSize(pageIndex)
        return SizeF(size.width * zoom, size.height * zoom)
    }

    /**
     * get page size with biggest dimension (width in vertical mode and height in horizontal mode)
     *
     * @return size of page
     */
    val maxPageSize: SizeF?
        get() = if (isVertical) maxWidthPageSize else maxHeightPageSize
    val maxPageWidth: Float?
        get() = maxPageSize?.width
    val maxPageHeight: Float?
        get() = maxPageSize?.height

    private fun prepareAutoSpacing(viewSize: Size) {
        pageSpacing.clear()
        for (i in 0 until pagesCount) {
            val pageSize: SizeF = pageSizes[i]
            var spacing: Float =
                0F.coerceAtLeast(if (isVertical) viewSize.height - pageSize.height else viewSize.width - pageSize.width)
            if (i < pagesCount - 1) {
                spacing += spacingPx.toFloat()
            }
            pageSpacing.add(spacing)
        }
    }

    private fun prepareDocLen() {
        var length = 0f
        for (i in 0 until pagesCount) {
            val pageSize: SizeF = pageSizes[i]
            length += if (isVertical) pageSize.height else pageSize.width
            if (autoSpacing) {
                length += pageSpacing[i]
            } else if (i < pagesCount - 1) {
                length += spacingPx.toFloat()
            }
        }
        documentLength = length
    }

    private fun preparePagesOffset() {
        pageOffsets.clear()
        var offset = 0f
        for (i in 0 until pagesCount) {
            val pageSize: SizeF = pageSizes[i]
            val size: Float = if (isVertical) pageSize.height else pageSize.width
            if (autoSpacing) {
                offset += pageSpacing[i] / 2f
                if (i == 0) {
                    offset -= spacingPx / 2f
                } else if (i == pagesCount - 1) {
                    offset += spacingPx / 2f
                }
                pageOffsets.add(offset)
                offset += size + pageSpacing[i] / 2f
            } else {
                pageOffsets.add(offset)
                offset += size + spacingPx
            }
        }
    }

    fun getDocLen(zoom: Float): Float {
        return documentLength * zoom
    }

    /**
     * Get the page's height if swiping vertical, or width if swiping horizontal.
     */
    fun getPageLength(pageIndex: Int, zoom: Float): Float {
        val size: SizeF = getPageSize(pageIndex)
        return (if (isVertical) size.height else size.width) * zoom
    }

    fun getPageSpacing(pageIndex: Int, zoom: Float): Float {
        val spacing = if (autoSpacing) pageSpacing[pageIndex] else spacingPx.toFloat()
        return spacing * zoom
    }

    /**
     * Get primary page offset, that is Y for vertical scroll and X for horizontal scroll
     */
    fun getPageOffset(pageIndex: Int, zoom: Float): Float {
        val docPage = documentPage(pageIndex)
        return if (docPage < 0) {
            0f
        } else pageOffsets[pageIndex] * zoom
    }

    /**
     * Get secondary page offset, that is X for vertical scroll and Y for horizontal scroll
     */
    fun getSecondaryPageOffset(pageIndex: Int, zoom: Float): Float {
        val pageSize: SizeF = getPageSize(pageIndex)
        val maxHeight: Float? = maxPageHeight
        val maxWidth: Float? = maxPageWidth
        var total = 0f
        if (isVertical) {
            if (maxWidth != null) {
                total = zoom * (maxWidth.minus(pageSize.width) / 2) //
            }
        } else {
            if (maxHeight != null) {
                total = zoom * (maxHeight.minus(pageSize.height) / 2) //y
            }
        }
        return total
    }

    fun getPageAtOffset(offset: Float, zoom: Float): Int {
        var currentPage = 0
        for (i in 0 until pagesCount) {
            val off = pageOffsets[i] * zoom - getPageSpacing(i, zoom) / 2f
            if (off >= offset) {
                break
            }
            currentPage++
        }
        return if (--currentPage >= 0) currentPage else 0
    }

    @Throws(PageRenderingException::class)
    fun openPage(pageIndex: Int): Boolean {
        val docPage = documentPage(pageIndex)
        if (docPage < 0) {
            return false
        }
        synchronized(lock) {
            if (openedPages.indexOfKey(docPage) < 0) {
                try {
                    pdfiumCore!!.openPage(docPage)
                    openedPages.put(docPage, true)
                    /*
                    * Memory management
                    * Fix memory leak https://github.com/barteksc/AndroidPdfViewer/issues/495
                    * */
                    openedPageQueue!!.add(pageIndex)
                    if (openedPageQueue.size > PdfConstants.MAX_PAGES) {
                        val oldPage: Int = openedPageQueue.poll()!!
                        pdfiumCore.closePage(oldPage)
                        openedPages.delete(oldPage)
                    }
                    return true
                } catch (e: Exception) {
                    openedPages.put(docPage, false)
                    throw PageRenderingException(pageIndex, e)
                }
            }
            return false
        }
    }

    fun pageHasError(pageIndex: Int): Boolean {
        val docPage = documentPage(pageIndex)
        return !openedPages[docPage, false]
    }

    fun renderPageBitmap(
        bitmap: Bitmap?,
        pageIndex: Int,
        bounds: Rect,
        annotationRendering: Boolean
    ) {
        val docPage = documentPage(pageIndex)
        pdfiumCore!!.renderPageBitmap(
            bitmap!!, docPage, bounds.left, bounds.top, bounds.width(),
            bounds.height(), annotationRendering
        )
    }

    val metaData: Meta?
        get() = pdfiumCore?.documentMeta
    val bookmarks: List<Bookmark>
        get() = pdfiumCore?.tableOfContents() ?: ArrayList()


    fun getPageLinks(pageIndex: Int): List<Link> {
        val docPage = documentPage(pageIndex)
        return pdfiumCore!!.getPageLinks(docPage)
    }

    fun mapRectToDevice(
        pageIndex: Int,
        startX: Int,
        startY: Int,
        sizeX: Int,
        sizeY: Int,
        rect: RectF?
    ): RectF {
        val docPage = documentPage(pageIndex)
        return pdfiumCore!!.mapRectToDevice(docPage, startX, startY, sizeX, sizeY, 0, rect!!)
    }

    fun dispose() {
        pdfiumCore?.closeDocument()
        originalUserPages = null
    }

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
        if (originalUserPages != null) {
            if (userPage >= originalUserPages!!.size) {
                return originalUserPages!!.size - 1
            }
        } else {
            if (userPage >= pagesCount) {
                return pagesCount - 1
            }
        }
        return userPage
    }

    fun documentPage(userPage: Int): Int {
        var documentPage = userPage
        if (originalUserPages != null) {
            documentPage = if (userPage < 0 || userPage >= originalUserPages!!.size) {
                return -1
            } else {
                originalUserPages!![userPage]
            }
        }
        return if (documentPage < 0 || userPage >= pagesCount) {
            -1
        } else documentPage
    }

    companion object {
        private val lock = Any()
    }

    init {
        openedPageQueue = LinkedList()
        setup(viewSize)
    }
}