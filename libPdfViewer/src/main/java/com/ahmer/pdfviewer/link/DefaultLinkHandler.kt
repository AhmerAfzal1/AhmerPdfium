package com.ahmer.pdfviewer.link

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.core.net.toUri
import com.ahmer.pdfviewer.PDFView
import com.ahmer.pdfviewer.model.LinkTapEvent
import com.ahmer.pdfviewer.util.PdfConstants

class DefaultLinkHandler(private val pdfView: PDFView) : LinkHandler {

    /**
     * Processes a link tap event, delegating to URI or page handling as appropriate.
     *
     * @param event The link tap event containing navigation details, may be null
     */
    override fun handleLinkEvent(event: LinkTapEvent?) {
        if (event == null) return
        val uri: String? = event.link.uri
        val pageIndex: Int? = event.link.destPage

        when {
            !uri.isNullOrBlank() -> handleUri(uri = uri)
            pageIndex != null -> handlePage(pageNumber = pageIndex)
        }
    }

    /**
     * Creates a configured intent for viewing URIs.
     *
     * @param uri The parsed URI to view
     * @return Configured Intent ready for launch
     */
    private fun createUriIntent(uri: Uri): Intent {
        return Intent(Intent.ACTION_VIEW, uri).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
            ) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }

    /**
     * Handles external URI navigation attempts.
     *
     * @param uri The non-null URI string to navigate to
     */
    private fun handleUri(uri: String) {
        val context: Context = pdfView.context

        try {
            createUriIntent(uri = uri.toUri()).let { intent ->
                context.startActivity(Intent.createChooser(intent, CHOOSER_TITLE))
            }
        } catch (e: ActivityNotFoundException) {
            Log.e(PdfConstants.TAG, e.localizedMessage ?: "No activity to handle uri", e)
            showToast(context = context, message = ERROR_MESSAGE_NO_APPS)
        } catch (e: Exception) {
            Log.e(PdfConstants.TAG, e.localizedMessage ?: "Unexpected error opening uri", e)
            showToast(context = context, message = ERROR_MESSAGE_FAILED)
        }
    }

    /**
     * Handles internal page navigation within the PDF view.
     *
     * @param pageNumber The non-null page index to navigate to
     */
    private fun handlePage(pageNumber: Int) {
        pdfView.jumpTo(page = pageNumber, withAnimation = true)
    }

    private fun showToast(context: Context, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    companion object {
        private const val CHOOSER_TITLE: String = "Select app for open link"
        private const val ERROR_MESSAGE_FAILED: String = "Failed to open link"
        private const val ERROR_MESSAGE_NO_APPS: String = "No app available to handle this link"
    }
}