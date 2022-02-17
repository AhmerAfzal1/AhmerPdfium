package com.ahmer.pdfviewer.source

import com.ahmer.pdfium.PdfDocument
import com.ahmer.pdfium.PdfiumCore
import java.io.IOException

interface DocumentSource {

    @Throws(IOException::class)
    fun createDocument(core: PdfiumCore, password: String?): PdfDocument
}