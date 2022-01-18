package com.ahmer.pdfviewer.source

import android.content.Context
import com.ahmer.pdfium.PdfiumCore
import com.ahmer.pdfviewer.util.PdfFileUtils
import java.io.IOException

class AssetSource(private val assetName: String) : DocumentSource {

    @Throws(IOException::class)
    override fun createDocument(context: Context, password: String?): PdfiumCore {
        val file = PdfFileUtils.fileFromAsset(context, assetName)
        return PdfiumCore(context, file, password)
    }
}