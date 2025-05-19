package com.ahmer.pdfviewer

import android.graphics.RectF
import com.ahmer.pdfium.util.SizeF
import com.ahmer.pdfviewer.util.MathUtils
import com.ahmer.pdfviewer.util.PdfConstants
import com.ahmer.pdfviewer.util.PdfConstants.Cache
import com.ahmer.pdfviewer.util.PdfUtils
import kotlin.math.abs

internal class PagesLoader(private val pdfView: PDFView) {

    private val preLoadOffset: Int = PdfUtils.getDP(pdfView.context, PdfConstants.PRELOAD_OFFSET)
    private val thumbnailRect = RectF(0f, 0f, 1f, 1f)
    private var cacheOrder = 0
    private var offsetX = 0f
    private var offsetY = 0f
    private var pageRelativePartHeight = 0f
    private var pageRelativePartWidth = 0f
    private var partRenderHeight = 0f
    private var partRenderWidth = 0f

    private fun getPageColsRows(grid: GridSize, pageIndex: Int) {
        val size: SizeF = pdfView.pdfFile!!.getPageSize(pageIndex = pageIndex)
        val ratioX: Float = 1f / size.width
        val ratioY: Float = 1f / size.height
        val partHeight = PdfConstants.PART_SIZE * ratioY / pdfView.getZoom()
        val partWidth = PdfConstants.PART_SIZE * ratioX / pdfView.getZoom()
        grid.rows = MathUtils.ceil(value = 1f / partHeight)
        grid.column = MathUtils.ceil(value = 1f / partWidth)
    }

    private fun calculatePartSize(grid: GridSize) {
        pageRelativePartWidth = 1f / grid.column.toFloat()
        pageRelativePartHeight = 1f / grid.rows.toFloat()
        partRenderWidth = PdfConstants.PART_SIZE / pageRelativePartWidth
        partRenderHeight = PdfConstants.PART_SIZE / pageRelativePartHeight
    }

    /**
     * Calculate the render range of each page
     */
    private fun getRenderRangeList(
        firstXOffset: Float,
        firstYOffset: Float,
        lastXOffset: Float,
        lastYOffset: Float
    ): List<RenderRange> {
        val fixedFirstXOffset: Float = -MathUtils.max(number = firstXOffset, max = 0f)
        val fixedFirstYOffset: Float = -MathUtils.max(number = firstYOffset, max = 0f)
        val fixedLastXOffset: Float = -MathUtils.max(number = lastXOffset, max = 0f)
        val fixedLastYOffset: Float = -MathUtils.max(number = lastYOffset, max = 0f)

        val isVertical: Boolean = pdfView.isSwipeVertical()
        val offsetFirst: Float = if (isVertical) fixedFirstYOffset else fixedFirstXOffset
        val offsetLast: Float = if (isVertical) fixedLastYOffset else fixedLastXOffset

        val zoom: Float = pdfView.getZoom()
        val pageFirst: Int = pdfView.pdfFile!!.getPageAtOffset(offset = offsetFirst, zoom = zoom)
        val pageLast: Int = pdfView.pdfFile!!.getPageAtOffset(offset = offsetLast, zoom = zoom)
        val pageCount: Int = pageLast - pageFirst + 1
        val renderRanges: MutableList<RenderRange> = mutableListOf()

        for (page in pageFirst..pageLast) {
            val range = RenderRange().apply {
                this.page = page
            }
            val (pageFirstXOffset, pageFirstYOffset, pageLastXOffset, pageLastYOffset) = calculatePageOffsets(
                page = page,
                pageFirst = pageFirst,
                pageLast = pageLast,
                pageCount = pageCount,
                fixedFirstXOffset = fixedFirstXOffset,
                fixedFirstYOffset = fixedFirstYOffset,
                fixedLastXOffset = fixedLastXOffset,
                fixedLastYOffset = fixedLastYOffset,
                zoom = zoom,
                isVertical = isVertical
            )

            getPageColsRows(grid = range.gridSize, pageIndex = range.page)
            val scalePageSize: SizeF = pdfView.pdfFile!!.getScaledPageSize(
                pageIndex = range.page,
                zoom = zoom
            )
            val colWidth: Float = scalePageSize.width / range.gridSize.column
            val rowHeight: Float = scalePageSize.height / range.gridSize.rows
            val secondaryOffset: Float = pdfView.pdfFile!!.getSecondaryPageOffset(
                pageIndex = page,
                zoom = zoom
            )

            calculateGridPositions(
                range = range,
                pageFirstXOffset = pageFirstXOffset,
                pageFirstYOffset = pageFirstYOffset,
                pageLastXOffset = pageLastXOffset,
                pageLastYOffset = pageLastYOffset,
                colWidth = colWidth,
                rowHeight = rowHeight,
                secondaryOffset = secondaryOffset,
                isVertical = isVertical
            )
            renderRanges.add(range)
        }
        return renderRanges
    }

