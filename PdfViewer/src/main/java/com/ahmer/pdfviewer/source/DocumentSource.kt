package com.ahmer.pdfviewer.source

import android.content.Context
import com.ahmer.pdfium.PdfDocument
import com.ahmer.pdfium.PdfiumCore
import java.io.IOException

interface DocumentSource {

    @Throws(IOException::class)
    fun createDocument(context: Context, core: PdfiumCore, password: String?): PdfDocument
}