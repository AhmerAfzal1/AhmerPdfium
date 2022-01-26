package com.ahmer.pdfviewer

import android.os.Handler
import android.os.Looper
import com.ahmer.pdfium.util.Size
import com.ahmer.pdfviewer.source.DocumentSource
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

internal class DecodingTask(
    private val docSource: DocumentSource,
    private val password: String? = null,
    private val userPages: IntArray? = null,
    private val pdfView: PDFView
) {
    private val mExecutor: ExecutorService = Executors.newFixedThreadPool(4)
    private val mHandler: Handler = Handler(Looper.getMainLooper())

    private fun getViewSize(pdfView: PDFView): Size {
        return Size(pdfView.width, pdfView.height)
    }

    fun execute() {
        mExecutor.execute {
            val mPdfiumCore = docSource.createDocument(pdfView.context, password)
            val mPdfFile = PdfFile(
                pdfiumCore = mPdfiumCore,
                fitPolicy = pdfView.getPageFitPolicy(),
                viewSize = getViewSize(pdfView),
                userPages = userPages ?: intArrayOf(),
                isVertical = pdfView.isSwipeVertical(),
                spacingPx = pdfView.getSpacingPx(),
                autoSpacing = pdfView.isAutoSpacingEnabled(),
                fitEachPage = pdfView.isFitEachPage()
            )
            mHandler.post {
                try {
                    pdfView.loadComplete(mPdfFile)
                } catch (t: Throwable) {
                    pdfView.loadError(t)
                }
            }
        }
    }

    /**
     * Call to cancel background work
     */
    fun cancel() {
        mExecutor.shutdown()
        mHandler.removeCallbacksAndMessages(null)
    }
}