package com.ahmer.pdfviewer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import androidx.core.graphics.createBitmap
import com.ahmer.pdfviewer.exception.PageRenderingException
import com.ahmer.pdfviewer.model.PagePart
import com.ahmer.pdfviewer.util.PdfConstants
import kotlin.math.roundToInt

class RenderingHandler(looper: Looper, private val pdfView: PDFView) : Handler(looper) {
    private val bounds: RectF = RectF()
    private val renderMatrix: Matrix = Matrix()
    private val roundedBounds: Rect = Rect()
    private var isRunning: Boolean = false

    private fun getBitmapConfig(isBestQuality: Boolean): Bitmap.Config {
        return if (isBestQuality) Bitmap.Config.ARGB_8888 else Bitmap.Config.RGB_565
    }

    fun addRenderingTask(
        page: Int, width: Float, height: Float, bounds: RectF, isThumbnail: Boolean,
        cacheOrder: Int, isBestQuality: Boolean, isAnnotation: Boolean
    ) {
        val task = RenderingTask(
            page = page,
            width = width,
            height = height,
            bounds = bounds,
            isThumbnail = isThumbnail,
            cacheOrder = cacheOrder,
            isBestQuality = isBestQuality,
            isAnnotation = isAnnotation
        )
        val message: Message = obtainMessage(MSG_RENDER_PART_TASK, task)
        sendMessage(message)
    }

    override fun handleMessage(message: Message) {
        val bitmapPart: PagePart? = proceed(task = message.obj as RenderingTask)
        try {
            if (bitmapPart != null) {
                if (isRunning) {
                    pdfView.post { pdfView.onBitmapRendered(part = bitmapPart) }
                } else {
                    bitmapPart.renderedBitmap?.recycle()
                }
            }
        } catch (ex: PageRenderingException) {
            pdfView.post { pdfView.onPageError(ex = ex) }
        }
    }

    private fun toNightMode(bitmap: Bitmap, bestQuality: Boolean): Bitmap {
        val config: Bitmap.Config = getBitmapConfig(isBestQuality = bestQuality)
        val newBitmap: Bitmap = createBitmap(width = bitmap.width, height = bitmap.height, config = config)
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix().apply {
                postConcat(ColorMatrix().apply { setSaturation(0f) })
                postConcat(
                    ColorMatrix(
                        floatArrayOf(
                            -1f, 0f, 0f, 0f, 255f,
                            0f, -1f, 0f, 0f, 255f,
                            0f, 0f, -1f, 0f, 255f,
                            0f, 0f, 0f, 1f, 0f
                        )
                    )
                )
            })
        }
        Canvas(newBitmap).apply {
            drawBitmap(bitmap, 0f, 0f, paint)
        }
        bitmap.recycle()
        return newBitmap
    }

    @Throws(PageRenderingException::class)
    private fun proceed(task: RenderingTask): PagePart? {
        val pdfFile: PdfFile = pdfView.pdfFile ?: return null
        pdfFile.openPage(pageIndex = task.page)

        val width: Int = task.width.roundToInt()
        val height: Int = task.height.roundToInt()
        if (width == 0 || height == 0 || pdfFile.pageHasError(page = task.page)) return null

        var bitmap: Bitmap
        bitmap = try {
            createBitmap(width = width, height = height, config = getBitmapConfig(isBestQuality = task.isBestQuality))
        } catch (e: IllegalArgumentException) {
            Log.e(PdfConstants.TAG, "Cannot create bitmap", e)
            return null
        }

        calculateBounds(width = width, height = height, sliceRect = task.bounds)
        pdfFile.renderPageBitmap(
            pageIndex = task.page,
            bitmap = bitmap,
            bounds = roundedBounds,
            isAnnotation = task.isAnnotation
        )
        if (pdfView.isNightMode) {
            bitmap = toNightMode(bitmap = bitmap, bestQuality = task.isBestQuality)
        }
        return PagePart(
            page = task.page,
            renderedBitmap = bitmap,
            pageBounds = task.bounds,
            isThumbnail = task.isThumbnail,
            cacheOrder = task.cacheOrder
        )
    }

    private fun calculateBounds(width: Int, height: Int, sliceRect: RectF) {
        renderMatrix.apply {
            reset()
            postTranslate(-sliceRect.left * width, -sliceRect.top * height)
            postScale(1 / sliceRect.width(), 1 / sliceRect.height())
            mapRect(bounds.apply { set(0f, 0f, width.toFloat(), height.toFloat()) })
        }
        bounds.round(roundedBounds)
    }

    fun stop() {
        isRunning = false
    }

    fun start() {
        isRunning = true
    }

    private class RenderingTask(
        val page: Int,
        val width: Float,
        val height: Float,
        val bounds: RectF,
        val isThumbnail: Boolean,
        val cacheOrder: Int,
        val isBestQuality: Boolean,
        val isAnnotation: Boolean
    )

    companion object {
        const val MSG_RENDER_PART_TASK = 1
    }
}