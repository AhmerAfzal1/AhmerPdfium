package com.ahmer.pdfviewer.source

import android.content.Context
import android.os.ParcelFileDescriptor
import com.ahmer.pdfium.PdfDocument
import com.ahmer.pdfium.PdfiumCore
import com.ahmer.pdfviewer.util.PdfUtils
import java.io.IOException

class AssetSource(private val name: String) : DocumentSource {

    @Throws(IOException::class)
    override suspend fun createDocument(
        context: Context, pdfiumCore: PdfiumCore, password: String?
    ): PdfDocument {
        val mFile = PdfUtils.fileFromAsset(context, name)
        val mFileDescriptor = ParcelFileDescriptor.open(mFile, ParcelFileDescriptor.MODE_READ_ONLY)
        return pdfiumCore.newDocument(mFileDescriptor, password)
    }
}