package com.ahmer.pdfviewer.decoding

import com.ahmer.pdfviewer.PDFView
import com.ahmer.pdfviewer.PdfFile
import com.ahmer.pdfviewer.source.DocumentSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DecodingRunner(
    private val docSource: DocumentSource,
    private val password: String? = null,
    pages: IntArray? = null,
    private val pdfView: PDFView
) {
    private val userPages: IntArray = pages ?: intArrayOf()

    fun executeAsync() {
        CoroutineScope(Dispatchers.Main).launch {
            val task = DecodingTask(docSource, password, userPages, pdfView)
            try {
                val pdfFile: PdfFile? = task.call()
                pdfView.loadComplete(pdfFile)
            } catch (t: Throwable) {
                pdfView.loadError(t)
            }
        }
    }
}