package com.ahmer.pdfviewer.source

import android.content.Context
import android.net.Uri
import com.ahmer.pdfium.PdfDocument
import com.ahmer.pdfium.PdfiumCore
import java.io.IOException

class UriSource(private val uri: Uri) : DocumentSource {

    @Throws(IOException::class)
    override fun createDocument(
        context: Context, core: PdfiumCore, password: String?
    ): PdfDocument {
        val mFileDescriptor = context.contentResolver.openFileDescriptor(uri, "r")
        return core.newDocument(mFileDescriptor!!, password)
    }
}