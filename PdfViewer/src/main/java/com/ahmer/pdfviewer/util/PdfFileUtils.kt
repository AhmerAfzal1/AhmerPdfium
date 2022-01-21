package com.ahmer.pdfviewer.util

import android.content.Context
import java.io.*

object PdfFileUtils {

    @Throws(IOException::class)
    @JvmStatic
    fun fileFromAsset(context: Context, assetName: String): File {
        val outFile = File(context.cacheDir, assetName)
        return outFile.also {
            if (assetName.contains("/")) {
                it.parentFile?.mkdirs()
            }
            copy(context.assets.open(assetName), it)
        }
    }

    @Throws(IOException::class)
    @JvmStatic
    fun copy(inputStream: InputStream, output: File?) {
        var outputStream: OutputStream? = null
        try {
            outputStream = FileOutputStream(output)
            var read: Int
            val bytes = ByteArray(1024)
            while (inputStream.read(bytes).also { read = it } != -1) {
                outputStream.write(bytes, 0, read)
            }
        } finally {
            try {
                inputStream.close()
            } finally {
                outputStream?.close()
            }
        }
    }
}
