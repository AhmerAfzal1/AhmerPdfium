package com.ahmer.pdfviewer.util

import com.ahmer.pdfium.util.Size
import com.ahmer.pdfium.util.SizeF
import kotlin.math.floor

class PageSizeCalculator(
    private val fitPolicy: FitPolicy,
    private val originalWidth: Size,
    private val originalHeight: Size,
    private val viewSize: Size,
    private val fitEachPage: Boolean
) {
    var mOptimalHeight: SizeF? = null
        private set
    var mOptimalWidth: SizeF? = null
        private set
    private var mHeightRatio = 0f
    private var mWidthRatio = 0f

    fun calculate(pageSize: Size): SizeF {
        if (pageSize.width <= 0 || pageSize.height <= 0) return SizeF(0f, 0f)
        val maxWidth = if (fitEachPage) viewSize.width.toFloat() else pageSize.width * mWidthRatio
        val maxHeight =
            if (fitEachPage) viewSize.height.toFloat() else pageSize.height * mHeightRatio
        return when (fitPolicy) {
            FitPolicy.HEIGHT -> fitHeight(pageSize, maxHeight)
            FitPolicy.BOTH -> fitBoth(pageSize, maxWidth, maxHeight)
            else -> fitWidth(pageSize, maxWidth)
        }
    }

    private fun calculateMaxPages() {
        when (fitPolicy) {
            FitPolicy.HEIGHT -> {
                mOptimalHeight = fitHeight(originalHeight, viewSize.height.toFloat())
                mHeightRatio = mOptimalHeight!!.height / originalHeight.height
                mOptimalWidth = fitHeight(originalWidth, originalWidth.height * mHeightRatio)
            }

            FitPolicy.BOTH -> {
                val localOptimalMaxWidth = fitBoth(
                    originalWidth, viewSize.width.toFloat(), viewSize.height.toFloat()
                )
                val localWidthRatio = localOptimalMaxWidth.width / originalWidth.width
                mOptimalHeight = fitBoth(
                    originalHeight, originalHeight.width * localWidthRatio,
                    viewSize.height.toFloat()
                )
                mHeightRatio = mOptimalHeight!!.height / originalHeight.height
                mOptimalWidth = fitBoth(
                    originalWidth, viewSize.width.toFloat(), originalWidth.height * mHeightRatio
                )
                mWidthRatio = mOptimalWidth!!.width / originalWidth.width
            }

            else -> {
                mOptimalWidth = fitWidth(originalWidth, viewSize.width.toFloat())
                mWidthRatio = mOptimalWidth!!.width / originalWidth.width
                val newPageSize = fitWidth(originalHeight, originalHeight.width * mWidthRatio)
                if (mOptimalHeight == null || newPageSize.height > mOptimalHeight!!.height) {
                    mOptimalHeight = newPageSize
                }
            }
        }
    }

    private fun fitWidth(pageSize: Size, maxWidth: Float): SizeF {
        var mHeight: Float = pageSize.height.toFloat()
        var mWidth: Float = pageSize.width.toFloat()
        val mRatio: Float = mWidth / mHeight
        mWidth = maxWidth
        mHeight = floor((maxWidth / mRatio).toDouble()).toFloat()
        return SizeF(mWidth, mHeight)
    }

    private fun fitHeight(pageSize: Size, maxHeight: Float): SizeF {
        var mHeight: Float = pageSize.height.toFloat()
        var mWidth: Float = pageSize.width.toFloat()
        val mRatio: Float = mHeight / mWidth
        mHeight = maxHeight
        mWidth = floor((maxHeight / mRatio).toDouble()).toFloat()
        return SizeF(mWidth, mHeight)
    }

    private fun fitBoth(pageSize: Size, maxWidth: Float, maxHeight: Float): SizeF {
        var mHeight: Float = pageSize.height.toFloat()
        var mWidth: Float = pageSize.width.toFloat()
        val mRatio: Float = mWidth / mHeight
        mWidth = maxWidth
        mHeight = floor((maxWidth / mRatio).toDouble()).toFloat()
        if (mHeight > maxHeight) {
            mHeight = maxHeight
            mWidth = floor((maxHeight * mRatio).toDouble()).toFloat()
        }
        return SizeF(mWidth, mHeight)
    }

    init {
        calculateMaxPages()
    }
}