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
    private var _optimalHeight: SizeF = SizeF(width = 0f, height = 0f)
    private var _optimalWidth: SizeF = SizeF(width = 0f, height = 0f)
    private var heightRatio: Float = 0f
    private var widthRatio: Float = 0f
    val optimalHeight: SizeF
        get() = _optimalHeight

    val optimalWidth: SizeF
        get() = _optimalWidth

    fun calculate(pageSize: Size): SizeF {
        if (pageSize.width <= 0 || pageSize.height <= 0) return SizeF(width = 0f, height = 0f)

        val maxWidth = if (fitEachPage) viewSize.width.toFloat() else pageSize.width * widthRatio
        val maxHeight =
            if (fitEachPage) viewSize.height.toFloat() else pageSize.height * heightRatio

        return when (fitPolicy) {
            FitPolicy.HEIGHT -> fitHeight(pageSize = pageSize, maxHeight = maxHeight)
            FitPolicy.BOTH -> fitBoth(
                pageSize = pageSize, maxWidth = maxWidth, maxHeight = maxHeight
            )

            else -> fitWidth(pageSize = pageSize, maxWidth = maxWidth)
        }
    }

    private fun calculateMaxPages() {
        when (fitPolicy) {
            FitPolicy.HEIGHT -> {
                _optimalHeight =
                    fitHeight(pageSize = originalHeight, maxHeight = viewSize.height.toFloat())
                heightRatio = _optimalHeight.height / originalHeight.height
                _optimalWidth = fitHeight(
                    pageSize = originalWidth,
                    maxHeight = originalWidth.height * heightRatio
                )
                widthRatio = _optimalWidth.width / originalWidth.width
            }

            FitPolicy.BOTH -> {
                _optimalWidth = fitBoth(
                    pageSize = originalWidth,
                    maxWidth = viewSize.width.toFloat(),
                    maxHeight = viewSize.height.toFloat()
                )
                val tempWidthRatio = _optimalWidth.width / originalWidth.width
                _optimalHeight = fitBoth(
                    pageSize = originalHeight,
                    maxWidth = originalHeight.width * tempWidthRatio,
                    maxHeight = viewSize.height.toFloat()
                )
                heightRatio = _optimalHeight.height / originalHeight.height
                _optimalWidth = fitBoth(
                    pageSize = originalWidth,
                    maxWidth = viewSize.width.toFloat(),
                    maxHeight = originalWidth.height * heightRatio
                )
                widthRatio = _optimalWidth.width / originalWidth.width
            }

            else -> {
                _optimalWidth = fitWidth(
                    pageSize = originalWidth,
                    maxWidth = viewSize.width.toFloat()
                )
                widthRatio = _optimalWidth.width / originalWidth.width
                _optimalHeight = fitWidth(
                    pageSize = originalHeight,
                    maxWidth = originalHeight.width * widthRatio
                )
                heightRatio = _optimalHeight.height / originalHeight.height
            }
        }
    }

    private fun fitWidth(pageSize: Size, maxWidth: Float): SizeF {
        val ratio: Float = pageSize.width.toFloat() / pageSize.height.toFloat()
        return SizeF(width = maxWidth, height = floor(x = (maxWidth / ratio).toDouble()).toFloat())
    }

    private fun fitHeight(pageSize: Size, maxHeight: Float): SizeF {
        val ratio: Float = pageSize.height.toFloat() / pageSize.width.toFloat()
        return SizeF(
            width = floor(x = (maxHeight / ratio).toDouble()).toFloat(),
            height = maxHeight
        )
    }

    private fun fitBoth(pageSize: Size, maxWidth: Float, maxHeight: Float): SizeF {
        val ratio: Float = pageSize.width.toFloat() / pageSize.height.toFloat()
        var width: Float = maxWidth
        var height: Float = floor(x = width / ratio)

        if (height > maxHeight) {
            height = maxHeight
            width = floor(x = height * ratio)
        }
        return SizeF(width = width, height = height)
    }

    init {
        calculateMaxPages()
    }
}