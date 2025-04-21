package com.ahmer.pdfviewer.link

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.Toast
import com.ahmer.pdfviewer.PDFView
import com.ahmer.pdfviewer.model.LinkTapEvent
import com.ahmer.pdfviewer.util.PdfConstants
import androidx.core.net.toUri

class DefaultLinkHandler(private val pdfView: PDFView) : LinkHandler {

    override fun handleLinkEvent(event: LinkTapEvent?) {
        val mUri = event?.link?.uri
        val mPage = event?.link?.destPageIndex
        if (!mUri.isNullOrBlank()) handleUri(mUri) else mPage?.let { handlePage(it) }
    }

    private fun handleUri(uri: String) {
        val mContext: Context = pdfView.context
        val mParsedUri: Uri = uri.toUri()
        val mIntent: Intent = Intent(Intent.ACTION_VIEW, mParsedUri).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N || Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        val mTitle = "Select app for open link"

        // Try to invoke the intent.
        try {
            mContext.startActivity(Intent.createChooser(mIntent, mTitle))
        } catch (e: ActivityNotFoundException) {
            // Define what your app should do if no activity can handle the intent.
            Log.e(PdfConstants.TAG, e.localizedMessage ?: "NULL", e)
            Toast.makeText(mContext, "No apps can open for this link", Toast.LENGTH_LONG).show()
        }
    }

    private fun handlePage(page: Int) {
        pdfView.jumpTo(page, true)
    }
}