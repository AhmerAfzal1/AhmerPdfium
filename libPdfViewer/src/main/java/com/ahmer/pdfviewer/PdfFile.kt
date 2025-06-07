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
    val pdfDocument: PdfDocument,
    private val pdfiumCore: PdfiumCore,
    private val fitPolicy: FitPolicy,
    private val isAutoSpacing: Boolean,
    private val isFitEachPage: Boolean,
    private val isVertical: Boolean,
    private val spacingPixels: Int,
    private var userPages: IntArray = intArrayOf()
) {
    private val openedPages: SparseBooleanArray = SparseBooleanArray()
    private val originalPageSizes: MutableList<Size> = mutableListOf()
    private val pageOffsets: MutableList<Float> = mutableListOf()
    private val pageSpacing: MutableList<Float> = mutableListOf()
    private val scaledPageSizes: MutableList<SizeF> = mutableListOf()

    private var documentLength: Float = 0f
    private var maxHeightPageSize: SizeF = SizeF(width = 0f, height = 0f)
    private var maxWidthPageSize: SizeF = SizeF(width = 0f, height = 0f)
    private var originalMaxHeightPageSize: Size = Size(width = 0, height = 0)
    private var originalMaxWidthPageSize: Size = Size(width = 0, height = 0)

    val maxPageHeight: Float get() = (if (isVertical) maxWidthPageSize else maxHeightPageSize).height
    val maxPageWidth: Float get() = (if (isVertical) maxWidthPageSize else maxHeightPageSize).width
    val pagesCount: Int get() = if (userPages.isNotEmpty()) userPages.size else totalPages()

    fun ensureValidPageNumber(userPage: Int): Int {
        if (userPage <= 0) return 0

        return if (userPages.isNotEmpty()) {
            minOf(a = userPage, b = userPages.size - 1)
        } else {
            minOf(a = userPage, b = pagesCount - 1)
        }
    }

    fun documentPage(userPage: Int): Int {
        if (userPage < 0 || (userPages.isNotEmpty() && userPage >= userPages.size)) return -1
        val docPage = if (userPages.isNotEmpty()) userPages[userPage] else userPage
        return if (docPage < 0 || userPage >= pagesCount) -1 else docPage
    }

    fun getPageAtOffset(offset: Float, zoom: Float): Int {
        var currentPage = 0
        val pageSpacing: Float = getPageSpacing(pageIndex = currentPage, zoom = zoom)
        while (currentPage < pagesCount) {
            val pageOffset = pageOffsets[currentPage] * zoom - pageSpacing / 2f
            if (pageOffset >= offset) break
            currentPage++
        }
        return maxOf(a = currentPage - 1, b = 0)
    }

    fun getPageLength(pageIndex: Int, zoom: Float): Float {
        val size = getPageSize(pageIndex = pageIndex)
        return (if (isVertical) size.height else size.width) * zoom
    }

    fun getPageLinks(
        pageIndex: Int,
        size: SizeF,
        posX: Float,
        posY: Float
    ): List<PdfDocument.Link> {
        return pdfiumCore.getPageLinks(pageIndex = pageIndex, size = size, posX = posX, posY = posY)
    }

    fun getPageOffset(pageIndex: Int, zoom: Float): Float {
        return if (documentPage(userPage = pageIndex) < 0) 0f else pageOffsets[pageIndex] * zoom
    }

    fun getPageSize(pageIndex: Int): SizeF {
        val size = SizeF(width = 0f, height = 0f)
        return if (documentPage(userPage = pageIndex) < 0) size else scaledPageSizes[pageIndex]
    }

    fun getPageSizeNative(pageIndex: Int): Size = pdfiumCore.getPageSize(pageIndex = pageIndex)

    fun getPageSpacing(pageIndex: Int, zoom: Float): Float {
        return (if (isAutoSpacing) pageSpacing[pageIndex] else spacingPixels.toFloat()) * zoom
    }

    fun getScaledPageSize(pageIndex: Int, zoom: Float): SizeF {
        val size: SizeF = getPageSize(pageIndex = pageIndex)
        return SizeF(width = size.width * zoom, height = size.height * zoom)
    }

    fun getSecondaryPageOffset(pageIndex: Int, zoom: Float): Float {
        val size: SizeF = getPageSize(pageIndex = pageIndex)
        val new: Float = if (isVertical) maxPageWidth - size.width else maxPageHeight - size.height
        return zoom * new / 2f
    }

    fun mapRectToDevice(
        pageIndex: Int,
        startX: Int,
        startY: Int,
        sizeX: Int,
        sizeY: Int,
        rect: RectF
    ): RectF {
        return pdfiumCore.mapRectToDevice(
            pageIndex = pageIndex,
            startX = startX,
            startY = startY,
            sizeX = sizeX,
            sizeY = sizeY,
            rotate = 0,
            coords = rect
        )
    }

    fun renderPageBitmap(
        pageIndex: Int,
        bitmap: Bitmap,
        bounds: Rect,
        isAnnotation: Boolean
    ) {
        pdfiumCore.renderPageBitmap(
            pageIndex = pageIndex,
            bitmap = bitmap,
            startX = bounds.left,
            startY = bounds.top,
            drawSizeX = bounds.width(),
            drawSizeY = bounds.height(),
            annotation = isAnnotation
        )
    }

    @Throws(PageRenderingException::class)
    fun openPage(pageIndex: Int): Boolean {
        synchronized(lock = lock) {
            return documentPage(userPage = pageIndex).takeIf { it >= 0 }?.let { docPage ->
                if (openedPages.indexOfKey(docPage) < 0) {
                    try {
                        pdfDocument.openPage(pageIndex = docPage)
                        openedPages.put(docPage, true)
                        true
                    } catch (e: Exception) {
                        openedPages.put(docPage, false)
                        throw PageRenderingException(page = pageIndex, cause = e)
                    }
                } else false
            } ?: false
        }
    }

    fun recalculatePageSizes(viewSize: Size) {
        scaledPageSizes.clear()
        val calculator = PageSizeCalculator(
            fitPolicy = fitPolicy,
            originalWidth = originalMaxWidthPageSize,
            originalHeight = originalMaxHeightPageSize,
            viewSize = viewSize,
            fitEachPage = isFitEachPage
        )
        maxWidthPageSize = calculator.optimalWidth
        maxHeightPageSize = calculator.optimalHeight
        scaledPageSizes.addAll(originalPageSizes.map { calculator.calculate(pageSize = it) })

        if (isAutoSpacing) prepareAutoSpacing(viewSize = viewSize)
        prepareDocLen()
        preparePagesOffset()
    }

    fun bookmarks(): List<PdfDocument.Bookmark> = pdfDocument.bookmarks

    fun docLength(zoom: Float): Float = documentLength * zoom

    fun getPageRotation(pageIndex: Int): Int = pdfiumCore.getPageRotation(pageIndex = pageIndex)

    fun metaData(): PdfDocument.Meta = pdfDocument.metaData

    fun pageHasError(page: Int): Boolean = !openedPages[documentPage(userPage = page), false]

    fun totalPages(): Int = pdfDocument.totalPages()

    private fun prepareAutoSpacing(viewSize: Size) {
        pageSpacing.clear()
        (0 until pagesCount).forEach { i ->
            val pageSize: SizeF = scaledPageSizes[i]
            val baseSpacing: Float = when {
                isVertical -> viewSize.height - pageSize.height
                else -> viewSize.width - pageSize.width
            }.coerceAtLeast(minimumValue = 0f)

            val finalSpacing = if (i < pagesCount - 1) baseSpacing + spacingPixels else baseSpacing
            pageSpacing.add(finalSpacing)
        }
    }

    private fun prepareDocLen() {
        documentLength = (0 until pagesCount).fold(initial = 0f) { acc, i ->
            val pageSize: SizeF = scaledPageSizes[i]
            var length: Float = if (isVertical) pageSize.height else pageSize.width
            length += when {
                isAutoSpacing -> pageSpacing[i]
                i < pagesCount - 1 -> spacingPixels.toFloat()
                else -> 0f
            }
            acc + length
        }
    }

    private fun preparePagesOffset() {
        pageOffsets.clear()
        var offset = 0f

        (0 until pagesCount).forEach { i ->
            val pageSize: SizeF = scaledPageSizes[i]
            val size: Float = if (isVertical) pageSize.height else pageSize.width

            if (isAutoSpacing) {
                offset += pageSpacing[i] / 2f
                when (i) {
                    0 -> offset -= spacingPixels / 2f
                    pagesCount - 1 -> offset += spacingPixels / 2f
                }
                pageOffsets.add(offset)
                offset += size + pageSpacing[i] / 2f
            } else {
                pageOffsets.add(offset)
                offset += size + spacingPixels
            }
        }
    }

    private fun setup(viewSize: Size) {
        (0 until pagesCount).forEach { i ->
            getPageSizeNative(pageIndex = i).let { pageSize ->
                if (pageSize.width > originalMaxWidthPageSize.width) {
                    originalMaxWidthPageSize = pageSize
                }
                if (pageSize.height > originalMaxHeightPageSize.height) {
                    originalMaxHeightPageSize = pageSize
                }
                originalPageSizes.add(pageSize)
            }
        }
        recalculatePageSizes(viewSize = viewSize)
    }

    fun dispose() {
        pdfDocument.close()
        userPages = intArrayOf()
    }

    companion object {
        private val lock = Any()

        fun create(
            pdfDocument: PdfDocument,
            pdfiumCore: PdfiumCore,
            fitPolicy: FitPolicy,
            isAutoSpacing: Boolean,
            isFitEachPage: Boolean,
            isVertical: Boolean,
            spacingPixels: Int,
            userPages: IntArray = intArrayOf(),
            size: Size,
        ): PdfFile {
            val pdfFile = PdfFile(
                pdfDocument = pdfDocument,
                pdfiumCore = pdfiumCore,
                fitPolicy = fitPolicy,
                isAutoSpacing = isAutoSpacing,
                isFitEachPage = isFitEachPage,
                isVertical = isVertical,
                spacingPixels = spacingPixels,
                userPages = userPages
            ).apply { setup(viewSize = size) }
            return pdfFile
        }
    }
}