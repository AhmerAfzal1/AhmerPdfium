package com.ahmer.pdfviewer

import android.graphics.RectF
import com.ahmer.pdfium.util.SizeF
import com.ahmer.pdfviewer.util.MathUtils
import com.ahmer.pdfviewer.util.PdfConstants
import com.ahmer.pdfviewer.util.PdfConstants.Cache.CACHE_SIZE
import com.ahmer.pdfviewer.util.PdfConstants.PRELOAD_OFFSET
import com.ahmer.pdfviewer.util.PdfUtils
import java.util.*
import kotlin.math.abs

internal class PagesLoader(private val pdfView: PDFView) {
    private val thumbnailRect = RectF(0f, 0f, 1f, 1f)
    private val preloadOffset: Int = PdfUtils.getDP(pdfView.context, PRELOAD_OFFSET)
    private var cacheOrder = 0
    private var xOffset = 0f
    private var yOffset = 0f
    private var pageRelativePartWidth = 0f
    private var pageRelativePartHeight = 0f
    private var partRenderWidth = 0f
    private var partRenderHeight = 0f

    private fun getPageColsRows(grid: GridSize, pageIndex: Int) {
        val size: SizeF = pdfView.pdfFile!!.getPageSize(pageIndex)
        val ratioX: Float = 1f / size.width
        val ratioY: Float = 1f / size.height
        val partHeight = PdfConstants.PART_SIZE * ratioY / pdfView.getZoom()
        val partWidth = PdfConstants.PART_SIZE * ratioX / pdfView.getZoom()
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
     * Calculate the render range of each page
     */
    private fun getRenderRangeList(
        firstXOffset: Float, firstYOffset: Float, lastXOffset: Float, lastYOffset: Float
    ): List<RenderRange> {
        val fixedFirstXOffset: Float = -MathUtils.max(firstXOffset, 0f)
        val fixedFirstYOffset: Float = -MathUtils.max(firstYOffset, 0f)
        val fixedLastXOffset: Float = -MathUtils.max(lastXOffset, 0f)
        val fixedLastYOffset: Float = -MathUtils.max(lastYOffset, 0f)
        val offsetFirst = if (pdfView.isSwipeVertical()) fixedFirstYOffset else fixedFirstXOffset
        val offsetLast = if (pdfView.isSwipeVertical()) fixedLastYOffset else fixedLastXOffset
        val firstPage = pdfView.pdfFile!!.getPageAtOffset(offsetFirst, pdfView.getZoom())
        val lastPage = pdfView.pdfFile!!.getPageAtOffset(offsetLast, pdfView.getZoom())
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
                    val pageOffset = pdfView.pdfFile!!.getPageOffset(page, pdfView.getZoom())
                    val pageSize: SizeF =
                        pdfView.pdfFile!!.getScaledPageSize(page, pdfView.getZoom())
                    if (pdfView.isSwipeVertical()) {
                        pageLastXOffset = fixedLastXOffset
                        pageLastYOffset = pageOffset + pageSize.height
                    } else {
                        pageLastYOffset = fixedLastYOffset
                        pageLastXOffset = pageOffset + pageSize.width
                    }
                }
            } else if (page == lastPage) {
                val pageOffset = pdfView.pdfFile!!.getPageOffset(page, pdfView.getZoom())
                if (pdfView.isSwipeVertical()) {
                    pageFirstXOffset = fixedFirstXOffset
                    pageFirstYOffset = pageOffset
                } else {
                    pageFirstYOffset = fixedFirstYOffset
                    pageFirstXOffset = pageOffset
                }
                pageLastXOffset = fixedLastXOffset
                pageLastYOffset = fixedLastYOffset
            } else {
                val pageOffset = pdfView.pdfFile!!.getPageOffset(page, pdfView.getZoom())
                val pageSize: SizeF = pdfView.pdfFile!!.getScaledPageSize(page, pdfView.getZoom())
                if (pdfView.isSwipeVertical()) {
                    pageFirstXOffset = fixedFirstXOffset
                    pageFirstYOffset = pageOffset
                    pageLastXOffset = fixedLastXOffset
                    pageLastYOffset = pageOffset + pageSize.height
                } else {
                    pageFirstXOffset = pageOffset
                    pageFirstYOffset = fixedFirstYOffset
                    pageLastXOffset = pageOffset + pageSize.width
                    pageLastYOffset = fixedLastYOffset
                }
            }
            // Get the page's grid size that rows and cols
            getPageColsRows(range.gridSize, range.page)
            val scaledPageSize: SizeF =
                pdfView.pdfFile!!.getScaledPageSize(range.page, pdfView.getZoom())
            val rowHeight: Float = scaledPageSize.height / range.gridSize.rows
            val colWidth: Float = scaledPageSize.width / range.gridSize.cols

