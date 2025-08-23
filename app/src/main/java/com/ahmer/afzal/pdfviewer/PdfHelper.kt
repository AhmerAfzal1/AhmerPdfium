package com.ahmer.afzal.pdfviewer

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns

object PdfHelper {
    fun getFileNameFromUri(context: Context, uri: Uri): String? {
        val cursor: Cursor? = context.contentResolver.query(
            uri,
            null,
            null,
            null,
            null
        )
        cursor?.use {
            val nameIndex: Int = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (it.moveToFirst() && nameIndex != -1) {
                return it.getString(nameIndex)
            }
        }
        return null
    }
}