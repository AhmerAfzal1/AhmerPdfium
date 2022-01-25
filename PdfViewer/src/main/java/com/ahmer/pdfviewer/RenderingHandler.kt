package com.ahmer.pdfviewer

import android.graphics.*
import android.util.Log
import com.ahmer.pdfviewer.exception.PageRenderingException
import com.ahmer.pdfviewer.model.PagePart
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext
import kotlin.math.roundToInt

class RenderingHandler(private val pdfView: PDFView) : CoroutineScope {
    private val renderBounds = RectF()
    private val roundedRenderBounds = Rect()
    private val renderMatrix = Matrix()
    private var running = false

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + Job()

    fun addRenderingTask(
        page: Int, width: Float, height: Float, bounds: RectF, thumbnail: Boolean, cacheOrder: Int,
        bestQuality: Boolean, annotation: Boolean, searchQuery: String
    ) {
        CoroutineScope(coroutineContext).launch(SupervisorJob()) {
            val task = RenderingTask(
                width, height, bounds, page, thumbnail, cacheOrder, bestQuality,
                annotation, searchQuery
            )
            val part = proceed(task)
            try {
                if (part != null) {
                    if (running) {
                        pdfView.post { pdfView.onBitmapRendered(part) }
                    } else {
                        part.renderedBitmap?.recycle()
                    }
                } else {
                    part?.renderedBitmap?.recycle()
                }
            } catch (ex: PageRenderingException) {
                pdfView.post { pdfView.onPageError(ex) }
            }
        }
    }

    @Throws(PageRenderingException::class)
    private fun parseText(task: RenderingTask): String? {
        val pdfFile = pdfView.pdfFile
        pdfFile?.openPage(task.page)
        val countCharsOnPage = pdfFile?.countCharactersOnPage(task.page) ?: 0
        return if (countCharsOnPage > 0) {
            pdfFile?.extractCharacters(task.page, 0, countCharsOnPage)
        } else {
            null
        }
    }

    private fun toNightMode(bmpOriginal: Bitmap, bestQuality: Boolean): Bitmap {
        val height: Int = bmpOriginal.height
        val width: Int = bmpOriginal.width
        val nightModeBitmap = Bitmap.createBitmap(
            width, height,
            if (bestQuality) Bitmap.Config.ARGB_8888 else Bitmap.Config.RGB_565
        )
        val canvas = Canvas(nightModeBitmap)
        val paint = Paint()
        val grayScaleMatrix = ColorMatrix().apply {
            setSaturation(0f)
        }
        val invertMatrix = ColorMatrix(
            floatArrayOf(
                -1f, 0f, 0f, 0f, 255f,
                0f, -1f, 0f, 0f, 255f,
                0f, 0f, -1f, 0f, 255f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        val nightModeMatrix = ColorMatrix().apply {
            postConcat(grayScaleMatrix)
            postConcat(invertMatrix)
        }
        paint.colorFilter = ColorMatrixColorFilter(nightModeMatrix)
        canvas.drawBitmap(bmpOriginal, 0f, 0f, paint)
        return nightModeBitmap
    }

    @Throws(PageRenderingException::class)
    private fun proceed(task: RenderingTask): PagePart? {
        val pdfFile = pdfView.pdfFile
        pdfFile?.openPage(task.page)
        val width = task.width.roundToInt()
        val height = task.height.roundToInt()
        if (width == 0 || height == 0 || pdfFile?.pageHasError(task.page) == true) {
            return null
        }
        val bitmapQuality = if (task.bestQuality) Bitmap.Config.ARGB_8888 else Bitmap.Config.RGB_565
        var render: Bitmap
        render = try {
            Bitmap.createBitmap(width, height, bitmapQuality)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Cannot create bitmap", e)
            return null
        }
        calculateBounds(width, height, task.bounds)
        val searchQuery = task.searchQuery
        pdfFile?.renderPageBitmap(render, task.page, roundedRenderBounds, task.annotation)
        if (pdfView.isNightMode()) {
            render = toNightMode(render, task.bestQuality)
        }
        return PagePart(
            task.page, render, task.bounds, task.thumbnail, task.cacheOrder, searchQuery
        )
    }

    private fun calculateBounds(width: Int, height: Int, pageSliceBounds: RectF) {
        renderMatrix.reset()
        renderMatrix.postTranslate(-pageSliceBounds.left * width, -pageSliceBounds.top * height)
        renderMatrix.postScale(1 / pageSliceBounds.width(), 1 / pageSliceBounds.height())
        renderBounds[0f, 0f, width.toFloat()] = height.toFloat()
        renderMatrix.mapRect(renderBounds)
        renderBounds.round(roundedRenderBounds)
    }

    fun stop() {
        running = false
    }

    fun start() {
        running = true
    }

    private class RenderingTask(
        val width: Float,
        val height: Float,
        val bounds: RectF,
        val page: Int,
        val thumbnail: Boolean,
        val cacheOrder: Int,
        val bestQuality: Boolean,
        val annotation: Boolean,
        val searchQuery: String
    )

    companion object {
        private val TAG = RenderingHandler::class.java.name
    }
}