package com.ahmer.pdfviewer.source

import android.content.Context
import android.os.ParcelFileDescriptor
import com.ahmer.pdfium.PdfiumCore
import com.ahmer.pdfviewer.util.PdfFileUtils
import java.io.File
import java.io.IOException

class AssetSource(private val assetName: String) : DocumentSource {

    @Throws(IOException::class)
    override fun createDocument(context: Context?, core: PdfiumCore?, password: String?) {
        val f: File = PdfFileUtils.fileFromAsset(context!!, assetName)
        val pfd = ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
        core?.newDocument(pfd, password)
    }
}
