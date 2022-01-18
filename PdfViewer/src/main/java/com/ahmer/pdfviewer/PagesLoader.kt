package com.ahmer.pdfviewer

import android.graphics.RectF
import com.ahmer.pdfium.util.SizeF
import com.ahmer.pdfviewer.util.MathUtils
import com.ahmer.pdfviewer.util.PdfConstants
import com.ahmer.pdfviewer.util.PdfUtils
import java.util.*
import kotlin.math.abs

internal class PagesLoader(private val pdfView: PDFView) {
    private var cacheOrder = 0
    private var xOffset = 0f
    private var yOffset = 0f
    private var pageRelativePartWidth = 0f
    private var pageRelativePartHeight = 0f
    private var partRenderWidth = 0f
    private var partRenderHeight = 0f
    private val thumbnailRect = RectF(0f, 0f, 1f, 1f)
    private val preloadOffset: Int = PdfUtils.getDP(pdfView.context, PdfConstants.PRELOAD_OFFSET)

    private class Holder {
        var row = 0
        var col = 0
        override fun toString(): String {
            return "Holder{row=$row, col=$col}"
        }
    }

    private class RenderRange() {
        var page = 0
        var gridSize: GridSize = GridSize()
        var leftTop: Holder = Holder()
        var rightBottom: Holder = Holder()
        override fun toString(): String {
            return "RenderRange{page=$page, gridSize=$gridSize, leftTop=$leftTop, rightBottom=$rightBottom}"
        }

    }

    private class GridSize {
        var rows = 0
        var cols = 0
        override fun toString(): String {
            return "GridSize{rows=$rows, cols=$cols}"
        }
    }

    private fun getPageColsRows(
        grid: GridSize,
        pageIndex: Int
    ) {
        val size = pdfView.pdfFile?.getPageSize(pageIndex) ?: SizeF(0f, 0f)
        val ratioX = 1f / size.width
        val ratioY = 1f / size.height
        val partHeight = PdfConstants.PART_SIZE * ratioY / pdfView.zoom
        val partWidth = PdfConstants.PART_SIZE * ratioX / pdfView.zoom
        grid.rows = MathUtils.ceil(1f / partHeight)
        grid.cols = MathUtils.ceil(1f / partWidth)
    }

    private fun calculatePartSize(grid: GridSize) {
        pageRelativePartWidth = 1f / grid.cols.toFloat()
        pageRelativePartHeight = 1f / grid.rows.toFloat()
        partRenderWidth = PdfConstants.PART_SIZE / pageRelativePartWidth
        partRenderHeight = PdfConstants.PART_SIZE / pageRelativePartHeight
    }