    private fun calculatePageOffsets(
        page: Int,
        pageFirst: Int,
        pageLast: Int,
        pageCount: Int,
        fixedFirstXOffset: Float,
        fixedFirstYOffset: Float,
        fixedLastXOffset: Float,
        fixedLastYOffset: Float,
        zoom: Float,
        isVertical: Boolean
    ): Quadruple<Float, Float, Float, Float> {
        return when (page) {
            pageFirst -> {
                val pageOffset: Float = pdfView.pdfFile!!.getPageOffset(
                    pageIndex = page,
                    zoom = zoom
                )
                val pageSize: SizeF = pdfView.pdfFile!!.getScaledPageSize(
                    pageIndex = page,
                    zoom = zoom
                )

                val lastX = if (pageCount == 1) fixedLastXOffset else {
                    if (isVertical) fixedLastXOffset else pageOffset + pageSize.width
                }
                val lastY = if (pageCount == 1) fixedLastYOffset else {
                    if (isVertical) pageOffset + pageSize.height else fixedLastYOffset
                }
                Quadruple(
                    first = fixedFirstXOffset,
                    second = fixedFirstYOffset,
                    third = lastX,
                    fourth = lastY
                )
            }

            pageLast -> {
                val pageOffset: Float = pdfView.pdfFile!!.getPageOffset(
                    pageIndex = page,
                    zoom = zoom
                )
                Quadruple(
                    first = if (isVertical) fixedFirstXOffset else pageOffset,
                    second = if (isVertical) pageOffset else fixedFirstYOffset,
                    third = fixedLastXOffset,
                    fourth = fixedLastYOffset
                )
            }

            else -> {
                val pageOffset: Float = pdfView.pdfFile!!.getPageOffset(
                    pageIndex = page,
                    zoom = zoom
                )
                val pageSize: SizeF = pdfView.pdfFile!!.getScaledPageSize(
                    pageIndex = page,
                    zoom = zoom
                )
                Quadruple(
                    first = if (isVertical) fixedFirstXOffset else pageOffset,
                    second = if (isVertical) pageOffset else fixedFirstYOffset,
                    third = if (isVertical) fixedLastXOffset else pageOffset + pageSize.width,
                    fourth = if (isVertical) pageOffset + pageSize.height else fixedLastYOffset
                )
            }
        }
    }

    private fun calculateGridPositions(
        range: RenderRange,
        pageFirstXOffset: Float,
        pageFirstYOffset: Float,
        pageLastXOffset: Float,
        pageLastYOffset: Float,
        colWidth: Float,
        rowHeight: Float,
        secondaryOffset: Float, isVertical: Boolean
    ) {
        val pageOffset: Float = pdfView.pdfFile!!.getPageOffset(
            pageIndex = range.page,
            zoom = pdfView.getZoom()
        )
        // Get the page offset int the whole file
        // ---------------------------------------
        // |            |           |            |
        // |<--offset-->|   (page)  |<--offset-->|
        // |            |           |            |
        // |            |           |            |
        // ---------------------------------------
        // Calculate the row,col of the point in the leftTop and rightBottom
        if (isVertical) {
            range.leftTop.row = MathUtils.floor(
                value = abs(x = pageFirstYOffset - pageOffset) / rowHeight
            )
            range.leftTop.column = MathUtils.floor(
                value = MathUtils.min(
                    number = pageFirstXOffset - secondaryOffset,
                    min = 0f
                ) / colWidth
            )
            range.rightBottom.row = MathUtils.ceil(
                value = abs(x = pageLastYOffset - pageOffset) / rowHeight
            )
            range.rightBottom.column =
                MathUtils.floor(
                    value = MathUtils.min(
                        number = pageLastXOffset - secondaryOffset,
                        min = 0f
                    ) / colWidth
                )
        } else {
            range.leftTop.column = MathUtils.floor(
                value = abs(x = pageFirstXOffset - pageOffset) / colWidth
            )
            range.leftTop.row = MathUtils.floor(
                value = MathUtils.min(
                    if (pageFirstYOffset > 0) pageFirstYOffset - secondaryOffset else 0f,
                    min = 0f
                ) / rowHeight
            )
            range.rightBottom.column = MathUtils.floor(
                value = abs(x = pageLastXOffset - pageOffset) / colWidth
            )
            range.rightBottom.row =
                MathUtils.floor(
                    value = MathUtils.min(
                        number = pageLastYOffset - secondaryOffset,
                        min = 0f
                    ) / rowHeight
                )
        }
    }

