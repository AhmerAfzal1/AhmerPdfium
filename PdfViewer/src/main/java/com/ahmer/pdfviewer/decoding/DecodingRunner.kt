package com.ahmer.pdfviewer.decoding

import com.ahmer.pdfviewer.PDFView
import com.ahmer.pdfviewer.PdfFile
import com.ahmer.pdfviewer.source.DocumentSource
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

class DecodingRunner(
    private val docSource: DocumentSource,
    private val password: String? = null,
    pages: IntArray? = null,
    private val pdfView: PDFView
) : CoroutineScope {

    private val userPages: IntArray = pages ?: intArrayOf()

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + Job()

    fun executeAsync() {
        CoroutineScope(coroutineContext).launch(SupervisorJob()) {
            val task = DecodingTask(docSource, password, userPages, pdfView)
            try {
                val pdfFile: PdfFile? = task.call()
                pdfView.loadComplete(pdfFile!!)
            } catch (t: Throwable) {
                pdfView.loadError(t)
            }
        }
    }
}