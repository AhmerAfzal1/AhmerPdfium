package com.ahmer.pdfviewer

import android.graphics.RectF
import com.ahmer.pdfium.util.SizeF
import com.ahmer.pdfviewer.util.MathUtils
import com.ahmer.pdfviewer.util.PdfConstants
import com.ahmer.pdfviewer.util.PdfConstants.Cache
import com.ahmer.pdfviewer.util.PdfUtils
import java.util.*
import kotlin.math.abs

internal class PagesLoader(private val pdfView: PDFView) {

    private val mPreLoadOffset: Int = PdfUtils.getDP(pdfView.context, PdfConstants.PRELOAD_OFFSET)
    private val mThumbnailRect = RectF(0f, 0f, 1f, 1f)
    private var mCacheOrder = 0
    private var mOffsetX = 0f
    private var mOffsetY = 0f
    private var mPageRelativePartHeight = 0f
    private var mPageRelativePartWidth = 0f
    private var mPartRenderHeight = 0f
    private var mPartRenderWidth = 0f

    private fun getPageColsRows(grid: GridSize, pageIndex: Int) {
        val mSize: SizeF = pdfView.pdfFile!!.getPageSize(pageIndex)
        val mRatioX: Float = 1f / mSize.width
        val mRatioY: Float = 1f / mSize.height
        val mPartHeight = PdfConstants.PART_SIZE * mRatioY / pdfView.getZoom()
        val mPartWidth = PdfConstants.PART_SIZE * mRatioX / pdfView.getZoom()
        grid.rows = MathUtils.ceil(1f / mPartHeight)
        grid.column = MathUtils.ceil(1f / mPartWidth)
    }

    private fun calculatePartSize(grid: GridSize) {
        mPageRelativePartWidth = 1f / grid.column.toFloat()
        mPageRelativePartHeight = 1f / grid.rows.toFloat()
        mPartRenderWidth = PdfConstants.PART_SIZE / mPageRelativePartWidth
        mPartRenderHeight = PdfConstants.PART_SIZE / mPageRelativePartHeight
    }

