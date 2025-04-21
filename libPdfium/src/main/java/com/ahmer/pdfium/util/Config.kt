package com.ahmer.pdfium.util

import android.util.Log
import androidx.annotation.Keep

var pdfiumConfig = Config()

@Keep
enum class AlreadyClosedBehavior {
    EXCEPTION,
    IGNORE,
}

@Keep
data class Config(
    val alreadyClosedBehavior: AlreadyClosedBehavior = AlreadyClosedBehavior.EXCEPTION,
)

fun handleAlreadyClosed(isClosed: Boolean): Boolean {
    if (isClosed) {
        when (pdfiumConfig.alreadyClosedBehavior) {
            AlreadyClosedBehavior.EXCEPTION -> error("Already closed")
            AlreadyClosedBehavior.IGNORE -> Log.d("PdfiumCore", "Already closed")
        }
    }
    return isClosed
}