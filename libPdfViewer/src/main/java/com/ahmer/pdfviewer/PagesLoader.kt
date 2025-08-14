package com.ahmer.pdfviewer

import android.graphics.RectF
import com.ahmer.pdfium.util.SizeF
import com.ahmer.pdfviewer.util.PdfConstants
import com.ahmer.pdfviewer.util.PdfConstants.Cache
import com.ahmer.pdfviewer.util.PdfUtils
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

internal class PagesLoader(private val pdfView: PDFView) {
    private val preLoadOffset: Int = PdfUtils.getDP(context = pdfView.context, dp = PdfConstants.PRELOAD_OFFSET)
    private val thumbnailRect = RectF(0f, 0f, 1f, 1f)
    private var cacheOrder = 0
    private var offsetX = 0f
    private var offsetY = 0f
    private var pageRelativePartHeight = 0f
    private var pageRelativePartWidth = 0f
    private var partRenderHeight = 0f
    private var partRenderWidth = 0f

    private fun getPageColsRows(grid: GridSize, pageIndex: Int) {
        val pdfFile: PdfFile = pdfView.pdfFile ?: return
        val size: SizeF = pdfFile.getPageSize(pageIndex = pageIndex)
        val ratioX: Float = 1f / size.width
        val ratioY: Float = 1f / size.height
        val partHeight = PdfConstants.PART_SIZE * ratioY / pdfView.zoom
        val partWidth = PdfConstants.PART_SIZE * ratioX / pdfView.zoom
        grid.rows = ceil(a = 1f / partHeight)
        grid.column = ceil(a = 1f / partWidth)
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
        val pdfFile: PdfFile = pdfView.pdfFile ?: return mutableListOf()
        val fixedFirstXOffset: Float = -min(a = firstXOffset, b = 0f)
        val fixedFirstYOffset: Float = -min(a = firstYOffset, b = 0f)
        val fixedLastXOffset: Float = -min(a = lastXOffset, b = 0f)
        val fixedLastYOffset: Float = -min(a = lastYOffset, b = 0f)

        val isVertical: Boolean = pdfView.isSwipeVertical
        val offsetFirst: Float = if (isVertical) fixedFirstYOffset else fixedFirstXOffset
        val offsetLast: Float = if (isVertical) fixedLastYOffset else fixedLastXOffset

        val zoom: Float = pdfView.zoom
        val pageFirst: Int = pdfFile.getPageAtOffset(offset = offsetFirst, zoom = zoom)
        val pageLast: Int = pdfFile.getPageAtOffset(offset = offsetLast, zoom = zoom)
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
            val scalePageSize: SizeF = pdfFile.getScaledPageSize(pageIndex = range.page, zoom = zoom)
            val colWidth: Float = scalePageSize.width / range.gridSize.column
            val rowHeight: Float = scalePageSize.height / range.gridSize.rows
            val secondaryOffset: Float = pdfFile.getSecondaryPageOffset(pageIndex = page, zoom = zoom)

            calculateGridPositions(
                range = range,
                pageStartX = pageFirstXOffset,
                pageStartY = pageFirstYOffset,
                pageEndX = pageLastXOffset,
                pageEndY = pageLastYOffset,
                colWidth = colWidth,
                rowHeight = rowHeight,
                offset = secondaryOffset,
                isVertical = isVertical,
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
        val pdfFile: PdfFile = pdfView.pdfFile ?: return Quadruple(first = 0f, second = 0f, third = 0f, fourth = 0f)
        return when (page) {
            pageFirst -> {
                val pageOffset: Float = pdfFile.getPageOffset(pageIndex = page, zoom = zoom)
                val pageSize: SizeF = pdfFile.getScaledPageSize(pageIndex = page, zoom = zoom)

                val lastX = if (pageCount == 1) fixedLastXOffset else {
                    if (isVertical) fixedLastXOffset else pageOffset + pageSize.width
                }
                val lastY = if (pageCount == 1) fixedLastYOffset else {
                    if (isVertical) pageOffset + pageSize.height else fixedLastYOffset
                }
                Quadruple(first = fixedFirstXOffset, second = fixedFirstYOffset, third = lastX, fourth = lastY)
            }

            pageLast -> {
                val pageOffset: Float = pdfFile.getPageOffset(pageIndex = page, zoom = zoom)
                Quadruple(
                    first = if (isVertical) fixedFirstXOffset else pageOffset,
                    second = if (isVertical) pageOffset else fixedFirstYOffset,
                    third = fixedLastXOffset,
                    fourth = fixedLastYOffset
                )
            }

            else -> {
                val pageOffset: Float = pdfFile.getPageOffset(pageIndex = page, zoom = zoom)
                val pageSize: SizeF = pdfFile.getScaledPageSize(pageIndex = page, zoom = zoom)
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
        pageStartX: Float,
        pageStartY: Float,
        pageEndX: Float,
        pageEndY: Float,
        colWidth: Float,
        rowHeight: Float,
        offset: Float,
        isVertical: Boolean
    ) {
        val pdfFile: PdfFile = pdfView.pdfFile ?: return
        val pageOffset: Float = pdfFile.getPageOffset(pageIndex = range.page, zoom = pdfView.zoom)
        // Get the page offset int the whole file
        // ---------------------------------------
        // |            |           |            |
        // |<--offset-->|   (page)  |<--offset-->|
        // |            |           |            |
        // |            |           |            |
        // ---------------------------------------
        // Calculate the row, col of the point in the leftTop and rightBottom
        if (isVertical) {
            range.leftTop.row = floor(a = abs(x = pageStartY - pageOffset) / rowHeight)
            range.leftTop.column = floor(a = max(a = pageStartX - offset, b = 0f) / colWidth)
            range.rightBottom.row = ceil(a = abs(x = pageEndY - pageOffset) / rowHeight)
            range.rightBottom.column = floor(a = max(a = pageEndX - offset, b = 0f) / colWidth)
        } else {
            range.leftTop.column = floor(a = abs(x = pageStartX - pageOffset) / colWidth)
            range.leftTop.row = floor(a = max(a = if (pageStartY > 0) pageStartY - offset else 0f, b = 0f) / rowHeight)
            range.rightBottom.column = floor(a = abs(x = pageEndX - pageOffset) / colWidth)
            range.rightBottom.row = floor(a = max(a = pageEndY - offset, b = 0f) / rowHeight)
        }
    }

    private fun loadVisible() {
        val scaledPreloadOffset: Float = preLoadOffset.toFloat()
        val firstOffsetX: Float = -offsetX + scaledPreloadOffset
        val firstOffsetY: Float = -offsetY + scaledPreloadOffset
        val lastOffsetX: Float = -offsetX - pdfView.width - scaledPreloadOffset
        val lastOffsetY: Float = -offsetY - pdfView.height - scaledPreloadOffset

        val rangeList: List<RenderRange> = getRenderRangeList(
            firstXOffset = firstOffsetX,
            firstYOffset = firstOffsetY,
            lastXOffset = lastOffsetX,
            lastYOffset = lastOffsetY
        )
        rangeList.forEach {
            // When the first page height is relatively small, after calculations using methods like
            // calculatePageOffsets, the final result of
            // "range.leftTop.row = floor(a = abs(x = pageStartY - pageOffset) / rowHeight)" in the
            // calculateGridPositions method is incorrect due to pageStartY = 0, a relatively large
            // pageOffset, and a relatively low rowHeight. The expected value is 0, but the actual
            // value is 1 or greater. I originally considered modifying this calculation,
            // but discovered that it would cause other rendering issues, so I'm choosing to modify
            // it here. I haven't found any issues yet. If you have a better solution, please ignore
            // it.
            // I also put the test PDF file in the assets directory, "sample_split.pdf"
            if (pdfView.isPageSnap && it.page == 0) {
                it.leftTop.row = 0
                it.leftTop.column = 0
            }
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
        val cacheManager: CacheManager = pdfView.cacheManager ?: return false
        val renderingHandler: RenderingHandler = pdfView.renderingHandler ?: return false
        val relativeX: Float = pageRelativePartWidth * column
        val relativeY: Float = pageRelativePartHeight * row
        var relativeHeight: Float = pageRelativePartHeight
        var relativeWidth: Float = pageRelativePartWidth

        if (relativeX + relativeWidth > 1) relativeWidth = 1 - relativeX
        if (relativeY + relativeHeight > 1) relativeHeight = 1 - relativeY

        val renderHeight: Float = partRenderWidth * relativeHeight
        val renderWidth: Float = partRenderHeight * relativeWidth

        if (renderWidth > 0 && renderHeight > 0) {
            val bounds = RectF(relativeX, relativeY, relativeX + relativeWidth, relativeY + relativeHeight)

            if (!cacheManager.moveToActiveCache(page = page, bounds = bounds, newOrder = cacheOrder)
            ) {
                renderingHandler.addRenderingTask(
                    page = page,
                    width = renderWidth,
                    height = renderHeight,
                    bounds = bounds,
                    isThumbnail = false,
                    cacheOrder = cacheOrder,
                    isBestQuality = pdfView.isBestQuality,
                    isAnnotation = pdfView.isAnnotationRendering
                )
            }
            cacheOrder++
            return true
        }
        return false
    }

    private fun loadThumbnail(page: Int) {
        val cacheManager: CacheManager = pdfView.cacheManager ?: return
        val pdfFile: PdfFile = pdfView.pdfFile ?: return
        val renderingHandler: RenderingHandler = pdfView.renderingHandler ?: return
        val pageSize: SizeF = pdfFile.getPageSize(pageIndex = page)
        val thumbnailHeight: Float = pageSize.height * PdfConstants.THUMBNAIL_RATIO
        val thumbnailWidth: Float = pageSize.width * PdfConstants.THUMBNAIL_RATIO

        if (!cacheManager.hasThumbnail(page = page, bounds = thumbnailRect)) {
            renderingHandler.addRenderingTask(
                page = page,
                width = thumbnailWidth,
                height = thumbnailHeight,
                bounds = thumbnailRect,
                isThumbnail = true,
                cacheOrder = 0,
                isBestQuality = pdfView.isBestQuality,
                isAnnotation = pdfView.isAnnotationRendering
            )
        }
    }

    fun loadPages() {
        cacheOrder = 1
        offsetX = -min(a = pdfView.currentXOffset, b = 0f)
        offsetY = -min(a = pdfView.currentYOffset, b = 0f)
        loadVisible()
    }

    private fun floor(a: Float): Int {
        return floor(x = a).toInt()
    }

    private fun ceil(a: Float): Int {
        return ceil(x = a).toInt()
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