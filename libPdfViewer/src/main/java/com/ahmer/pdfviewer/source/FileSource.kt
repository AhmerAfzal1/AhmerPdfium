package com.ahmer.pdfviewer.source

import android.content.Context
import android.os.ParcelFileDescriptor
import com.ahmer.pdfium.PdfDocument
import com.ahmer.pdfium.PdfiumCore
import java.io.File
import java.io.IOException

class FileSource(private val file: File) : DocumentSource {

    @Throws(IOException::class)
    override fun createDocument(
        context: Context,
        pdfiumCore: PdfiumCore,
        password: String?
    ): PdfDocument {
        return pdfiumCore.newDocument(
            parcelFileDescriptor = ParcelFileDescriptor.open(
                file,
                ParcelFileDescriptor.MODE_READ_ONLY
            ),
            password = password
        )
    }
}