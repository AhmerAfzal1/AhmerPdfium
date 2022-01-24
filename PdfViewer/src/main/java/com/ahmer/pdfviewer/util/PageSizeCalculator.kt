package com.ahmer.pdfviewer.util

import com.ahmer.pdfium.util.Size
import com.ahmer.pdfium.util.SizeF
import kotlin.math.floor

class PageSizeCalculator(
    private val fitPolicy: FitPolicy, private val originalWidth: Size,
    private val originalHeight: Size, private val viewSize: Size, private val fitEachPage: Boolean
) {
    var optimalWidth: SizeF? = null
        private set
    var optimalHeight: SizeF? = null
        private set
    private var widthRatio = 0f
    private var heightRatio = 0f

    fun calculate(pageSize: Size): SizeF {
        if (pageSize.width <= 0 || pageSize.height <= 0) {
            return SizeF(0f, 0f)
        }
        val maxWidth = if (fitEachPage) viewSize.width.toFloat() else pageSize.width * widthRatio
        val maxHeight =
            if (fitEachPage) viewSize.height.toFloat() else pageSize.height * heightRatio
        return when (fitPolicy) {
            FitPolicy.HEIGHT -> fitHeight(pageSize, maxHeight)
            FitPolicy.BOTH -> fitBoth(pageSize, maxWidth, maxHeight)
            else -> fitWidth(pageSize, maxWidth)
        }
    }

    private fun calculateMaxPages() {
        when (fitPolicy) {
            FitPolicy.HEIGHT -> {
                optimalHeight = fitHeight(originalHeight, viewSize.height.toFloat())
                heightRatio = optimalHeight!!.height / originalHeight.height
                optimalWidth = fitHeight(originalWidth, originalWidth.height * heightRatio)
            }
            FitPolicy.BOTH -> {
                val localOptimalMaxWidth = fitBoth(
                    originalWidth, viewSize.width.toFloat(), viewSize.height.toFloat()
                )
                val localWidthRatio = localOptimalMaxWidth.width / originalWidth.width
                optimalHeight = fitBoth(
                    originalHeight,
                    originalHeight.width * localWidthRatio,
                    viewSize.height.toFloat()
                )
                heightRatio = optimalHeight!!.height / originalHeight.height
                optimalWidth = fitBoth(
                    originalWidth,
                    viewSize.width.toFloat(),
                    originalWidth.height * heightRatio
                )
                widthRatio = optimalWidth!!.width / originalWidth.width
            }
            else -> {
                optimalWidth = fitWidth(originalWidth, viewSize.width.toFloat())
                widthRatio = optimalWidth!!.width / originalWidth.width
                optimalHeight = fitWidth(originalHeight, originalHeight.width * widthRatio)
            }
        }
    }

    private fun fitWidth(pageSize: Size, maxWidth: Float): SizeF {
        var w = pageSize.width.toFloat()
        var h = pageSize.height.toFloat()
        val ratio = w / h
        w = maxWidth
        h = floor((maxWidth / ratio).toDouble()).toFloat()
        return SizeF(w, h)
    }

    private fun fitHeight(pageSize: Size, maxHeight: Float): SizeF {
        var w = pageSize.width.toFloat()
        var h = pageSize.height.toFloat()
        val ratio = h / w
        h = maxHeight
        w = floor((maxHeight / ratio).toDouble()).toFloat()
        return SizeF(w, h)
    }

    private fun fitBoth(pageSize: Size, maxWidth: Float, maxHeight: Float): SizeF {
        var w = pageSize.width.toFloat()
        var h = pageSize.height.toFloat()
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
        calculateMaxPages()
    }
}