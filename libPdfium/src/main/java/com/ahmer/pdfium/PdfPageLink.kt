package com.ahmer.pdfium

import android.graphics.RectF
import android.util.Log
import java.io.Closeable
import java.nio.charset.StandardCharsets

class PdfPageLink(
    private val pageLinkPtr: Long,
) : Closeable {
    private external fun nativeClosePageLink(pageLinkPtr: Long)

    private external fun nativeCountRects(pageLinkPtr: Long, index: Int): Int

    private external fun nativeCountWebLinks(pageLinkPtr: Long): Int

    private external fun nativeGetRect(
        pageLinkPtr: Long, linkIndex: Int, rectIndex: Int
    ): FloatArray

    private external fun nativeGetTextRange(pageLinkPtr: Long, index: Int): IntArray

    private external fun nativeGetURL(
        pageLinkPtr: Long, index: Int, count: Int, result: ByteArray
    ): Int

    fun countWebLinks(): Int {
        synchronized(lock = PdfiumCore.lock) {
            return nativeCountWebLinks(pageLinkPtr = pageLinkPtr)
        }
    }

    fun getURL(index: Int, length: Int): String? {
        synchronized(lock = PdfiumCore.lock) {
            try {
                val bytes = ByteArray(size = length * 2)
                val url = nativeGetURL(
                    pageLinkPtr = pageLinkPtr,
                    index = index,
                    count = length,
                    result = bytes,
                )
                if (url <= 0) {
                    return ""
                }
                return String(bytes = bytes, charset = StandardCharsets.UTF_16LE)
            } catch (e: NullPointerException) {
                Log.e(TAG, "mContext may be null", e)
            } catch (e: Exception) {
                Log.e(TAG, "Exception throw from native", e)
            }
            return null
        }
    }

    fun countRects(index: Int): Int {
        synchronized(lock = PdfiumCore.lock) {
            return nativeCountRects(pageLinkPtr = pageLinkPtr, index = index)
        }
    }

    fun getRect(linkIndex: Int, rectIndex: Int): RectF {
        synchronized(lock = PdfiumCore.lock) {
            return nativeGetRect(
                pageLinkPtr = pageLinkPtr,
                linkIndex = linkIndex,
                rectIndex = rectIndex
            ).let {
                RectF().apply {
                    left = it[0]
                    top = it[1]
                    right = it[2]
                    bottom = it[3]
                }
            }
        }
    }

    fun getTextRange(index: Int): Pair<Int, Int> {
        synchronized(lock = PdfiumCore.lock) {
            return nativeGetTextRange(pageLinkPtr = pageLinkPtr, index = index).let {
                Pair(first = it[0], second = it[1])
            }
        }
    }

    override fun close() {
        nativeClosePageLink(pageLinkPtr = pageLinkPtr)
    }

    companion object {
        private val TAG = PdfPageLink::class.java.name
    }
}