package com.ahmer.pdfviewer.link

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import com.ahmer.pdfviewer.PDFView
import com.ahmer.pdfviewer.model.LinkTapEvent
import com.ahmer.pdfviewer.util.PdfConstants
import io.ahmer.utils.utilcode.ToastUtils

class DefaultLinkHandler(private val pdfView: PDFView) : LinkHandler {

    override fun handleLinkEvent(event: LinkTapEvent?) {
        val mUri = event?.link?.uri
        val mPage = event?.link?.destPageIdx
        if (!mUri.isNullOrBlank()) handleUri(mUri) else mPage?.let { handlePage(it) }
    }

    private fun handleUri(uri: String) {
        val mParsedUri = Uri.parse(uri)
        val mIntent = Intent(Intent.ACTION_VIEW, mParsedUri).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N || Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        val mTitle = "Select app for open link"

        // Try to invoke the intent.
        try {
            pdfView.context.startActivity(Intent.createChooser(mIntent, mTitle))
        } catch (e: ActivityNotFoundException) {
            // Define what your app should do if no activity can handle the intent.
            Log.e(PdfConstants.TAG, e.message ?: "NULL")
            ToastUtils.showLong("No apps can open for this link")
        }
    }

    private fun handlePage(page: Int) {
        pdfView.jumpTo(page)
    }
}