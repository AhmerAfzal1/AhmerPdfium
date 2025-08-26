package com.ahmer.pdfviewer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.Log
import androidx.core.graphics.createBitmap
import com.ahmer.pdfviewer.exception.PageRenderingException
import com.ahmer.pdfviewer.model.PagePart
import com.ahmer.pdfviewer.util.PdfConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

class RenderingHandler(private val pdfView: PDFView) {
    private val bounds: RectF = RectF()
    private val channel: Channel<RenderMessage> = Channel(capacity = Channel.UNLIMITED)
    private val coroutineScope: CoroutineScope = CoroutineScope(context = Dispatchers.Default + SupervisorJob())
    private val renderMatrix: Matrix = Matrix()
    private val roundedBounds: Rect = Rect()
    private var isRunning: Boolean = false

    fun start() {
        if (isRunning) return
        isRunning = true

        coroutineScope.launch {
            channel.consumeAsFlow().collect { renderMessage ->
                when (renderMessage) {
                    is RenderMessage.RenderingTask -> handleTask(task = renderMessage)
                    RenderMessage.Stop -> isRunning = false
                }
            }
        }
    }

    fun stop() {
        isRunning = false
        channel.trySend(element = RenderMessage.Stop)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun removeTasks() {
        while (!channel.isEmpty) channel.tryReceive().getOrNull()
    }

    private suspend fun handleTask(task: RenderMessage.RenderingTask) {
        try {
            val bitmapPagePart: PagePart = proceed(task = task) ?: return
            if (isRunning) {
                withContext(context = Dispatchers.Main) {
                    pdfView.onBitmapRendered(part = bitmapPagePart)
                }
            } else {
                bitmapPagePart.renderedBitmap?.recycle()
            }
        } catch (e: PageRenderingException) {
            withContext(context = Dispatchers.Main) {
                pdfView.onPageError(ex = e)
            }
        }
    }

    fun addRenderingTask(
        page: Int, width: Float, height: Float, bounds: RectF, isThumbnail: Boolean,
        cacheOrder: Int, isBestQuality: Boolean, isAnnotation: Boolean
    ) {
        channel.trySend(
            element = RenderMessage.RenderingTask(
                page = page,
                width = width,
                height = height,
                bounds = bounds,
                isThumbnail = isThumbnail,
                cacheOrder = cacheOrder,
                isBestQuality = isBestQuality,
                isAnnotation = isAnnotation
            )
        )
    }

    @Throws(PageRenderingException::class)
    private fun proceed(task: RenderMessage.RenderingTask): PagePart? {
        val pdfFile: PdfFile = pdfView.pdfFile ?: return null
        pdfFile.openPage(pageIndex = task.page)

        val width: Int = task.width.roundToInt()
        val height: Int = task.height.roundToInt()
        if (width == 0 || height == 0 || pdfFile.pageHasError(page = task.page)) return null

        var bitmap: Bitmap
        bitmap = try {
            createBitmap(width = width, height = height, config = bitmapConfig(isBestQuality = task.isBestQuality))
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

    private fun toNightMode(bitmap: Bitmap, bestQuality: Boolean): Bitmap {
        val config: Bitmap.Config = bitmapConfig(isBestQuality = bestQuality)
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

    private fun bitmapConfig(isBestQuality: Boolean): Bitmap.Config {
        return if (isBestQuality) Bitmap.Config.ARGB_8888 else Bitmap.Config.RGB_565
    }

    sealed class RenderMessage {
        data class RenderingTask(
            val page: Int,
            val width: Float,
            val height: Float,
            val bounds: RectF,
            val isThumbnail: Boolean,
            val cacheOrder: Int,
            val isBestQuality: Boolean,
            val isAnnotation: Boolean
        ) : RenderMessage()

        object Stop : RenderMessage()
    }
}