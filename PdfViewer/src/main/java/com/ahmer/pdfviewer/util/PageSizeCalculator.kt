package com.ahmer.pdfviewer.util

import com.ahmer.pdfium.util.Size
import com.ahmer.pdfium.util.SizeF
import kotlin.math.floor

class PageSizeCalculator(
    private val fitPolicy: FitPolicy, originalMaxWidthPageSize: Size,
    originalMaxHeightPageSize: Size, viewSize: Size, fitEachPage: Boolean
) {
    private val originalMaxWidthPageSize: Size
    private val originalMaxHeightPageSize: Size
    private val viewSize: Size
    private val fitEachPage: Boolean
    private var optimalMaxWidthPageSize: SizeF? = null
    private var optimalMaxHeightPageSize: SizeF? = null
    private var widthRatio = 0f
    private var heightRatio = 0f
    fun calculate(pageSize: Size): SizeF {
        if (pageSize.width <= 0 || pageSize.height <= 0) {
            return SizeF(0f, 0f)
        }
        val maxWidth: Float = if (fitEachPage) viewSize.width.toFloat() else pageSize.width * widthRatio
        val maxHeight: Float = if (fitEachPage) viewSize.height.toFloat() else pageSize.height * heightRatio
        return when (fitPolicy) {
            FitPolicy.HEIGHT -> fitHeight(pageSize, maxHeight)
            FitPolicy.BOTH -> fitBoth(pageSize, maxWidth, maxHeight)
            else -> fitWidth(pageSize, maxWidth)
        }
    }

    fun getOptimalMaxWidthPageSize(): SizeF? {
        return optimalMaxWidthPageSize
    }

    fun getOptimalMaxHeightPageSize(): SizeF? {
        return optimalMaxHeightPageSize
    }

    private fun calculateMaxPages() {
        when (fitPolicy) {
            FitPolicy.HEIGHT -> {
                optimalMaxHeightPageSize =
                    fitHeight(originalMaxHeightPageSize, viewSize.height.toFloat())
                heightRatio = optimalMaxHeightPageSize!!.height / originalMaxHeightPageSize.height
                optimalMaxWidthPageSize = fitHeight(
                    originalMaxWidthPageSize,
                    originalMaxWidthPageSize.height * heightRatio
                )
            }
            FitPolicy.BOTH -> {
                val localOptimalMaxWidth: SizeF =
                    fitBoth(originalMaxWidthPageSize, viewSize.width.toFloat(), viewSize.height.toFloat())
                val localWidthRatio: Float =
                    localOptimalMaxWidth.width / originalMaxWidthPageSize.width
                optimalMaxHeightPageSize = fitBoth(
                    originalMaxHeightPageSize,
                    originalMaxHeightPageSize.width * localWidthRatio, viewSize.height.toFloat()
                )
                heightRatio =
                    optimalMaxHeightPageSize!!.height / originalMaxHeightPageSize.height
                optimalMaxWidthPageSize = fitBoth(
                    originalMaxWidthPageSize, viewSize.width.toFloat(),
                    originalMaxWidthPageSize.height * heightRatio
                )
                widthRatio =
                    optimalMaxWidthPageSize!!.width / originalMaxWidthPageSize.width
            }
            else -> {
                optimalMaxWidthPageSize = fitWidth(originalMaxWidthPageSize, viewSize.width.toFloat())
                widthRatio =
                    optimalMaxWidthPageSize!!.width / originalMaxWidthPageSize.width
                val newPageSize: SizeF = fitWidth(
                    originalMaxHeightPageSize, originalMaxHeightPageSize.width * widthRatio
                )
                if (optimalMaxHeightPageSize == null || newPageSize.height > optimalMaxHeightPageSize!!.height) {
                    optimalMaxHeightPageSize = newPageSize
                }
            }
        }
    }

    private fun fitWidth(pageSize: Size, maxWidth: Float): SizeF {
        var w: Float = pageSize.width.toFloat()
        var h: Float = pageSize.height.toFloat()
        val ratio = w / h
        w = maxWidth
        h = floor((maxWidth / ratio).toDouble()).toFloat()
        return SizeF(w, h)
    }

    private fun fitHeight(pageSize: Size, maxHeight: Float): SizeF {
        var w: Float = pageSize.width.toFloat()
        var h: Float = pageSize.height.toFloat()
        val ratio = h / w
        h = maxHeight
        w = floor((maxHeight / ratio).toDouble()).toFloat()
        return SizeF(w, h)
    }

    private fun fitBoth(pageSize: Size, maxWidth: Float, maxHeight: Float): SizeF {
        var w: Float = pageSize.width.toFloat()
        var h: Float = pageSize.height.toFloat()
        val ratio = w / h
        w = maxWidth
        h = floor((maxWidth / ratio).toDouble()).toFloat()
        if (h > maxHeight) {
            h = maxHeight
            w = floor((maxHeight * ratio).toDouble()).toFloat()
        }
        return SizeF(w, h)
    }

    init {
        this.originalMaxWidthPageSize = originalMaxWidthPageSize
        this.originalMaxHeightPageSize = originalMaxHeightPageSize
        this.viewSize = viewSize
        this.fitEachPage = fitEachPage
        calculateMaxPages()
    }
}
