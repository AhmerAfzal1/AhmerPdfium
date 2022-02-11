package com.ahmer.pdfviewer.util

import android.content.Context
import android.util.TypedValue
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

object PdfUtils {

    private const val DEFAULT_BUFFER_SIZE = 1024 * 4

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
        val mBuffer = ByteArray(DEFAULT_BUFFER_SIZE)
        val mOutputStream = ByteArrayOutputStream()
        var mLength: Int
        while (-1 != inputStream.read(mBuffer).also { read -> mLength = read }) {
            mOutputStream.write(mBuffer, 0, mLength)
        }
        return mOutputStream.toByteArray()
    }
}
