package com.ahmer.pdfviewer

import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import com.ahmer.pdfviewer.exception.PageRenderingException
import com.ahmer.pdfviewer.model.PagePart
import com.ahmer.pdfviewer.util.PdfConstants
import kotlin.math.roundToInt

class RenderingHandler(looper: Looper, private val pdfView: PDFView) : Handler(looper) {
    private val mBounds: RectF = RectF()
    private val mRenderMatrix: Matrix = Matrix()
    private val mRoundedBounds: Rect = Rect()
    private var isRunning: Boolean = false

    fun addRenderingTask(
        page: Int, width: Float, height: Float, bounds: RectF, isThumbnail: Boolean,
        cacheOrder: Int, isBestQuality: Boolean, isAnnotation: Boolean
    ) {
        val task = RenderingTask(
            width, height, bounds, page, isThumbnail, cacheOrder, isBestQuality, isAnnotation
        )
        val mMessage = obtainMessage(MSG_RENDER_PART_TASK, task)
        sendMessage(mMessage)
    }

    override fun handleMessage(message: Message) {
        val mTask = message.obj as RenderingTask
        val mPart = proceed(mTask)
        try {
            if (mPart != null) {
                if (isRunning) {
                    pdfView.post { pdfView.onBitmapRendered(mPart) }
                } else {
                    mPart.renderedBitmap?.recycle()
                }
            } else {
                mPart?.renderedBitmap?.recycle()
            }
        } catch (ex: PageRenderingException) {
            pdfView.post { pdfView.onPageError(ex) }
        }
    }

    private fun toNightMode(bmpOriginal: Bitmap, bestQuality: Boolean): Bitmap {
        val mBitmapQuality = if (bestQuality) Bitmap.Config.ARGB_8888 else Bitmap.Config.RGB_565
        val mHeight: Int = bmpOriginal.height
        val mWidth: Int = bmpOriginal.width
        val mNightModeBitmap = Bitmap.createBitmap(mWidth, mHeight, mBitmapQuality)
        val mNightModeCanvas = Canvas(mNightModeBitmap)
        val mPaint = Paint()
        val mInvertMatrix = ColorMatrix(
            floatArrayOf(
                -1f, 0f, 0f, 0f, 255f,
                0f, -1f, 0f, 0f, 255f,
                0f, 0f, -1f, 0f, 255f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        val mMatrix = ColorMatrix().apply {
            setSaturation(0f)
        }
        val mNightModeMatrix = ColorMatrix().apply {
            postConcat(mMatrix)
            postConcat(mInvertMatrix)
        }
        mPaint.colorFilter = ColorMatrixColorFilter(mNightModeMatrix)
        mNightModeCanvas.drawBitmap(bmpOriginal, 0f, 0f, mPaint)
        return mNightModeBitmap
    }

    @Throws(PageRenderingException::class)
    private fun proceed(task: RenderingTask): PagePart? {
        val mPdfFile = pdfView.pdfFile
        mPdfFile?.openPage(task.page)
        val mHeight = task.height.roundToInt()
        val mWidth = task.width.roundToInt()
        if (mWidth == 0 || mHeight == 0 || mPdfFile?.pageHasError(task.page) == true) return null
        val mQuality = if (task.isBestQuality) Bitmap.Config.ARGB_8888 else Bitmap.Config.RGB_565
        var mBitmap: Bitmap
        mBitmap = try {
            Bitmap.createBitmap(mWidth, mHeight, mQuality)
        } catch (e: IllegalArgumentException) {
            Log.e(PdfConstants.TAG, "Cannot create bitmap", e)
            return null
        }
        calculateBounds(mWidth, mHeight, task.bounds)
        mPdfFile?.renderPageBitmap(mBitmap, task.page, mRoundedBounds, task.isAnnotation)
        if (pdfView.isNightMode()) {
            mBitmap = toNightMode(mBitmap, task.isBestQuality)
        }
        return PagePart(task.page, mBitmap, task.bounds, task.isThumbnail, task.cacheOrder)
    }

    private fun calculateBounds(width: Int, height: Int, pageSliceBounds: RectF) {
        mRenderMatrix.reset()
        mRenderMatrix.postTranslate(-pageSliceBounds.left * width, -pageSliceBounds.top * height)
        mRenderMatrix.postScale(1 / pageSliceBounds.width(), 1 / pageSliceBounds.height())
        mBounds[0f, 0f, width.toFloat()] = height.toFloat()
        mRenderMatrix.mapRect(mBounds)
        mBounds.round(mRoundedBounds)
    }

    fun stop() {
        isRunning = false
    }

    fun start() {
        isRunning = true
    }

    private class RenderingTask(
        val width: Float,
        val height: Float,
        val bounds: RectF,
        val page: Int,
        val isThumbnail: Boolean,
        val cacheOrder: Int,
        val isBestQuality: Boolean,
        val isAnnotation: Boolean
    )

    companion object {
        const val MSG_RENDER_PART_TASK = 1
    }
}