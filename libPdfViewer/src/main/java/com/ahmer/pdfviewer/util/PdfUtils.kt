package com.ahmer.pdfviewer.util

import android.content.Context
import android.util.DisplayMetrics
import android.util.TypedValue
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream

object PdfUtils {

    private const val BUFFER_SIZE: Int = 0x1000 // 1024 * 4

    @Throws(exceptionClasses = [IOException::class])
    fun fileFromAsset(context: Context, assetName: String): File {
        require(value = assetName.isNotBlank()) { "Asset name cannot be blank" }

        return File(context.cacheDir, assetName).also { file ->
            if (assetName.contains(other = "/")) {
                file.parentFile?.mkdirs()
            }
            context.assets.open(assetName).use { input ->
                input.copyTo(out = file.outputStream(), bufferSize = BUFFER_SIZE)
            }
        }
    }

    fun getDP(context: Context, dp: Int): Int {
        require(value = dp >= 0) { "DP value cannot be negative" }
        val metrics: DisplayMetrics = context.resources.displayMetrics
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), metrics).toInt()
    }

    fun indexExists(list: List<*>, index: Int): Boolean {
        return index >= 0 && index < list.size
    }

    fun indexExists(count: Int, index: Int): Boolean {
        return index in 0 until count
    }

    @Throws(exceptionClasses = [IOException::class])
    fun toByteArray(inputStream: InputStream): ByteArray {
        return inputStream.use { input ->
            ByteArrayOutputStream().use { output ->
                input.copyTo(out = output, bufferSize = BUFFER_SIZE)
                output.toByteArray()
            }
        }
    }
}
