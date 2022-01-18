package com.ahmer.pdfviewer.source

import android.content.Context
import com.ahmer.pdfium.PdfiumCore
import java.io.File
import java.io.IOException

class FileSource(private val file: File) : DocumentSource {

    @Throws(IOException::class)
    override fun createDocument(context: Context, password: String?): PdfiumCore {
        return PdfiumCore(context, file, password)
    }
}