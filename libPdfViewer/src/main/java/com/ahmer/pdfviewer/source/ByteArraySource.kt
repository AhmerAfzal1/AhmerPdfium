package com.ahmer.pdfviewer.source

import com.ahmer.pdfium.PdfDocument
import com.ahmer.pdfium.PdfiumCore
import java.io.IOException

class ByteArraySource(private val data: ByteArray) : DocumentSource {

    @Throws(IOException::class)
    override fun createDocument(core: PdfiumCore, password: String?): PdfDocument {
        return core.newDocument(data, password)
    }
}