package com.ahmer.pdfviewer.source

import android.os.ParcelFileDescriptor
import com.ahmer.pdfium.PdfDocument
import com.ahmer.pdfium.PdfiumCore
import com.ahmer.pdfviewer.util.PdfUtils
import java.io.IOException

class AssetSource(private val assetName: String) : DocumentSource {

    @Throws(IOException::class)
    override fun createDocument(core: PdfiumCore, password: String?): PdfDocument {
        val mFile = PdfUtils.fileFromAsset(core.context, assetName)
        val mFileDescriptor = ParcelFileDescriptor.open(mFile, ParcelFileDescriptor.MODE_READ_ONLY)
        return core.newDocument(mFileDescriptor, password)
    }
}