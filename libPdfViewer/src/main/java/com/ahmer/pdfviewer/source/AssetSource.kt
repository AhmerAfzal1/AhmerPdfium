package com.ahmer.pdfviewer.source

import android.content.Context
import android.os.ParcelFileDescriptor
import com.ahmer.pdfium.PdfDocument
import com.ahmer.pdfium.PdfiumCore
import com.ahmer.pdfviewer.util.PdfUtils
import java.io.IOException


class AssetSource(private val name: String) : DocumentSource {

    @Throws(IOException::class)
    override fun createDocument(
        context: Context,
        pdfiumCore: PdfiumCore,
        password: String?
    ): PdfDocument {
        return pdfiumCore.newDocument(
            parcelFileDescriptor = ParcelFileDescriptor.open(
                PdfUtils.fileFromAsset(context = context, assetName = name),
                ParcelFileDescriptor.MODE_READ_ONLY
            ),
            password = password
        )
    }
}