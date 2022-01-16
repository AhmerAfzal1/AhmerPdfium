package com.ahmer.pdfviewer.source

import android.content.Context
import android.os.ParcelFileDescriptor
import com.ahmer.pdfium.PdfiumCore
import java.io.File
import java.io.IOException

class FileSource(private val file: File) : DocumentSource {

    @Throws(IOException::class)
    override fun createDocument(context: Context?, core: PdfiumCore?, password: String?) {
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        core!!.newDocument(pfd, password)
    }
}