    /**
     * Calculate the render range of each page
     */
    private fun getRenderRangeList(
        firstXOffset: Float, firstYOffset: Float, lastXOffset: Float, lastYOffset: Float
    ): List<RenderRange> {
        val mZoom: Float = pdfView.getZoom()
        val mFixedFirstXOffset: Float = -MathUtils.max(firstXOffset, 0f)
        val mFixedFirstYOffset: Float = -MathUtils.max(firstYOffset, 0f)
        val mFixedLastXOffset: Float = -MathUtils.max(lastXOffset, 0f)
        val mFixedLastYOffset: Float = -MathUtils.max(lastYOffset, 0f)
        val mOffsetFirst = if (pdfView.isSwipeVertical()) mFixedFirstYOffset else mFixedFirstXOffset
        val mOffsetLast = if (pdfView.isSwipeVertical()) mFixedLastYOffset else mFixedLastXOffset
        val mPageFirst = pdfView.pdfFile!!.getPageAtOffset(mOffsetFirst, mZoom)
        val mPageLast = pdfView.pdfFile!!.getPageAtOffset(mOffsetLast, mZoom)
        val mPageCount = mPageLast - mPageFirst + 1
        val mRenderRanges: MutableList<RenderRange> = LinkedList()

        for (mPage in mPageFirst..mPageLast) {
            val mRange = RenderRange()
            var mPageFirstXOffset: Float
            var mPageFirstYOffset: Float
            var mPageLastXOffset: Float
            var mPageLastYOffset: Float
            mRange.page = mPage
            if (mPage == mPageFirst) {
                mPageFirstXOffset = mFixedFirstXOffset
                mPageFirstYOffset = mFixedFirstYOffset
                if (mPageCount == 1) {
                    mPageLastXOffset = mFixedLastXOffset
                    mPageLastYOffset = mFixedLastYOffset
                } else {
                    val mPageOffset = pdfView.pdfFile!!.getPageOffset(mPage, mZoom)
                    val mPageSize = pdfView.pdfFile!!.getScaledPageSize(mPage, mZoom)
                    if (pdfView.isSwipeVertical()) {
                        mPageLastXOffset = mFixedLastXOffset
                        mPageLastYOffset = mPageOffset + mPageSize.height
                    } else {
                        mPageLastYOffset = mFixedLastYOffset
                        mPageLastXOffset = mPageOffset + mPageSize.width
                    }
                }
            } else if (mPage == mPageLast) {
                val mPageOffset = pdfView.pdfFile!!.getPageOffset(mPage, mZoom)
                if (pdfView.isSwipeVertical()) {
                    mPageFirstXOffset = mFixedFirstXOffset
                    mPageFirstYOffset = mPageOffset
                } else {
                    mPageFirstYOffset = mFixedFirstYOffset
                    mPageFirstXOffset = mPageOffset
                }
                mPageLastXOffset = mFixedLastXOffset
                mPageLastYOffset = mFixedLastYOffset
            } else {
                val mPageOffset = pdfView.pdfFile!!.getPageOffset(mPage, mZoom)
                val mPageSize = pdfView.pdfFile!!.getScaledPageSize(mPage, mZoom)
                if (pdfView.isSwipeVertical()) {
                    mPageFirstXOffset = mFixedFirstXOffset
                    mPageFirstYOffset = mPageOffset
                    mPageLastXOffset = mFixedLastXOffset
                    mPageLastYOffset = mPageOffset + mPageSize.height
                } else {
                    mPageFirstXOffset = mPageOffset
                    mPageFirstYOffset = mFixedFirstYOffset
                    mPageLastXOffset = mPageOffset + mPageSize.width
                    mPageLastYOffset = mFixedLastYOffset
                }
            }
            // Get the page's grid size that rows and cols
            getPageColsRows(mRange.gridSize, mRange.page)
            val mScalePageSize = pdfView.pdfFile!!.getScaledPageSize(mRange.page, mZoom)
            val mColWidth: Float = mScalePageSize.width / mRange.gridSize.column
            val mRowHeight: Float = mScalePageSize.height / mRange.gridSize.rows
            val mSecondaryOffset = pdfView.pdfFile!!.getSecondaryPageOffset(mPage, mZoom)
            // Get the page offset int the whole file
            // ---------------------------------------
            // |            |           |            |
            // |<--offset-->|   (page)  |<--offset-->|
            // |            |           |            |
            // |            |           |            |
            // ---------------------------------------
            // Calculate the row,col of the point in the leftTop and rightBottom
            if (pdfView.isSwipeVertical()) {
                mRange.leftTop.row = MathUtils.floor(
                    abs(
                        mPageFirstYOffset - pdfView.pdfFile!!.getPageOffset(mRange.page, mZoom)
                    ) / mRowHeight
                )
                mRange.leftTop.column = MathUtils.floor(
                    MathUtils.min(mPageFirstXOffset - mSecondaryOffset, 0f) / mColWidth
                )
                mRange.rightBottom.row = MathUtils.ceil(
                    abs(
                        mPageLastYOffset - pdfView.pdfFile!!.getPageOffset(mRange.page, mZoom)
                    ) / mRowHeight
                )
                mRange.rightBottom.column = MathUtils.floor(
                    MathUtils.min(mPageLastXOffset - mSecondaryOffset, 0f) / mColWidth
                )
            } else {
                mRange.leftTop.column = MathUtils.floor(
                    abs(
                        mPageFirstXOffset - pdfView.pdfFile!!.getPageOffset(mRange.page, mZoom)
                    ) / mColWidth
                )
                mRange.leftTop.row = MathUtils.floor(
                    MathUtils.min(
                        if (mPageFirstYOffset > 0) mPageFirstYOffset - mSecondaryOffset else 0f, 0f
                    ) / mRowHeight
                )
                mRange.rightBottom.column = MathUtils.floor(
                    abs(
                        mPageLastXOffset - pdfView.pdfFile!!.getPageOffset(mRange.page, mZoom)
                    ) / mColWidth
                )
                mRange.rightBottom.row = MathUtils.floor(
                    MathUtils.min(mPageLastYOffset - mSecondaryOffset, 0f) / mRowHeight
                )
            }
            mRenderRanges.add(mRange)
        }
        return mRenderRanges
    }

