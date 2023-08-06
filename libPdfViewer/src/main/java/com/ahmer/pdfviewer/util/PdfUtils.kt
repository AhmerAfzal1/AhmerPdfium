package com.ahmer.pdfviewer.util

import android.content.Context
import android.util.TypedValue
import java.io.*

object PdfUtils {

    private const val BUFFER_SIZE: Int = 1024 * 4

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
            val mBytes = ByteArray(BUFFER_SIZE)
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

    @JvmStatic
    fun getDP(context: Context, dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), context.resources.displayMetrics
        ).toInt()
    }

    @JvmStatic
    fun indexExists(list: List<*>, index: Int): Boolean {
        return index >= 0 && index < list.size
    }

    @JvmStatic
    fun indexExists(count: Int, index: Int): Boolean {
        return index in 0 until count
    }

    @Throws(IOException::class)
    @JvmStatic
    fun toByteArray(inputStream: InputStream): ByteArray {
        val mBuffer = ByteArray(BUFFER_SIZE)
        val mOutputStream = ByteArrayOutputStream()
        var mLength: Int
        while (-1 != inputStream.read(mBuffer).also { read -> mLength = read }) {
            mOutputStream.write(mBuffer, 0, mLength)
        }
        return mOutputStream.toByteArray()
    }
}
