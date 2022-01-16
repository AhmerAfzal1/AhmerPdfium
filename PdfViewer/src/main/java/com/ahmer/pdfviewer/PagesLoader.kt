package com.ahmer.pdfviewer

import android.graphics.RectF
import com.ahmer.pdfium.util.SizeF
import com.ahmer.pdfviewer.util.MathUtils
import com.ahmer.pdfviewer.util.PdfConstants
import com.ahmer.pdfviewer.util.PdfConstants.Cache.CACHE_SIZE
import com.ahmer.pdfviewer.util.PdfConstants.PRELOAD_OFFSET
import com.ahmer.pdfviewer.util.PdfUtils
import java.util.*

internal class PagesLoader(pdfView: PDFView) {

    private val thumbnailRect = RectF(0F, 0F, 1F, 1F)
    private val mPreloadOffset: Int = PdfUtils.getDP(pdfView.context, PRELOAD_OFFSET)
    private val mPdfView: PDFView = pdfView
    private var cacheOrder = 0
    private var xOffset = 0f
    private var yOffset = 0f
    private var pageRelativePartWidth = 0f
    private var pageRelativePartHeight = 0f
    private var partRenderWidth = 0f
    private var partRenderHeight = 0f

    private fun getPageColsRows(grid: GridSize, pageIndex: Int) {
        val size: SizeF = mPdfView.pdfFile?.getPageSize(pageIndex)!!
        val ratioX: Float = 1f / size.width
        val ratioY: Float = 1f / size.height
        val partHeight: Float = PdfConstants.PART_SIZE * ratioY / mPdfView.zoom
        val partWidth: Float = PdfConstants.PART_SIZE * ratioX / mPdfView.zoom
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
        firstXOffset: Float, firstYOffset: Float,
        lastXOffset: Float, lastYOffset: Float
    ): List<RenderRange> {
        val fixedFirstXOffset: Float = -MathUtils.max(firstXOffset, 0f)
        val fixedFirstYOffset: Float = -MathUtils.max(firstYOffset, 0f)
        val fixedLastXOffset: Float = -MathUtils.max(lastXOffset, 0f)
        val fixedLastYOffset: Float = -MathUtils.max(lastYOffset, 0f)
        val offsetFirst = if (mPdfView.isSwipeVertical) fixedFirstYOffset else fixedFirstXOffset
        val offsetLast = if (mPdfView.isSwipeVertical) fixedLastYOffset else fixedLastXOffset
        val firstPage: Int = mPdfView.pdfFile?.getPageAtOffset(offsetFirst, mPdfView.zoom)!!
        val lastPage: Int = mPdfView.pdfFile?.getPageAtOffset(offsetLast, mPdfView.zoom)!!
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
                    val pageOffset: Float = mPdfView.pdfFile!!.getPageOffset(page, mPdfView.zoom)
                    val pageSize: SizeF = mPdfView.pdfFile!!.getScaledPageSize(page, mPdfView.zoom)
                    if (mPdfView.isSwipeVertical) {
                        pageLastXOffset = fixedLastXOffset
                        pageLastYOffset = pageOffset + pageSize.height
                    } else {
                        pageLastYOffset = fixedLastYOffset
                        pageLastXOffset = pageOffset + pageSize.width
                    }
                }
            } else if (page == lastPage) {
                val pageOffset: Float = mPdfView.pdfFile!!.getPageOffset(page, mPdfView.zoom)
                if (mPdfView.isSwipeVertical) {
                    pageFirstXOffset = fixedFirstXOffset
                    pageFirstYOffset = pageOffset
                } else {
                    pageFirstYOffset = fixedFirstYOffset
                    pageFirstXOffset = pageOffset
                }
                pageLastXOffset = fixedLastXOffset
                pageLastYOffset = fixedLastYOffset
            } else {
                val pageOffset: Float = mPdfView.pdfFile!!.getPageOffset(page, mPdfView.zoom)
                val pageSize: SizeF = mPdfView.pdfFile!!.getScaledPageSize(page, mPdfView.zoom)
                if (mPdfView.isSwipeVertical) {
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
            getPageColsRows(
                range.gridSize,
                range.page
            ) // get the page's grid size that rows and cols
            val scaledPageSize: SizeF =
                mPdfView.pdfFile!!.getScaledPageSize(range.page, mPdfView.zoom)
            val rowHeight: Float = scaledPageSize.height / range.gridSize.rows
            val colWidth: Float = scaledPageSize.width / range.gridSize.cols

            // get the page offset int the whole file
            // ---------------------------------------
            // |            |           |            |
            // |<--offset-->|   (page)  |<--offset-->|
            // |            |           |            |
            // |            |           |            |
            // ---------------------------------------
            val secondaryOffset: Float =
                mPdfView.pdfFile!!.getSecondaryPageOffset(page, mPdfView.zoom)

            // calculate the row,col of the point in the leftTop and rightBottom
            if (mPdfView.isSwipeVertical) {
                range.leftTop.row = MathUtils.floor(
                    Math.abs(
                        pageFirstYOffset - mPdfView.pdfFile!!.getPageOffset(range.page, mPdfView.zoom)) / rowHeight
                )
                range.leftTop.col = MathUtils.floor(
                    MathUtils.min(pageFirstXOffset - secondaryOffset, 0f) / colWidth
                )
                range.rightBottom.row = MathUtils.ceil(
                    Math.abs(
                        pageLastYOffset -
                                mPdfView.pdfFile!!.getPageOffset(range.page, mPdfView.zoom)
                    ) / rowHeight
                )
                range.rightBottom.col = MathUtils.floor(
                    MathUtils.min(pageLastXOffset - secondaryOffset, 0f) / colWidth
                )
            } else {
                range.leftTop.col = MathUtils.floor(
                    Math.abs(
                        pageFirstXOffset -
                                mPdfView.pdfFile!!.getPageOffset(range.page, mPdfView.zoom)
                    ) / colWidth
                )
                range.leftTop.row = MathUtils.floor(
                    MathUtils.min(if (pageFirstYOffset > 0) pageFirstYOffset - secondaryOffset else 0f, 0f) / rowHeight
                )
                range.rightBottom.col = MathUtils.floor(
                    Math.abs(
                        pageLastXOffset -
                                mPdfView.pdfFile!!.getPageOffset(range.page, mPdfView.zoom)
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

    private fun loadVisible() {
        var parts = 0
        val scaledPreloadOffset = mPreloadOffset.toFloat()
        val firstXOffset = -xOffset + scaledPreloadOffset
        val lastXOffset: Float = -xOffset - mPdfView.width - scaledPreloadOffset
        val firstYOffset = -yOffset + scaledPreloadOffset
        val lastYOffset: Float = -yOffset - mPdfView.height - scaledPreloadOffset
        val rangeList = getRenderRangeList(firstXOffset, firstYOffset, lastXOffset, lastYOffset)
        for (range in rangeList) {
            loadThumbnail(range.page)
        }
        for (range in rangeList) {
            calculatePartSize(range.gridSize)
            parts += loadPage(
                range.page, range.leftTop.row, range.rightBottom.row, range.leftTop.col,
                range.rightBottom.col, CACHE_SIZE - parts
            )
            if (parts >= CACHE_SIZE) {
                break
            }
        }
    }

    private fun loadPage(
        page: Int, firstRow: Int, lastRow: Int, firstCol: Int, lastCol: Int,
        nbOfPartsLoadable: Int
    ): Int {
        var loaded = 0
        for (row in firstRow..lastRow) {
            for (col in firstCol..lastCol) {
                if (loadCell(page, row, col, pageRelativePartWidth, pageRelativePartHeight)) {
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
        pageRelativePartHeight: Float
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
        val pageRelativeBounds = RectF(relX, relY, relX + relWidth, relY + relHeight)
        if (renderWidth > 0 && renderHeight > 0) {
            if (!mPdfView.cacheManager?.upPartIfContained(page, pageRelativeBounds, cacheOrder)!!) {
                mPdfView.renderingHandler?.addRenderingTask(
                    page, renderWidth, renderHeight, pageRelativeBounds,
                    false, cacheOrder, mPdfView.isBestQuality, mPdfView.isAnnotationRendering
                )
            }
            cacheOrder++
            return true
        }
        return false
    }

    private fun loadThumbnail(page: Int) {
        val pageSize: SizeF = mPdfView.pdfFile?.getPageSize(page)!!
        val thumbnailWidth: Float = pageSize.width * PdfConstants.THUMBNAIL_RATIO
        val thumbnailHeight: Float = pageSize.height * PdfConstants.THUMBNAIL_RATIO
        if (!mPdfView.cacheManager?.containsThumbnail(page, thumbnailRect)!!) {
            mPdfView.renderingHandler?.addRenderingTask(
                page, thumbnailWidth, thumbnailHeight, thumbnailRect,
                true, 0, mPdfView.isBestQuality, mPdfView.isAnnotationRendering
            )
        }
    }

    fun loadPages() {
        cacheOrder = 1
        xOffset = -MathUtils.max(mPdfView.currentXOffset, 0f)
        yOffset = -MathUtils.max(mPdfView.currentYOffset, 0f)
        loadVisible()
    }

    private class Holder {
        var row = 0
        var col = 0
        override fun toString(): String {
            return "Holder{" +
                    "row=" + row +
                    ", col=" + col +
                    '}'
        }
    }

    private inner class RenderRange() {
        var page = 0
        var gridSize: GridSize = GridSize()
        var leftTop: Holder = Holder()
        var rightBottom: Holder = Holder()
        override fun toString(): String {
            return "RenderRange{" +
                    "page=" + page +
                    ", gridSize=" + gridSize +
                    ", leftTop=" + leftTop +
                    ", rightBottom=" + rightBottom +
                    '}'
        }

    }

    private inner class GridSize {
        var rows = 0
        var cols = 0
        override fun toString(): String {
            return "GridSize{" +
                    "rows=" + rows +
                    ", cols=" + cols +
                    '}'
        }
    }

}