    /**
     * calculate the render range of each page
     */
    private fun getRenderRangeList(
        firstXOffset: Float, firstYOffset: Float, lastXOffset: Float, lastYOffset: Float
    ): List<RenderRange> {
        val fixedFirstXOffset = -MathUtils.max(firstXOffset, 0f)
        val fixedFirstYOffset = -MathUtils.max(firstYOffset, 0f)
        val fixedLastXOffset = -MathUtils.max(lastXOffset, 0f)
        val fixedLastYOffset = -MathUtils.max(lastYOffset, 0f)
        val offsetFirst = if (pdfView.isSwipeVertical) fixedFirstYOffset else fixedFirstXOffset
        val offsetLast = if (pdfView.isSwipeVertical) fixedLastYOffset else fixedLastXOffset
        val firstPage = pdfView.pdfFile?.getPageAtOffset(offsetFirst, pdfView.zoom) ?: 0
        val lastPage = pdfView.pdfFile?.getPageAtOffset(offsetLast, pdfView.zoom) ?: 0
        val pageCount = lastPage - firstPage + 1
        val renderRanges: MutableList<RenderRange> = LinkedList()
        for (page in firstPage..lastPage) {
            val range = RenderRange()
            range.page = page
            var pageFirstXOffset: Float
            var pageFirstYOffset: Float
            var pageLastXOffset: Float
            var pageLastYOffset: Float
            if (page == firstPage) {
                pageFirstXOffset = fixedFirstXOffset
                pageFirstYOffset = fixedFirstYOffset
                if (pageCount == 1) {
                    pageLastXOffset = fixedLastXOffset
                    pageLastYOffset = fixedLastYOffset
                } else {
                    val pageOffset = pdfView.pdfFile?.getPageOffset(page, pdfView.zoom) ?: 0f
                    val pageSize = pdfView.pdfFile?.getScaledPageSize(page, pdfView.zoom)
                    if (pdfView.isSwipeVertical) {
                        pageLastXOffset = fixedLastXOffset
                        pageLastYOffset = pageOffset + (pageSize?.height ?: 0f)
                    } else {
                        pageLastYOffset = fixedLastYOffset
                        pageLastXOffset = pageOffset + (pageSize?.width ?: 0f)
                    }
                }
            } else if (page == lastPage) {
                val pageOffset = pdfView.pdfFile?.getPageOffset(page, pdfView.zoom) ?: 0f
                if (pdfView.isSwipeVertical) {
                    pageFirstXOffset = fixedFirstXOffset
                    pageFirstYOffset = pageOffset
                } else {
                    pageFirstYOffset = fixedFirstYOffset
                    pageFirstXOffset = pageOffset
                }
                pageLastXOffset = fixedLastXOffset
                pageLastYOffset = fixedLastYOffset
            } else {
                val pageOffset = pdfView.pdfFile?.getPageOffset(page, pdfView.zoom) ?: 0f
                val pageSize = pdfView.pdfFile?.getScaledPageSize(page, pdfView.zoom)
                if (pdfView.isSwipeVertical) {
                    pageFirstXOffset = fixedFirstXOffset
                    pageFirstYOffset = pageOffset
                    pageLastXOffset = fixedLastXOffset
                    pageLastYOffset = pageOffset + (pageSize?.height ?: 0f)
                } else {
                    pageFirstXOffset = pageOffset
                    pageFirstYOffset = fixedFirstYOffset
                    pageLastXOffset = pageOffset + (pageSize?.width ?: 0f)
                    pageLastYOffset = fixedLastYOffset
                }
            }
            // Get the page's grid size that rows and cols
            getPageColsRows(range.gridSize, range.page)
            val scaledPageSize =
                pdfView.pdfFile?.getScaledPageSize(range.page, pdfView.zoom) ?: SizeF(0f, 0f)
            val rowHeight = scaledPageSize.height / range.gridSize.rows
            val colWidth = scaledPageSize.width / range.gridSize.cols

            // Get the page offset int the whole file
            // ---------------------------------------
            // |            |           |            |
            // |<--offset-->|   (page)  |<--offset-->|
            // |            |           |            |
            // |            |           |            |
            // ---------------------------------------
            val secondaryOffset = pdfView.pdfFile?.getSecondaryPageOffset(page, pdfView.zoom) ?: 0f

            // calculate the row,col of the point in the leftTop and rightBottom
            if (pdfView.isSwipeVertical) {
                range.leftTop.row = MathUtils.floor(
                    abs(
                        pageFirstYOffset - (pdfView.pdfFile?.getPageOffset(
                            range.page,
                            pdfView.zoom
                        ) ?: 0f)
                    ) / rowHeight
                )
                range.leftTop.col = MathUtils.floor(
                    MathUtils.min(
                        pageFirstXOffset - secondaryOffset,
                        0f
                    ) / colWidth
                )
                range.rightBottom.row = MathUtils.ceil(
                    abs(
                        pageLastYOffset - (pdfView.pdfFile?.getPageOffset(
                            range.page,
                            pdfView.zoom
                        ) ?: 0f)
                    ) / rowHeight
                )
                range.rightBottom.col = MathUtils.floor(
                    MathUtils.min(
                        pageLastXOffset - secondaryOffset,
                        0f
                    ) / colWidth
                )
            } else {
                range.leftTop.col = MathUtils.floor(
                    abs(
                        pageFirstXOffset - (pdfView.pdfFile?.getPageOffset(
                            range.page,
                            pdfView.zoom
                        ) ?: 0f)
                    ) / colWidth
                )
                range.leftTop.row = MathUtils.floor(
                    MathUtils.min(
                        pageFirstYOffset - secondaryOffset,
                        0f
                    ) / rowHeight
                )
                range.rightBottom.col = MathUtils.floor(
                    abs(
                        pageLastXOffset - (pdfView.pdfFile?.getPageOffset(
                            range.page,
                            pdfView.zoom
                        ) ?: 0f)
                    ) / colWidth
                )
                range.rightBottom.row = MathUtils.floor(
                    MathUtils.min(
                        pageLastYOffset - secondaryOffset,
                        0f
                    ) / rowHeight
                )
            }
            renderRanges.add(range)
        }
        return renderRanges
    }

