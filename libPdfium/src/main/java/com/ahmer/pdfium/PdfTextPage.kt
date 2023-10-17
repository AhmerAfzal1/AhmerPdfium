package com.ahmer.pdfium

import android.graphics.RectF
import android.util.Log
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

/**
 * PdfTextPage is a wrapper around the native PdfiumCore text page
 * It is used to get text and other information about the text on a page
 * @property document the PdfDocument this page belongs to
 * @property pageIndex the index of this page in the document
 * @property pagePtr the pointer to the native page
 */
class PdfTextPage(
    val document: PdfDocument, val pageIndex: Int, val pagePtr: Long,
    val pageMap: MutableMap<Int, PdfDocument.PageCount>
) : Closeable {
    private var isClosed = false

    private external fun nativeCloseTextPage(pagePtr: Long)
    private external fun nativeGetFontSize(pagePtr: Long, charIndex: Int): Double
    private external fun nativeTextCountChars(textPagePtr: Long): Int
    private external fun nativeTextCountRects(textPagePtr: Long, startIndex: Int, count: Int): Int
    private external fun nativeTextGetBoundedText(
        textPagePtr: Long, left: Double, top: Double, right: Double, bottom: Double, arr: ShortArray
    ): Int

    private external fun nativeTextGetCharBox(textPagePtr: Long, index: Int): DoubleArray
    private external fun nativeTextGetCharIndexAtPos(
        textPagePtr: Long, x: Double, y: Double, xTolerance: Double, yTolerance: Double
    ): Int

    private external fun nativeTextGetRect(textPagePtr: Long, rectIndex: Int): DoubleArray
    private external fun nativeTextGetText(
        textPagePtr: Long, startIndex: Int, count: Int, result: ShortArray
    ): Int

    private external fun nativeTextGetTextByteArray(
        textPagePtr: Long, startIndex: Int, count: Int, result: ByteArray
    ): Int

    private external fun nativeTextGetUnicode(textPagePtr: Long, index: Int): Int

    /**
     * Get character count of the page
     * @return the number of characters on the page
     * @throws IllegalStateException if the page or document is closed
     */
    val textPageCountChars: Int by lazy {
        check(value = !isClosed && !document.isClosed) { "Already closed" }
        synchronized(lock = PdfiumCore.lock) {
            nativeTextCountChars(textPagePtr = pagePtr)
        }
    }

    /**
     * Get the text on the page
     * @param startIndex the index of the first character to get
     * @param length the number of characters to get
     * @return the text
     * @throws IllegalStateException if the page or document is closed
     */
    fun textPageGetTextLegacy(startIndex: Int, length: Int): String? {
        check(value = !isClosed && !document.isClosed) { "Already closed" }
        synchronized(lock = PdfiumCore.lock) {
            try {
                val buffer = ShortArray(size = length + 1)
                val chars = nativeTextGetText(
                    textPagePtr = pagePtr, startIndex = startIndex, count = length, result = buffer
                )
                if (chars <= 0) {
                    return ""
                }
                val bytes = ByteArray(size = (chars - 1) * 2)
                val byteBuffer = ByteBuffer.wrap(bytes)
                byteBuffer.order(ByteOrder.LITTLE_ENDIAN)
                for (i in 0 until chars - 1) {
                    byteBuffer.putShort(buffer[i])
                }
                return String(bytes = bytes, charset = StandardCharsets.UTF_16LE)
            } catch (e: NullPointerException) {
                Log.e(TAG, "Context may be null", e)
            } catch (e: Exception) {
                Log.e(TAG, "Exception throw from native", e)
            }
            return null
        }
    }

    fun textPageGetText(startIndex: Int, length: Int): String? {
        check(value = !isClosed && !document.isClosed) { "Already closed" }
        synchronized(lock = PdfiumCore.lock) {
            try {
                val bytes = ByteArray(size = length * 2)
                val chars = nativeTextGetTextByteArray(
                    textPagePtr = pagePtr, startIndex = startIndex, count = length, result = bytes
                )
                if (chars <= 0) {
                    return ""
                }
                return String(bytes = bytes, charset = StandardCharsets.UTF_16LE)
            } catch (e: NullPointerException) {
                Log.e(TAG, "Context may be null", e)
            } catch (e: Exception) {
                Log.e(TAG, "Exception throw from native", e)
            }
            return null
        }
    }

    /**
     * Get a unicode character on the page
     * @param index the index of the character to get
     * @return the character
     * @throws IllegalStateException if the page or document is closed
     */
    fun textPageGetUnicode(index: Int): Char {
        check(value = !isClosed && !document.isClosed) { "Already closed" }
        synchronized(lock = PdfiumCore.lock) {
            return nativeTextGetUnicode(textPagePtr = pagePtr, index = index).toChar()
        }
    }

    /**
     * Get the bounding box of a character on the page
     * @param index the index of the character to get
     * @return the bounding box
     * @throws IllegalStateException if the page or document is closed
     */
    fun textPageGetCharBox(index: Int): RectF? {
        check(value = !isClosed && !document.isClosed) { "Already closed" }
        synchronized(lock = PdfiumCore.lock) {
            try {
                val odd = nativeTextGetCharBox(textPagePtr = pagePtr, index = index)
                // Note these are in an odd order left, right, bottom, top
                // What Pdfium native code returns
                return RectF().apply {
                    left = odd[0].toFloat()
                    right = odd[1].toFloat()
                    bottom = odd[2].toFloat()
                    top = odd[3].toFloat()
                }
            } catch (e: NullPointerException) {
                Log.e(TAG, "Context may be null", e)
            } catch (e: Exception) {
                Log.e(TAG, "Exception throw from native", e)
            }
        }
        return null
    }

    /**
     * Get the index of the character at a given position on the page
     * @param x the x position
     * @param y the y position
     * @param xTolerance the x tolerance
     * @param yTolerance the y tolerance
     * @return the index of the character at the position
     * @throws IllegalStateException if the page or document is closed
     */
    fun textPageGetCharIndexAtPos(
        x: Double, y: Double, xTolerance: Double, yTolerance: Double
    ): Int {
        check(value = !isClosed && !document.isClosed) { "Already closed" }
        synchronized(lock = PdfiumCore.lock) {
            try {
                return nativeTextGetCharIndexAtPos(
                    textPagePtr = pagePtr, x = x, y = y,
                    xTolerance = xTolerance, yTolerance = yTolerance
                )
            } catch (e: Exception) {
                Log.e(TAG, "Exception throw from native", e)
            }
        }
        return -1
    }

    /**
     * Get the count of rectages that bound the text on the page in a given range
     * @param startIndex the index of the first character to get
     * @param count the number of characters to get
     * @return the number of rectangles
     * @throws IllegalStateException if the page or document is closed
     */
    fun textPageCountRects(startIndex: Int, count: Int): Int {
        check(value = !isClosed && !document.isClosed) { "Already closed" }
        synchronized(lock = PdfiumCore.lock) {
            try {
                return nativeTextCountRects(
                    textPagePtr = pagePtr, startIndex = startIndex, count = count
                )
            } catch (e: NullPointerException) {
                Log.e(TAG, "Context may be null", e)
            } catch (e: Exception) {
                Log.e(TAG, "Exception throw from native", e)
            }
        }
        return -1
    }

    /**
     * Get the bounding box of a text on the page
     * @param rectIndex the index of the rectangle to get
     * @return the bounding box
     * @throws IllegalStateException if the page or document is closed
     */
    fun textPageGetRect(rectIndex: Int): RectF? {
        check(value = !isClosed && !document.isClosed) { "Already closed" }
        synchronized(lock = PdfiumCore.lock) {
            try {
                val odd = nativeTextGetRect(textPagePtr = pagePtr, rectIndex = rectIndex)
                return RectF().apply {
                    left = odd[0].toFloat()
                    top = odd[1].toFloat()
                    right = odd[2].toFloat()
                    bottom = odd[3].toFloat()
                }
            } catch (e: NullPointerException) {
                Log.e(TAG, "Context may be null", e)
            } catch (e: Exception) {
                Log.e(TAG, "Exception throw from native", e)
            }
        }
        return null
    }

    /**
     * Get the text bounded by the given rectangle
     * @param rect the rectangle to bound the text
     * @param length the maximum number of characters to get
     * @return the text bounded by the rectangle
     * @throws IllegalStateException if the page or document is closed
     */
    fun textPageGetBoundedText(rect: RectF, length: Int): String? {
        check(value = !isClosed && !document.isClosed) { "Already closed" }
        synchronized(lock = PdfiumCore.lock) {
            try {
                val buffer = ShortArray(size = length + 1)
                val textRect = nativeTextGetBoundedText(
                    textPagePtr = pagePtr, left = rect.left.toDouble(), top = rect.top.toDouble(),
                    right = rect.right.toDouble(), bottom = rect.bottom.toDouble(), arr = buffer
                )
                val bytes = ByteArray(size = (textRect - 1) * 2)
                val byteBuffer = ByteBuffer.wrap(bytes)
                byteBuffer.order(ByteOrder.LITTLE_ENDIAN)
                for (i in 0 until textRect - 1) {
                    byteBuffer.putShort(buffer[i])
                }
                return String(bytes = bytes, charset = StandardCharsets.UTF_16LE)
            } catch (e: NullPointerException) {
                Log.e(TAG, "Context may be null", e)
            } catch (e: Exception) {
                Log.e(TAG, "Exception throw from native", e)
            }
            return null
        }
    }

    /**
     * Get character font size in PostScript points (1/72th of an inch).<br></br>
     * @param charIndex the index of the character to get
     * @return the font size
     * @throws IllegalStateException if the page or document is closed
     */
    fun getFontSize(charIndex: Int): Double {
        check(value = !isClosed && !document.isClosed) { "Already closed" }
        synchronized(lock = PdfiumCore.lock) {
            return nativeGetFontSize(pagePtr = pagePtr, charIndex = charIndex)
        }
    }

    /**
     * Close the page and release all resources
     */
    override fun close() {
        if (isClosed) return
        synchronized(lock = PdfiumCore.lock) {
            pageMap[pageIndex]?.let {
                if (it.count > 1) {
                    it.count--
                    return
                }
            }
            pageMap.remove(key = pageIndex)
            isClosed = true
            nativeCloseTextPage(pagePtr = pagePtr)
        }
    }

    companion object {
        private val TAG = PdfTextPage::class.java.name
    }
}