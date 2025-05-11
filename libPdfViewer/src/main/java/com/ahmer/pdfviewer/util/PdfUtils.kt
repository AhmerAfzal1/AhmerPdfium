package com.ahmer.pdfviewer.util

import android.content.Context
import android.util.TypedValue
import java.io.*

object PdfUtils {

    private const val BUFFER_SIZE: Int = 0x1000 // 1024 * 4

    @Throws(IOException::class)
    fun fileFromAsset(context: Context, assetName: String): File {
        return File(context.cacheDir, assetName).also { file ->
            if (assetName.contains(other = "/")) {
                file.parentFile?.mkdirs()
            }
            context.assets.open(assetName).use { input ->
                copy(inputStream = input, file = file)
            }
        }
    }

    @Throws(IOException::class)
    fun copy(inputStream: InputStream, file: File) {
        inputStream.use { input ->
            FileOutputStream(file).use { output ->
                input.copyTo(out = output, bufferSize = BUFFER_SIZE)
            }
        }
    }

    fun getDP(context: Context, dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            context.resources.displayMetrics
        ).toInt()
    }

    fun indexExists(list: List<*>, index: Int): Boolean {
        return index >= 0 && index < list.size
    }

    fun indexExists(count: Int, index: Int): Boolean {
        return index in 0 until count
    }

    @Throws(IOException::class)
    fun toByteArray(inputStream: InputStream): ByteArray {
        return inputStream.use { input ->
            ByteArrayOutputStream().use { output ->
                input.copyTo(out = output, bufferSize = BUFFER_SIZE)
                output.toByteArray()
            }
        }
    }
}
