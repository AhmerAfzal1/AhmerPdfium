package com.ahmer.pdfviewer

import android.util.Log
import com.ahmer.pdfium.PdfDocument
import com.ahmer.pdfium.PdfiumCore
import com.ahmer.pdfium.util.Size
import com.ahmer.pdfviewer.source.DocumentSource
import com.ahmer.pdfviewer.util.PdfConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class DecodingTask(
    private val docSource: DocumentSource,
    private val password: String?,
    private val userPages: IntArray? = null,
    private val pdfView: PDFView
) {
    private val coroutineScope: CoroutineScope = CoroutineScope(context = Dispatchers.Main + SupervisorJob())
    private var loadingJob: Job? = null

    fun execute() {
        loadingJob?.cancel()

        val pdfiumCore: PdfiumCore = pdfView.pdfiumCore ?: run {
            Log.e(PdfConstants.TAG, "PdfiumCore is not initialized")
            pdfView.loadError(error = IllegalStateException("PdfiumCore is not initialized"))
            return
        }

        loadingJob = coroutineScope.launch(context = Dispatchers.IO) {
            try {
                val document: PdfDocument = createDocument(pdfiumCore = pdfiumCore)
                val pdfFile: PdfFile = createPdfFile(document = document, pdfiumCore = pdfiumCore)

                withContext(context = Dispatchers.Main) {
                    pdfView.loadComplete(pdfFile = pdfFile)
                }
            } catch (t: Throwable) {
                Log.e(PdfConstants.TAG, "Error decoding PDF", t)
                withContext(context = Dispatchers.Main) {
                    pdfView.loadError(error = t)
                }
            }
        }
    }

    /**
     * Create PDF document from source
     */
    private suspend fun createDocument(pdfiumCore: PdfiumCore): PdfDocument {
        return withContext(context = Dispatchers.IO) {
            docSource.createDocument(context = pdfView.context, pdfiumCore = pdfiumCore, password = password)
        }
    }

    /**
     * Create PDF file with appropriate settings
     */
    private suspend fun createPdfFile(document: PdfDocument, pdfiumCore: PdfiumCore): PdfFile {
        return withContext(context = Dispatchers.Default) {
            PdfFile.create(
                pdfDocument = document,
                pdfiumCore = pdfiumCore,
                fitPolicy = pdfView.pageFitPolicy,
                isAutoSpacing = pdfView.isAutoSpacingEnabled,
                isFitEachPage = pdfView.isFitEachPage,
                isVertical = pdfView.isSwipeVertical,
                spacingPixels = pdfView.spacingPx,
                userPages = userPages ?: intArrayOf(),
                size = Size(width = pdfView.width, height = pdfView.height)
            )
        }
    }

    /**
     * Clean up resources
     */
    fun cancel() {
        coroutineScope.cancel()
        loadingJob?.cancel()
        loadingJob = null
    }
}