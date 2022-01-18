package com.ahmer.pdfviewer.decoding

import com.ahmer.pdfium.util.Size
import com.ahmer.pdfviewer.PDFView
import com.ahmer.pdfviewer.PdfFile
import com.ahmer.pdfviewer.source.DocumentSource
import java.lang.ref.WeakReference
import java.util.concurrent.Callable

internal class DecodingTask(
    private val docSource: DocumentSource, private val password: String? = null,
    private val userPages: IntArray = intArrayOf(), pdfView: PDFView
) : Callable<PdfFile?> {

    private var pdfFile: PdfFile? = null
    private var pdfViewReference: WeakReference<PDFView>? = null

    init {
        pdfViewReference = WeakReference(pdfView)
    }

    @Throws(Exception::class)
    override fun call(): PdfFile? {
        val pdfView = pdfViewReference?.get()
        return if (pdfView != null) {
            val pdfiumCore = docSource.createDocument(pdfView.context, password)
            pdfFile = PdfFile(
                pdfiumCore = pdfiumCore,
                pageFitPolicy = pdfView.pageFitPolicy,
                viewSize = getViewSize(pdfView),
                originalUserPages = userPages,
                isVertical = pdfView.isSwipeVertical,
                spacingPx = pdfView.spacingPx,
                autoSpacing = pdfView.isAutoSpacingEnabled(),
                fitEachPage = pdfView.isFitEachPage
            )
            pdfFile?.textHighlightColor = pdfView.getTextHighlightColor()
            pdfFile
        } else {
            throw NullPointerException("pdfView == null")
        }
    }

    private fun getViewSize(pdfView: PDFView): Size {
        return Size(pdfView.width, pdfView.height)
    }
}