package com.ahmer.pdfviewer.source

import android.os.ParcelFileDescriptor
import com.ahmer.pdfium.PdfDocument
import com.ahmer.pdfium.PdfiumCore
import java.io.File
import java.io.IOException

class FileSource(private val file: File) : DocumentSource {

    @Throws(IOException::class)
    override fun createDocument(core: PdfiumCore, password: String?): PdfDocument {
        val mFileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        return core.newDocument(mFileDescriptor, password)
    }
}