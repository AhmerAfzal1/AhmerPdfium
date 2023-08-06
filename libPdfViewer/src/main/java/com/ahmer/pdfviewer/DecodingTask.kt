package com.ahmer.pdfviewer

import android.os.Handler
import android.os.Looper
import com.ahmer.pdfium.PdfiumCore
import com.ahmer.pdfium.util.Size
import com.ahmer.pdfviewer.source.DocumentSource
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

internal class DecodingTask(
    private val docSource: DocumentSource,
    private val pdfiumCore: PdfiumCore,
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
        try {
            val mPdfDocument = docSource.createDocument(pdfiumCore, password)
            val mPdfFile = PdfFile(
                pdfiumCore = pdfiumCore,
                pdfDocument = mPdfDocument,
                fitPolicy = pdfView.getPageFitPolicy(),
                size = getViewSize(pdfView),
                userPages = userPages ?: intArrayOf(),
                isVertical = pdfView.isSwipeVertical(),
                spacingPx = pdfView.getSpacingPx(),
                autoSpacing = pdfView.isAutoSpacingEnabled(),
                fitEachPage = pdfView.isFitEachPage()
            )
            mExecutor.execute {
                mHandler.post {
                    pdfView.loadComplete(mPdfFile)
                }
            }
        } catch (t: Throwable) {
            pdfView.loadError(t)
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