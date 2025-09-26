package com.ahmer.pdfviewer.util

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.ahmer.pdfium.PdfPasswordException
import com.ahmer.pdfium.PdfiumCore
import java.io.IOException
import kotlin.text.contains

object PdfStatus {
    enum class State {
        EMPTY,
        INVALID,
        PASSWORD_PROTECTED,
        VALID,
    }

    fun check(context: Context, uri: Uri): State {
        val fileDescriptor: ParcelFileDescriptor = try {
            context.contentResolver.openFileDescriptor(uri, "r")
        } catch (_: Exception) {
            return State.INVALID
        } ?: return State.INVALID

        return try {
            if (fileDescriptor.statSize <= 0) {
                return State.EMPTY
            }

            val pdfiumCore = PdfiumCore(context = context)
            try {
                pdfiumCore.newDocument(parcelFileDescriptor = fileDescriptor, password = null)
                pdfiumCore.close()
                State.VALID
            } catch (_: PdfPasswordException) {
                State.PASSWORD_PROTECTED
            } catch (e: IOException) {
                if (e.message?.contains(other = "empty", ignoreCase = true) == true) {
                    State.EMPTY
                } else {
                    State.INVALID
                }
            }
        } catch (_: Exception) {
            State.INVALID
        } finally {
            fileDescriptor.close()
        }
    }
}