    private fun loadVisible() {
        val mScaledPreloadOffset: Float = mPreLoadOffset.toFloat()
        val mFirstOffsetX: Float = -mOffsetX + mScaledPreloadOffset
        val mFirstOffsetY: Float = -mOffsetY + mScaledPreloadOffset
        val mLastOffsetX: Float = -mOffsetX - pdfView.width - mScaledPreloadOffset
        val mLastOffsetY: Float = -mOffsetY - pdfView.height - mScaledPreloadOffset
        val mRangeList =
            getRenderRangeList(mFirstOffsetX, mFirstOffsetY, mLastOffsetX, mLastOffsetY)
        var mParts = 0
        for (mRange in mRangeList) {
            loadThumbnail(mRange.page)
        }
        for (mRange in mRangeList) {
            calculatePartSize(mRange.gridSize)
            mParts += loadPage(
                mRange.page, mRange.leftTop.row, mRange.rightBottom.row, mRange.leftTop.column,
                mRange.rightBottom.column, Cache.CACHE_SIZE - mParts
            )
            if (mParts >= Cache.CACHE_SIZE) break
        }
    }

    private fun loadPage(
        page: Int, firstRow: Int, lastRow: Int, firstColumn: Int, lastColumn: Int,
        nbOfPartsLoadable: Int
    ): Int {
        var mLoaded = 0
        for (mRow in firstRow..lastRow) {
            for (mColumn in firstColumn..lastColumn) {
                if (loadCell(
                        page, mRow, mColumn, mPageRelativePartWidth, mPageRelativePartHeight
                    )
                ) {
                    mLoaded++
                }
                if (mLoaded >= nbOfPartsLoadable) return mLoaded
            }
        }
        return mLoaded
    }

    private fun loadCell(
        page: Int, row: Int, col: Int, pageRelativePartWidth: Float, pageRelativePartHeight: Float
    ): Boolean {
        val mRelX: Float = pageRelativePartWidth * col
        val mRelY: Float = pageRelativePartHeight * row
        var mRelHeight: Float = pageRelativePartHeight
        var mRelWidth: Float = pageRelativePartWidth
        var mRenderHeight: Float = mPartRenderHeight
        var mRenderWidth: Float = mPartRenderWidth

        if (mRelX + mRelWidth > 1) mRelWidth = 1 - mRelX
        if (mRelY + mRelHeight > 1) mRelHeight = 1 - mRelY
        mRenderHeight *= mRelHeight
        mRenderWidth *= mRelWidth
        val bounds = RectF(mRelX, mRelY, mRelX + mRelWidth, mRelY + mRelHeight)
        if (mRenderWidth > 0 && mRenderHeight > 0) {
            if (!pdfView.cacheManager?.upPartIfContained(page, bounds, mCacheOrder)!!) {
                pdfView.renderingHandler?.addRenderingTask(
                    page, mRenderWidth, mRenderHeight, bounds, false, mCacheOrder,
                    pdfView.isBestQuality(), pdfView.isAnnotationRendering()
                )
            }
            mCacheOrder++
            return true
        }
        return false
    }

    private fun loadThumbnail(page: Int) {
        val mPageSize: SizeF = pdfView.pdfFile!!.getPageSize(page)
        val mThumbnailHeight: Float = mPageSize.height * PdfConstants.THUMBNAIL_RATIO
        val mThumbnailWidth: Float = mPageSize.width * PdfConstants.THUMBNAIL_RATIO
        if (!pdfView.cacheManager?.containsThumbnail(page, mThumbnailRect)!!) {
            pdfView.renderingHandler?.addRenderingTask(
                page, mThumbnailWidth, mThumbnailHeight, mThumbnailRect, true, 0,
                pdfView.isBestQuality(), pdfView.isAnnotationRendering()
            )
        }
    }

    fun loadPages() {
        mCacheOrder = 1
        mOffsetX = -MathUtils.max(pdfView.getCurrentXOffset(), 0f)
        mOffsetY = -MathUtils.max(pdfView.getCurrentYOffset(), 0f)
        loadVisible()
    }

    private class Holder {
        var column = 0
        var row = 0
        override fun toString(): String {
            return "Holder{row=$row, col=$column}"
        }
    }

    private inner class RenderRange {
        val gridSize: GridSize = GridSize()
        val leftTop: Holder = Holder()
        val rightBottom: Holder = Holder()
        var page = 0

        override fun toString(): String {
            return "RenderRange{page=$page, gridSize=$gridSize, leftTop=$leftTop, rightBottom=$rightBottom}"
        }
    }

    private class GridSize {
        var column = 0
        var rows = 0
        override fun toString(): String {
            return "GridSize{rows=$rows, cols=$column}"
        }
    }
}