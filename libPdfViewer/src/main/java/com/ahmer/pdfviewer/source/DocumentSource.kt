package com.ahmer.pdfviewer.source

import android.content.Context
import com.ahmer.pdfium.PdfDocument
import com.ahmer.pdfium.PdfiumCore
import java.io.IOException

interface DocumentSource {

    @Throws(IOException::class)
   suspend fun createDocument(context: Context, pdfiumCore: PdfiumCore, password: String?): PdfDocument
}