    private fun loadVisible(searchQuery: String) {
        var parts = 0
        val scaledPreloadOffset = preloadOffset.toFloat()
        val firstXOffset = -xOffset + scaledPreloadOffset
        val lastXOffset = -xOffset - pdfView.width - scaledPreloadOffset
        val firstYOffset = -yOffset + scaledPreloadOffset
        val lastYOffset = -yOffset - pdfView.height - scaledPreloadOffset
        val rangeList = getRenderRangeList(
            firstXOffset,
            firstYOffset,
            lastXOffset,
            lastYOffset
        )
        for (range in rangeList) {
            loadThumbnail(range.page, searchQuery)
        }
        for (range in rangeList) {
            calculatePartSize(range.gridSize)
            parts += loadPage(
                range.page, range.leftTop.row, range.rightBottom.row, range.leftTop.col,
                range.rightBottom.col, PdfConstants.Cache.CACHE_SIZE - parts, searchQuery
            )
            if (parts >= PdfConstants.Cache.CACHE_SIZE) {
                break
            }
        }
    }

    private fun loadPage(
        page: Int, firstRow: Int, lastRow: Int, firstCol: Int,
        lastCol: Int, nbOfPartsLoadable: Int, searchQuery: String
    ): Int {
        var loaded = 0
        for (row in firstRow..lastRow) {
            for (col in firstCol..lastCol) {
                if (loadCell(
                        page, row, col, pageRelativePartWidth, pageRelativePartHeight,
                        searchQuery
                    )
                ) {
                    loaded++
                }
                if (loaded >= nbOfPartsLoadable) {
                    return loaded
                }
            }
        }
        return loaded
    }

    private fun loadCell(
        page: Int, row: Int, col: Int, pageRelativePartWidth: Float,
        pageRelativePartHeight: Float, searchQuery: String
    ): Boolean {
        val relX = pageRelativePartWidth * col
        val relY = pageRelativePartHeight * row
        var relWidth = pageRelativePartWidth
        var relHeight = pageRelativePartHeight
        var renderWidth = partRenderWidth
        var renderHeight = partRenderHeight
        if (relX + relWidth > 1) {
            relWidth = 1 - relX
        }
        if (relY + relHeight > 1) {
            relHeight = 1 - relY
        }
        renderWidth *= relWidth
        renderHeight *= relHeight
        val bounds = RectF(relX, relY, relX + relWidth, relY + relHeight)
        if (renderWidth > 0 && renderHeight > 0) {
            if (!pdfView.cacheManager.upPartIfContained(page, bounds, cacheOrder, searchQuery)) {
                pdfView.renderingHandler?.addPartRenderingTask(
                    page, renderWidth, renderHeight, bounds, false, cacheOrder,
                    pdfView.isBestQuality, pdfView.isAnnotationRendering, searchQuery
                )
            }
            cacheOrder++
            return true
        }
        return false
    }

    private fun loadThumbnail(page: Int, searchQuery: String) {
        val pageSize = pdfView.pdfFile?.getPageSize(page) ?: SizeF(0f, 0f)
        val thumbnailWidth = pageSize.width * PdfConstants.THUMBNAIL_RATIO
        val thumbnailHeight = pageSize.height * PdfConstants.THUMBNAIL_RATIO
        if (!pdfView.cacheManager.containsThumbnail(page, thumbnailRect, searchQuery)) {
            pdfView.renderingHandler?.addPartRenderingTask(
                page, thumbnailWidth, thumbnailHeight, thumbnailRect, true, 0,
                pdfView.isBestQuality, pdfView.isAnnotationRendering, searchQuery
            )
        }
    }

    fun loadPages(searchQuery: String) {
        cacheOrder = 1
        xOffset = -MathUtils.max(pdfView.currentXOffset, 0f)
        yOffset = -MathUtils.max(pdfView.currentYOffset, 0f)
        loadVisible(searchQuery)
    }

    fun parseText(pagesIndexes: List<Int>) {
        if (pagesIndexes.isNotEmpty()) {
            for (index in pagesIndexes) {
                pdfView.renderingHandler?.addParseTextTask(index)
            }
        }
    }

    fun renderPage(page: Int, isThumbnail: Boolean = true) {
        val ratio = if (isThumbnail) PdfConstants.THUMBNAIL_RATIO else 1.0f
        val pageSize = pdfView.pdfFile?.getPageSize(page) ?: SizeF(0f, 0f)
        val width = pageSize.width * ratio
        val height = pageSize.height * ratio
        pdfView.renderingHandler?.addPageRenderingTask(page = page, width = width, height = height)
    }
}