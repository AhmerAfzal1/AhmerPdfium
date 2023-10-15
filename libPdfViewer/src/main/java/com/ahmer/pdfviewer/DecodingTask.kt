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
    private val mExecutor: ExecutorService = Executors.newFixedThreadPool(4)
    private val mHandler: Handler = Handler(Looper.getMainLooper())

    fun execute() {
        try {
            val document = docSource.createDocument(pdfView.pdfiumCore!!, password)
            val pdfPage = document.openPage(pdfView.mCurrentPage)
            val mPdfFile = PdfFile(
                context = pdfView.context,
                pdfDocument = document,
                pdfPage = pdfPage,
                fitPolicy = pdfView.getPageFitPolicy(),
                size = Size(pdfView.width, pdfView.height),
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