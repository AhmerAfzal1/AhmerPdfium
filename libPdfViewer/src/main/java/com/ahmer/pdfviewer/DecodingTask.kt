package com.ahmer.pdfviewer

import android.os.Handler
import android.os.Looper
import com.ahmer.pdfium.util.Size
import com.ahmer.pdfviewer.source.DocumentSource
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

internal class DecodingTask(
    private val docSource: DocumentSource,
    private val password: String?,
    private val userPages: IntArray? = null,
    private val pdfView: PDFView
) {
    private val executor: ExecutorService = Executors.newFixedThreadPool(4)
    private val mainHandler: Handler = Handler(Looper.getMainLooper())

    fun execute() {
        try {
            val document = docSource.createDocument(
                context = pdfView.context,
                pdfiumCore = pdfView.pdfiumCore!!,
                password = password
            )

            val pdfFile = PdfFile.create(
                pdfDocument = document,
                pdfiumCore = pdfView.pdfiumCore!!,
                fitPolicy = pdfView.pageFitPolicy,
                isAutoSpacing = pdfView.isAutoSpacingEnabled,
                isFitEachPage = pdfView.isFitEachPage,
                isVertical = pdfView.isSwipeVertical,
                spacingPixels = pdfView.spacingPx,
                userPages = userPages ?: intArrayOf(),
                size = Size(width = pdfView.width, height = pdfView.height)
            )

            executor.execute {
                mainHandler.post {
                    pdfView.loadComplete(pdfFile = pdfFile)
                }
            }
        } catch (t: Throwable) {
            pdfView.loadError(error = t)
        }
    }

    /**
     * Call to cancel background work
     */
    fun cancel() {
        executor.shutdown()
        mainHandler.removeCallbacksAndMessages(null)
    }
}