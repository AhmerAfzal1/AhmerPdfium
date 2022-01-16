package com.ahmer.pdfviewer

import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import com.ahmer.pdfviewer.RenderingHandler.RenderingTask
import com.ahmer.pdfviewer.exception.PageRenderingException
import com.ahmer.pdfviewer.model.PagePart
import kotlin.math.roundToInt


/**
 * A [Handler] that will process incoming [RenderingTask] messages
 * and alert [PDFView.onBitmapRendered] when the portion of the
 * PDF is ready to render.
 */
class RenderingHandler(looper: Looper?, pdfView: PDFView) : Handler(
    looper!!
) {
    private val mPdfView: PDFView = pdfView
    private val renderBounds = RectF()
    private val roundedRenderBounds = Rect()
    private val renderMatrix = Matrix()
    private var running = false

    fun addRenderingTask(
        page: Int, width: Float, height: Float, bounds: RectF, thumbnail: Boolean,
        cacheOrder: Int, bestQuality: Boolean, annotationRendering: Boolean
    ) {
        val task = RenderingTask(
            width, height, bounds, page, thumbnail, cacheOrder,
            bestQuality, annotationRendering
        )
        val msg = obtainMessage(MSG_RENDER_TASK, task)
        sendMessage(msg)
    }

    override fun handleMessage(message: Message) {
        if (!running) {
            return
        }
        val task = message.obj as RenderingTask
        try {
            val part: PagePart? = proceed(task)
            if (part != null) {
                if (running) {
                    mPdfView.post(Runnable {
                        if (running) {
                            mPdfView.onBitmapRendered(part)
                        } else {
                            part.renderedBitmap?.recycle()
                        }
                    })
                } else {
                    part.renderedBitmap?.recycle()
                }
            }
        } catch (ex: PageRenderingException) {
            mPdfView.post(Runnable {
                if (running) {
                    mPdfView.onPageError(ex)
                }
            })
        }
    }

    @Throws(PageRenderingException::class)
    private fun proceed(renderingTask: RenderingTask): PagePart? {
        val pdfFile: PdfFile? = mPdfView.pdfFile
        pdfFile?.openPage(renderingTask.page)
        val w = renderingTask.width.roundToInt()
        val h = renderingTask.height.roundToInt()
        if (pdfFile != null) {
            if (w == 0 || h == 0 || pdfFile.pageHasError(renderingTask.page)) {
                return null
            }
        }
        var render: Bitmap
        render = try {
            Bitmap.createBitmap(
                w,
                h,
                if (renderingTask.bestQuality) Bitmap.Config.ARGB_8888 else Bitmap.Config.RGB_565
            )
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Cannot create bitmap", e)
            return null
        }
        calculateBounds(w, h, renderingTask.bounds)
        pdfFile?.renderPageBitmap(
            render, renderingTask.page,
            roundedRenderBounds, renderingTask.annotationRendering
        )
        if (mPdfView.isNightMode) {
            render = toNightMode(render, renderingTask.bestQuality)
        }
        return PagePart(
            renderingTask.page, render, renderingTask.bounds,
            renderingTask.thumbnail, renderingTask.cacheOrder
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
        removeMessages(MSG_RENDER_TASK)
    }

    fun purge() {
        removeMessages(MSG_RENDER_TASK)
    }

    fun start() {
        running = true
    }

    private class RenderingTask internal constructor(
        val width: Float,
        val height: Float,
        val bounds: RectF,
        val page: Int,
        val thumbnail: Boolean,
        val cacheOrder: Int,
        val bestQuality: Boolean,
        val annotationRendering: Boolean
    )

    companion object {
        /**
         * [Message.what] kind of message this handler processes.
         */
        const val MSG_RENDER_TASK = 1
        private val TAG = RenderingHandler::class.java.name
        private fun toNightMode(bmpOriginal: Bitmap, bestQuality: Boolean): Bitmap {
            val width: Int
            val height: Int
            height = bmpOriginal.height
            width = bmpOriginal.width
            val nightModeBitmap = Bitmap.createBitmap(
                width, height,
                if (bestQuality) Bitmap.Config.ARGB_8888 else Bitmap.Config.RGB_565
            )
            val canvas = Canvas(nightModeBitmap)
            val paint = Paint()
            val grayScaleMatrix = ColorMatrix()
            grayScaleMatrix.setSaturation(0f)
            val invertMatrix = ColorMatrix(
                floatArrayOf(
                    -1f,
                    0f,
                    0f,
                    0f,
                    255f,
                    0f,
                    -1f,
                    0f,
                    0f,
                    255f,
                    0f,
                    0f,
                    -1f,
                    0f,
                    255f,
                    0f,
                    0f,
                    0f,
                    1f,
                    0f
                )
            )
            val nightModeMatrix = ColorMatrix()
            nightModeMatrix.postConcat(grayScaleMatrix)
            nightModeMatrix.postConcat(invertMatrix)
            paint.colorFilter = ColorMatrixColorFilter(nightModeMatrix)
            canvas.drawBitmap(bmpOriginal, 0f, 0f, paint)
            return nightModeBitmap
        }
    }

}
