package com.ahmer.pdfviewer.link

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.ahmer.pdfviewer.PDFView
import com.ahmer.pdfviewer.model.LinkTapEvent
import io.ahmer.utils.utilcode.ToastUtils

class DefaultLinkHandler(
    private val pdfView: PDFView,
    private val chooserTitle: String = "Select app for open link"
) : LinkHandler {

    override fun handleLinkEvent(event: LinkTapEvent?) {
        val uri = event?.link?.uri
        val page = event?.link?.destPageIdx
        if (!uri.isNullOrBlank()) {
            handleUri(uri)
        } else page?.let { handlePage(it) }
    }

    private fun handleUri(uri: String) {
        val parsedUri = Uri.parse(uri)
        val intent = Intent(Intent.ACTION_VIEW, parsedUri)
        val context = pdfView.context
        // Create intent to show chooser
        val chooser = Intent.createChooser(intent, chooserTitle)

        // Try to invoke the intent.
        try {
            context.startActivity(chooser)
        } catch (e: ActivityNotFoundException) {
            // Define what your app should do if no activity can handle the intent.
            Log.e(TAG, e.message ?: "NULL")
            ToastUtils.showLong("No apps can open this link")
        }
    }

    private fun handlePage(page: Int) {
        pdfView.jumpTo(page)
    }

    companion object {
        private val TAG = DefaultLinkHandler::class.java.simpleName
    }
}