    private fun loadVisible() {
        val scaledPreloadOffset: Float = preLoadOffset.toFloat()
        val firstOffsetX: Float = -offsetX + scaledPreloadOffset
        val firstOffsetY: Float = -offsetY + scaledPreloadOffset
        val lastOffsetX: Float = -offsetX - pdfView.width - scaledPreloadOffset
        val lastOffsetY: Float = -offsetY - pdfView.height - scaledPreloadOffset

        val rangeList: List<PagesLoader.RenderRange> = getRenderRangeList(
            firstXOffset = firstOffsetX,
            firstYOffset = firstOffsetY,
            lastXOffset = lastOffsetX,
            lastYOffset = lastOffsetY
        )
        rangeList.forEach {
            loadThumbnail(page = it.page)
        }

        var partsLoaded = 0
        for (range in rangeList) {
            calculatePartSize(grid = range.gridSize)
            partsLoaded += loadPage(
                page = range.page,
                firstRow = range.leftTop.row,
                lastRow = range.rightBottom.row,
                firstColumn = range.leftTop.column,
                lastColumn = range.rightBottom.column,
                maxPartsToLoad = Cache.CACHE_SIZE - partsLoaded
            )
            if (partsLoaded >= Cache.CACHE_SIZE) break
        }
    }

    private fun loadPage(
        page: Int,
        firstRow: Int,
        lastRow: Int,
        firstColumn: Int,
        lastColumn: Int,
        maxPartsToLoad: Int
    ): Int {
        var loaded = 0
        for (row in firstRow..lastRow) {
            for (column in firstColumn..lastColumn) {
                if (loadCell(
                        page = page,
                        row = row,
                        column = column,
                        pageRelativePartWidth = pageRelativePartWidth,
                        pageRelativePartHeight = pageRelativePartHeight
                    )
                ) {
                    loaded++
                }
                if (loaded >= maxPartsToLoad) return loaded
            }
        }
        return loaded
    }

    private fun loadCell(
        page: Int,
        row: Int,
        column: Int,
        pageRelativePartWidth: Float,
        pageRelativePartHeight: Float
    ): Boolean {
        val relativeX: Float = pageRelativePartWidth * column
        val relativeY: Float = pageRelativePartHeight * row
        var relativeHeight: Float = pageRelativePartHeight
        var relativeWidth: Float = pageRelativePartWidth

        if (relativeX + relativeWidth > 1) relativeWidth = 1 - relativeX
        if (relativeY + relativeHeight > 1) relativeHeight = 1 - relativeY

        val renderHeight: Float = partRenderWidth * relativeHeight
        val renderWidth: Float = partRenderHeight * relativeWidth

        if (renderWidth > 0 && renderHeight > 0) {
            val bounds = RectF(
                relativeX,
                relativeY,
                relativeX + relativeWidth,
                relativeY + relativeHeight
            )

            if (!pdfView.cacheManager?.moveToActiveCache(
                    page = page,
                    bounds = bounds,
                    newOrder = cacheOrder
                )!!
            ) {
                pdfView.renderingHandler?.addRenderingTask(
                    page = page,
                    width = renderWidth,
                    height = renderHeight,
                    bounds = bounds,
                    isThumbnail = false,
                    cacheOrder = cacheOrder,
                    isBestQuality = pdfView.isBestQuality(),
                    isAnnotation = pdfView.isAnnotationRendering()
                )
            }
            cacheOrder++
            return true
        }
        return false
    }

    private fun loadThumbnail(page: Int) {
        val pageSize: SizeF = pdfView.pdfFile!!.getPageSize(page)
        val thumbnailHeight: Float = pageSize.height * PdfConstants.THUMBNAIL_RATIO
        val thumbnailWidth: Float = pageSize.width * PdfConstants.THUMBNAIL_RATIO

        if (!pdfView.cacheManager?.hasThumbnail(page = page, bounds = thumbnailRect)!!) {
            pdfView.renderingHandler?.addRenderingTask(
                page = page,
                width = thumbnailWidth,
                height = thumbnailHeight,
                bounds = thumbnailRect,
                isThumbnail = true,
                cacheOrder = 0,
                isBestQuality = pdfView.isBestQuality(),
                isAnnotation = pdfView.isAnnotationRendering()
            )
        }
    }

    fun loadPages() {
        cacheOrder = 1
        offsetX = -MathUtils.max(number = pdfView.getCurrentXOffset(), max = 0f)
        offsetY = -MathUtils.max(number = pdfView.getCurrentYOffset(), max = 0f)
        loadVisible()
    }

    private data class GridSize(var column: Int = 0, var rows: Int = 0)

    private data class Holder(var column: Int = 0, var row: Int = 0)

    private data class Quadruple<out A, out B, out C, out D>(
        val first: A, val second: B, val third: C, val fourth: D
    )

    private data class RenderRange(
        val gridSize: GridSize = GridSize(),
        val leftTop: Holder = Holder(),
        val rightBottom: Holder = Holder(),
        var page: Int = 0
    )
}