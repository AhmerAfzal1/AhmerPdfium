package com.ahmer.pdfviewer.source

import android.content.Context
import com.ahmer.pdfium.PdfiumCore
import java.io.IOException

class ByteArraySource(private val data: ByteArray) : DocumentSource {

    @Throws(IOException::class)
    override fun createDocument(context: Context?, core: PdfiumCore?, password: String?) {
        core?.newDocument(data, password)
    }
}