            // Get the page offset int the whole file
            // ---------------------------------------
            // |            |           |            |
            // |<--offset-->|   (page)  |<--offset-->|
            // |            |           |            |
            // |            |           |            |
            // ---------------------------------------
            val secondaryOffset = pdfView.pdfFile!!.getSecondaryPageOffset(page, pdfView.getZoom())

            // Calculate the row,col of the point in the leftTop and rightBottom
            if (pdfView.isSwipeVertical()) {
                range.leftTop.row = MathUtils.floor(
                    abs(
                        pageFirstYOffset - pdfView.pdfFile!!.getPageOffset(
                            range.page,
                            pdfView.getZoom()
                        )
                    ) / rowHeight
                )
                range.leftTop.col = MathUtils.floor(
                    MathUtils.min(pageFirstXOffset - secondaryOffset, 0f) / colWidth
                )
                range.rightBottom.row = MathUtils.ceil(
                    abs(
                        pageLastYOffset - pdfView.pdfFile!!.getPageOffset(
                            range.page,
                            pdfView.getZoom()
                        )
                    ) / rowHeight
                )
                range.rightBottom.col = MathUtils.floor(
                    MathUtils.min(pageLastXOffset - secondaryOffset, 0f) / colWidth
                )
            } else {
                range.leftTop.col = MathUtils.floor(
                    abs(
                        pageFirstXOffset - pdfView.pdfFile!!.getPageOffset(
                            range.page,
                            pdfView.getZoom()
                        )
                    ) / colWidth
                )
                range.leftTop.row = MathUtils.floor(
                    MathUtils.min(
                        if (pageFirstYOffset > 0) pageFirstYOffset - secondaryOffset else 0f, 0f
                    ) / rowHeight
                )
                range.rightBottom.col = MathUtils.floor(
                    abs(
                        pageLastXOffset - pdfView.pdfFile!!.getPageOffset(
                            range.page,
                            pdfView.getZoom()
                        )
                    ) / colWidth
                )
                range.rightBottom.row = MathUtils.floor(
                    MathUtils.min(pageLastYOffset - secondaryOffset, 0f) / rowHeight
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
        val rangeList = getRenderRangeList(firstXOffset, firstYOffset, lastXOffset, lastYOffset)
        for (range in rangeList) {
            loadThumbnail(range.page, searchQuery)
        }
        for (range in rangeList) {
            calculatePartSize(range.gridSize)
            parts += loadPage(
                range.page, range.leftTop.row, range.rightBottom.row, range.leftTop.col,
                range.rightBottom.col, CACHE_SIZE - parts, searchQuery
            )
            if (parts >= CACHE_SIZE) {
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
                        page, row, col, pageRelativePartWidth, pageRelativePartHeight, searchQuery
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
            if (!pdfView.cacheManager?.upPartIfContained(page, bounds, cacheOrder, searchQuery)!!) {
                pdfView.renderingHandler?.addRenderingTask(
                    page, renderWidth, renderHeight, bounds, false, cacheOrder,
                    pdfView.isBestQuality(), pdfView.isAnnotationRendering(), searchQuery
                )
            }
            cacheOrder++
            return true
        }
        return false
    }

    private fun loadThumbnail(page: Int, searchQuery: String) {
        val pageSize: SizeF = pdfView.pdfFile!!.getPageSize(page)
        val thumbnailWidth: Float = pageSize.width * PdfConstants.THUMBNAIL_RATIO
        val thumbnailHeight: Float = pageSize.height * PdfConstants.THUMBNAIL_RATIO
        if (!pdfView.cacheManager?.containsThumbnail(page, thumbnailRect, searchQuery)!!) {
            pdfView.renderingHandler?.addRenderingTask(
                page, thumbnailWidth, thumbnailHeight, thumbnailRect, true, 0,
                pdfView.isBestQuality(), pdfView.isAnnotationRendering(), searchQuery
            )
        }
    }

    fun loadPages(searchQuery: String) {
        cacheOrder = 1
        xOffset = -MathUtils.max(pdfView.getCurrentXOffset(), 0f)
        yOffset = -MathUtils.max(pdfView.getCurrentYOffset(), 0f)
        loadVisible(searchQuery)
    }

    private class Holder {
        var row = 0
        var col = 0
        override fun toString(): String {
            return "Holder{row=$row, col=$col}"
        }
    }

    private inner class RenderRange() {
        var page = 0
        val gridSize: GridSize = GridSize()
        val leftTop: Holder = Holder()
        val rightBottom: Holder = Holder()

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

}