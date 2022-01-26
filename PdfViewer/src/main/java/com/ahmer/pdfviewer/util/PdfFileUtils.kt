package com.ahmer.pdfviewer.util

import android.content.Context
import java.io.*

object PdfFileUtils {

    @Throws(IOException::class)
    @JvmStatic
    fun fileFromAsset(context: Context, assetName: String): File {
        val mFile = File(context.cacheDir, assetName)
        return mFile.also { file ->
            if (assetName.contains("/")) file.parentFile?.mkdirs()
            copy(context.assets.open(assetName), file)
        }
    }

    @Throws(IOException::class)
    @JvmStatic
    fun copy(inputStream: InputStream, file: File?) {
        var mOutputStream: OutputStream? = null
        try {
            mOutputStream = FileOutputStream(file)
            val mBytes = ByteArray(1024)
            var mRead: Int
            while (inputStream.read(mBytes).also { read -> mRead = read } != -1) {
                mOutputStream.write(mBytes, 0, mRead)
            }
        } finally {
            try {
                inputStream.close()
            } finally {
                mOutputStream?.close()
            }
        }
    }
}
