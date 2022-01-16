package com.ahmer.pdfviewer

import com.ahmer.pdfium.util.Size
import com.ahmer.pdfviewer.async.AsyncTask
import com.ahmer.pdfium.PdfiumCore
import com.ahmer.pdfviewer.source.DocumentSource
import java.lang.NullPointerException

internal open class DecodingAsyncTask(
    private val docSource: DocumentSource,
    private val password: String?,
    private val userPages: IntArray?,
    private val pdfView: PDFView?,
    private val pdfiumCore: PdfiumCore
) : AsyncTask<Void?, Void?, Throwable?>() {
    private var cancelled = false
    private var pdfFile: PdfFile? = null

    override fun onPreExecute() {
        super.onPreExecute()
    }

    override fun doInBackground(aVoid: Void?): Throwable? {
        return try {
            if (pdfView != null) {
                docSource.createDocument(pdfView.context, pdfiumCore, password)
                pdfFile = PdfFile(
                    pdfiumCore, pdfView.pageFitPolicy, getViewSize(pdfView),
                    userPages, pdfView.isSwipeVertical, pdfView.spacingPx,
                    pdfView.isAutoSpacingEnabled(), pdfView.isFitEachPage
                )
                null
            } else {
                NullPointerException("pdfView == null")
            }
        } catch (t: Throwable) {
            t
        }
    }

    private fun getViewSize(pdfView: PDFView): Size {
        return Size(pdfView.width, pdfView.height)
    }

    override fun onPostExecute(t: Throwable?) {
        super.onPostExecute(t)
        if (pdfView != null) {
            if (t != null) {
                pdfView.loadError(t)
                return
            }
            if (!cancelled) {
                pdfView.loadComplete(pdfFile!!)
            }
        }
    }

    public override fun onCancelled() {
        super.onCancelled()
        cancelled = true
    }
}
