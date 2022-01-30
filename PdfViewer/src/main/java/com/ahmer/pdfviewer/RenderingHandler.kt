package com.ahmer.pdfviewer

import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import com.ahmer.pdfviewer.exception.PageRenderingException
import com.ahmer.pdfviewer.model.PagePart
import com.ahmer.pdfviewer.util.MathUtils.max
import com.ahmer.pdfviewer.util.MathUtils.min
import kotlin.math.abs
import kotlin.math.roundToInt

class RenderingHandler(looper: Looper, private val pdfView: PDFView) : Handler(looper) {
    private val mBounds: RectF = RectF()
    private val mRenderMatrix: Matrix = Matrix()
    private val mRoundedBounds: Rect = Rect()
    private var isRunning: Boolean = false

    fun addRenderingTask(
        page: Int, width: Float, height: Float, bounds: RectF, isThumbnail: Boolean,
        cacheOrder: Int, isBestQuality: Boolean, isAnnotation: Boolean, query: String
    ) {
        val task = RenderingTask(
            width, height, bounds, page, isThumbnail, cacheOrder, isBestQuality, isAnnotation, query
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

    @Throws(PageRenderingException::class)
    private fun parseText(task: RenderingTask): String? {
        val mPdfFile = pdfView.pdfFile
        mPdfFile?.openPage(task.page)
        val mCountCharsOnPage = mPdfFile?.getCountCharactersOnPage(task.page) ?: 0
        return if (mCountCharsOnPage > 0) {
            mPdfFile?.getExtractCharacters(task.page, 0, mCountCharsOnPage)
        } else {
            null
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
            Log.e(TAG, "Cannot create bitmap", e)
            return null
        }
        val mNativePageSize = mPdfFile?.getPageSizeNative(task.page)
        val mNativePageWidth = mNativePageSize?.width?.toFloat() ?: 0f
        val mNativePageHeight = mNativePageSize?.height?.toFloat() ?: 0f
        val mCanvas = Canvas(mBitmap)
        val mPaint = Paint()
        mPaint.color = Color.WHITE
        val mSearchQuery = task.searchQuery
        if (mSearchQuery.isNotBlank()) {
            val mSearch =
                mPdfFile?.pageSearch(task.page, mSearchQuery, false, matchWholeWord = false)
            if (mSearch?.hasNext() == true) {
                while (true) {
                    val rect = mSearch.searchNext() ?: break
                    //If thumbnail
                    if (mRoundedBounds.width() <= mNativePageWidth.toInt()) {
                        val currentRenderedRealRectByBounds = RectF(
                            task.bounds.left * mNativePageWidth,
                            task.bounds.top * mNativePageHeight,
                            task.bounds.right * mNativePageWidth,
                            task.bounds.bottom * mNativePageHeight
                        )
                        if (rect.intersect(currentRenderedRealRectByBounds)) {
                            val l1 = rect.left * mRoundedBounds.width() / mNativePageWidth
                            val t1 =
                                mRoundedBounds.height() - rect.top * mRoundedBounds.height() / mNativePageHeight
                            val r1 = rect.right * mRoundedBounds.width() / mNativePageWidth
                            val b1 =
                                mRoundedBounds.height() - rect.bottom * mRoundedBounds.height() / mNativePageHeight
                            var strLen = mSearchQuery.length - 1
                            if (strLen < 1) {
                                strLen = 1
                            }
                            val w1 = l1 + (r1 - l1) * strLen
                            mPaint.color = Color.YELLOW
                            mCanvas.drawRect(RectF(l1, t1, w1, b1), mPaint)
                        } else {
                            break
                        }
                    } else {
                        val rectForBitmap = RectF(
                            task.bounds.left * mBounds.width(),
                            task.bounds.top * mBounds.height(),
                            task.bounds.right * mBounds.width(),
                            task.bounds.bottom * mBounds.height()
                        )
                        val left = rect.left / mNativePageWidth * mBounds.width()
                        val rr = rect.right / mNativePageWidth * mBounds.width()
                        var strLen = mSearchQuery.length - 2
                        if (strLen < 1) {
                            strLen = 1
                        }
                        if (mSearchQuery.length <= 2 && strLen < 2) {
                            strLen = 2
                        }
                        val symbolWidth = rr - left
                        val ww1 = left + symbolWidth * strLen
                        val rectForSearch = RectF(
                            left,
                            mBounds.height() - rect.top / mNativePageHeight * mBounds.height(),
                            ww1,
                            mBounds.height() - rect.bottom / mNativePageHeight * mBounds.height()
                        )
                        if (rectForSearch.intersect(rectForBitmap)) {
                            val l1 = abs(abs(rectForSearch.left) - abs(rectForBitmap.left))
                            val t1 = abs(abs(rectForSearch.top) - abs(rectForBitmap.top))
                            val r1 = l1 + rectForSearch.width()
                            val b1 = t1 + rectForSearch.height()
                            val realRect = RectF(
                                max(0f, min(mBitmap.width.toFloat(), l1)),
                                max(0f, min(mBitmap.height.toFloat(), t1)),
                                min(r1, mBitmap.width.toFloat()),
                                min(mBitmap.height.toFloat(), b1)
                            )
                            mPaint.color = Color.YELLOW
                            mCanvas.drawRect(realRect, mPaint)
                        }
                    }
                }
            }
        }
        calculateBounds(mWidth, mHeight, task.bounds)
        mPdfFile?.renderPageBitmap(mBitmap, task.page, mRoundedBounds, task.isAnnotation)
        if (pdfView.isNightMode()) {
            mBitmap = toNightMode(mBitmap, task.isBestQuality)
        }
        return PagePart(
            task.page, mBitmap, task.bounds, task.isThumbnail, task.cacheOrder, mSearchQuery
        )
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
        val isAnnotation: Boolean,
        val searchQuery: String
    )

    companion object {
        const val MSG_RENDER_PART_TASK = 1
        private val TAG = RenderingHandler::class.java.name
    }
}