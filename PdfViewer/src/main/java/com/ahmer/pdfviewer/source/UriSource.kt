package com.ahmer.pdfviewer.source

import android.content.Context
import android.net.Uri
import androidx.core.net.toFile
import com.ahmer.pdfium.PdfiumCore
import java.io.IOException

class UriSource(private val uri: Uri) : DocumentSource {

    @Throws(IOException::class)
    override fun createDocument(context: Context, password: String?): PdfiumCore {
        val file = uri.toFile()
        return PdfiumCore(context, file, password)
    }
}