package com.ahmer.pdfviewer.source

import android.content.Context
import com.ahmer.pdfium.PdfDocument
import com.ahmer.pdfium.PdfiumCore
import com.ahmer.pdfviewer.util.PdfUtils
import java.io.IOException
import java.io.InputStream

class InputStreamSource(private val inputStream: InputStream) : DocumentSource {

    @Throws(IOException::class)
    override fun createDocument(
        context: Context, pdfiumCore: PdfiumCore, password: String?
    ): PdfDocument {
        return pdfiumCore.newDocument(PdfUtils.toByteArray(inputStream), password)
    }
}