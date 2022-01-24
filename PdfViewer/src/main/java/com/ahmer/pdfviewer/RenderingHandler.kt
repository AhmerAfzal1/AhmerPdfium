package com.ahmer.pdfviewer

import android.graphics.*
import android.os.Handler
import android.util.Log
import com.ahmer.pdfviewer.RenderingHandler.RenderingTask
import com.ahmer.pdfviewer.exception.PageRenderingException
import com.ahmer.pdfviewer.model.PagePart
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A [Handler] that will process incoming [RenderingTask] messages
 * and alert [PDFView.onBitmapRendered] when the portion of the
 * PDF is ready to render.
 */
class RenderingHandler(private val pdfView: PDFView) : CoroutineScope {
    private val renderBounds = RectF()
    private val roundedRenderBounds = Rect()
    private val renderMatrix = Matrix()
    private var running = false

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + Job()

    fun addPartRenderingTask(
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
        val nativePageSize = pdfFile?.getPageSizeNative(task.page)
        val nativePageWidth = nativePageSize?.width?.toFloat() ?: 0f
        val nativePageHeight = nativePageSize?.height?.toFloat() ?: 0f
        if (width == 0 || height == 0 || pdfFile?.pageHasError(task.page) == true) {
            return null
        }
        val bitmapQuality = if (task.bestQuality) Bitmap.Config.ARGB_8888 else Bitmap.Config.RGB_565
        var pageBitmap: Bitmap = try {
            Bitmap.createBitmap(width, height, bitmapQuality)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Cannot create bitmap", e)
            return null
        }
        val canvas = Canvas(pageBitmap)
        val paint = Paint()

        paint.color = Color.WHITE
        val bRect = Rect(0, 0, pageBitmap.width, pageBitmap.height)
        canvas.drawRect(bRect, paint)
        calculateBounds(width, height, task.bounds)

        /*
        val rotation = pdfFile?.getPageRotation(renderingTask.page) ?: 0
        if (rotation != 0) {
            Log.e(TAG, "Page rotation: " + rotation + "_")
        }
        */

        val searchQuery = task.searchQuery
        val countCharsOnPage = pdfFile?.countCharactersOnPage(task.page) ?: 0
        var textQuery: String? = null
        if (countCharsOnPage > 0) {
            textQuery = pdfFile?.extractCharacters(task.page, 0, countCharsOnPage)
        }

        if (searchQuery.isNotBlank()) {
            val search = pdfFile?.newPageSearch(
                pageIndex = task.page,
                query = searchQuery,
                matchCase = false,
                matchWholeWord = false
            )
            //Log.e(TAG, "SearchQuery: $searchQuery")
            //Log.e(TAG, "SearchQuery TextQuery: $textQuery")
            if (search?.hasNext() == true) {
                while (true) {
                    //Log.e(TAG, "SearchQuery CountCharsOnPage: $countCharsOnPage")
                    val rect = search.searchNext() ?: break
                    //If thumbnail
                    if (roundedRenderBounds.width() <= nativePageWidth.toInt()) {
                        val currentRenderedRealRectByBounds = RectF(
                            task.bounds.left * nativePageWidth,
                            task.bounds.top * nativePageHeight,
                            task.bounds.right * nativePageWidth,
                            task.bounds.bottom * nativePageHeight
                        )
                        if (rect.intersect(currentRenderedRealRectByBounds)) {
                            val l1 = rect.left * roundedRenderBounds.width() / nativePageWidth
                            val t1 =
                                roundedRenderBounds.height() - rect.top * roundedRenderBounds.height() / nativePageHeight
                            val r1 = rect.right * roundedRenderBounds.width() / nativePageWidth
                            val b1 =
                                roundedRenderBounds.height() - rect.bottom * roundedRenderBounds.height() / nativePageHeight
                            var strLen = searchQuery.length - 1
                            if (strLen < 1) {
                                strLen = 1
                            }
                            val w1 = l1 + (r1 - l1) * strLen
                            paint.color = pdfFile.textHighlightColor
                            canvas.drawRect(RectF(l1, t1, w1, b1), paint)
                        } else {
                            break
                        }
                    } else {
                        val rectForBitmap = RectF(
                            task.bounds.left * renderBounds.width(),
                            task.bounds.top * renderBounds.height(),
                            task.bounds.right * renderBounds.width(),
                            task.bounds.bottom * renderBounds.height()
                        )
                        val left = rect.left / nativePageWidth * renderBounds.width()
                        val right = rect.right / nativePageWidth * renderBounds.width()
                        var strLen = searchQuery.length - 2
                        if (strLen < 1) {
                            strLen = 1
                        }
                        if (searchQuery.length <= 2 && strLen < 2) {
                            strLen = 2
                        }
                        val symbolWidth = right - left
                        val ww1 = left + symbolWidth * strLen
                        val rectForSearch = RectF(
                            left,
                            renderBounds.height() - rect.top / nativePageHeight * renderBounds.height(),
                            ww1,
                            renderBounds.height() - rect.bottom / nativePageHeight * renderBounds.height()
                        )
                        if (rectForSearch.intersect(rectForBitmap)) {

                            // float halfSymbolWidth = symbolWidth / 4.0f;
                            val l1 = abs(abs(rectForSearch.left) - abs(rectForBitmap.left))
                            val t1 = abs(abs(rectForSearch.top) - abs(rectForBitmap.top))
                            val r1 = l1 + rectForSearch.width()
                            val b1 = t1 + rectForSearch.height()

                            // float w1 = l1 + (r1 - l1) * (strLen);
                            val realRect = RectF(
                                max(0f, min(pageBitmap.width.toFloat(), l1)),
                                max(0f, min(pageBitmap.height.toFloat(), t1)),
                                /*min(pageBitmap.getHeight(), b1)*/
                                min(r1, pageBitmap.width.toFloat()),
                                min(pageBitmap.height.toFloat(), b1)
                            )
                            paint.color = pdfFile.textHighlightColor
                            canvas.drawRect(realRect, paint)
                        }
                    }
                }
            }
        }
        pdfFile?.renderPageBitmap(pageBitmap, task.page, roundedRenderBounds, task.annotation)
        if (pdfView.getNightMode()) {
            pageBitmap = toNightMode(pageBitmap, task.bestQuality)
        }
        return PagePart(
            task.page, pageBitmap, task.bounds, task.thumbnail, task.cacheOrder, searchQuery
        )
    }

    private fun calculateBounds(width: Int, height: Int, pageSliceBounds: RectF?) {
        renderMatrix.reset()
        renderMatrix.postTranslate(
            -(pageSliceBounds?.left ?: 0f) * width, -(pageSliceBounds?.top ?: 0f) * height
        )
        renderMatrix.postScale(
            1 / (pageSliceBounds?.width() ?: 0f), 1 / (pageSliceBounds?.height() ?: 0f)
        )
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