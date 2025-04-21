package com.ahmer.pdfium

import android.graphics.RectF
import android.util.Log
import com.ahmer.pdfium.util.handleAlreadyClosed
import dalvik.annotation.optimization.FastNative
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

typealias FindHandle = Long

private const val LEFT_OFFSET = 0
private const val TOP_OFFSET = 1
private const val RIGHT_OFFSET = 2
private const val BOTTOM_OFFSET = 3
private const val RANGE_START_OFFSET = 4
private const val RANGE_LENGTH_OFFSET = 5

private const val RANGE_RECT_DATA_SIZE = 6

/**
 * PdfTextPage is a wrapper around the native PdfiumCore text page
 * It is used to get text and other information about the text on a page
 * @property doc the PdfDocument this page belongs to
 * @property pageIndex the index of this page in the document
 * @property pagePtr the pointer to the native page
 */
class PdfTextPage(
    val doc: PdfDocument,
    val pageIndex: Int,
    val pagePtr: Long,
    val pageMap: MutableMap<Int, PdfDocument.PageCount>,
) : Closeable {
    private var isClosed = false

    private external fun nativeCloseTextPage(pagePtr: Long)

    private external fun nativeFindStart(
        textPagePtr: Long, findWhat: String, flags: Int, startIndex: Int,
    ): Long

    @FastNative
    private external fun nativeGetFontSize(pagePtr: Long, charIndex: Int): Double

    private external fun nativeLoadWebLink(textPagePtr: Long): Long

    @FastNative
    private external fun nativeTextCountChars(textPagePtr: Long): Int

    @FastNative
    private external fun nativeTextCountRects(textPagePtr: Long, startIndex: Int, count: Int): Int

    @FastNative
    private external fun nativeTextGetBoundedText(
        textPagePtr: Long, left: Double, top: Double, right: Double, bottom: Double, arr: ShortArray
    ): Int

    @FastNative
    private external fun nativeTextGetCharBox(textPagePtr: Long, index: Int): DoubleArray

    private external fun nativeTextGetCharIndexAtPos(
        textPagePtr: Long, x: Double, y: Double, xTolerance: Double, yTolerance: Double
    ): Int

    @FastNative
    private external fun nativeTextGetRect(textPagePtr: Long, rectIndex: Int): DoubleArray

    @FastNative
    private external fun nativeTextGetRects(textPagePtr: Long, wordRanges: IntArray): DoubleArray?

    private external fun nativeTextGetText(
        textPagePtr: Long, startIndex: Int, count: Int, result: ShortArray
    ): Int

    private external fun nativeTextGetTextByteArray(
        textPagePtr: Long, startIndex: Int, count: Int, result: ByteArray
    ): Int

    @FastNative
    private external fun nativeTextGetUnicode(textPagePtr: Long, index: Int): Int

    /**
     * Get character count of the page
     * @return the number of characters on the page
     * @throws IllegalStateException if the page or document is closed
     */
    fun textPageCountChars(): Int {
        if (handleAlreadyClosed(isClosed = isClosed || doc.isClosed)) return -1
        synchronized(PdfiumCore.lock) {
            return nativeTextCountChars(textPagePtr = pagePtr)
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
        if (handleAlreadyClosed(isClosed = isClosed || doc.isClosed)) return null
        synchronized(lock = PdfiumCore.lock) {
            try {
                val buffer = ShortArray(size = length + 1)
                val chars = nativeTextGetText(
                    textPagePtr = pagePtr,
                    startIndex = startIndex,
                    count = length,
                    result = buffer
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
        if (handleAlreadyClosed(isClosed = isClosed || doc.isClosed)) return null
        synchronized(lock = PdfiumCore.lock) {
            try {
                val bytes = ByteArray(size = length * 2)
                val chars = nativeTextGetTextByteArray(
                    textPagePtr = pagePtr,
                    startIndex = startIndex,
                    count = length,
                    result = bytes
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
        check(value = !isClosed && !doc.isClosed) { "Already closed" }
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
        if (handleAlreadyClosed(isClosed = isClosed || doc.isClosed)) return null
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
        x: Double,
        y: Double,
        xTolerance: Double,
        yTolerance: Double
    ): Int {
        if (handleAlreadyClosed(isClosed = isClosed || doc.isClosed)) return -1
        synchronized(lock = PdfiumCore.lock) {
            try {
                return nativeTextGetCharIndexAtPos(
                    textPagePtr = pagePtr,
                    x = x,
                    y = y,
                    xTolerance = xTolerance,
                    yTolerance = yTolerance
                )
            } catch (e: Exception) {
                Log.e(TAG, "Exception throw from native", e)
            }
        }
        return -1
    }

    /**
     * Get the count of rectangles that bound the text on the page in a given range
     * @param startIndex the index of the first character to get
     * @param count the number of characters to get
     * @return the number of rectangles
     * @throws IllegalStateException if the page or document is closed
     */
    fun textPageCountRects(startIndex: Int, count: Int): Int {
        check(value = !isClosed && !doc.isClosed) { "Already closed" }
        synchronized(lock = PdfiumCore.lock) {
            try {
                return nativeTextCountRects(
                    textPagePtr = pagePtr,
                    startIndex = startIndex,
                    count = count
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
        if (handleAlreadyClosed(isClosed = isClosed || doc.isClosed)) return null
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
     * Get the bounding box of a range of texts on the page
     * @param wordRanges an array of word ranges to get the bounding boxes for.
     * Even indices are the start index, odd indices are the length
     * @return list of bounding boxes with their start and length
     * @throws IllegalStateException if the page or document is closed
     */
    fun textPageGetRectsForRanges(wordRanges: IntArray): List<WordRangeRect>? {
        if (handleAlreadyClosed(isClosed = isClosed || doc.isClosed)) return null
        synchronized(PdfiumCore.lock) {
            val data = nativeTextGetRects(textPagePtr = pagePtr, wordRanges = wordRanges)
            if (data != null) {
                val wordRangeRects = mutableListOf<WordRangeRect>()
                for (i in data.indices step RANGE_RECT_DATA_SIZE) {
                    val rectF = RectF().apply {
                        left = data[i + LEFT_OFFSET].toFloat()
                        top = data[i + TOP_OFFSET].toFloat()
                        right = data[i + RIGHT_OFFSET].toFloat()
                        bottom = data[i + BOTTOM_OFFSET].toFloat()
                    }

                    val rangeStart = data[i + RANGE_START_OFFSET].toInt()
                    val rangeLength = data[i + RANGE_LENGTH_OFFSET].toInt()
                    WordRangeRect(
                        rangeStart = rangeStart,
                        rangeLength = rangeLength,
                        rect = rectF
                    ).let {
                        wordRangeRects.add(it)
                    }
                }
                return wordRangeRects
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
        if (handleAlreadyClosed(isClosed = isClosed || doc.isClosed)) return null
        synchronized(lock = PdfiumCore.lock) {
            try {
                val buffer = ShortArray(size = length + 1)
                val textRect = nativeTextGetBoundedText(
                    textPagePtr = pagePtr,
                    left = rect.left.toDouble(),
                    top = rect.top.toDouble(),
                    right = rect.right.toDouble(),
                    bottom = rect.bottom.toDouble(),
                    arr = buffer
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
     * Get character font size in PostScript points (1/72th of an inch).
     * @param charIndex the index of the character to get
     * @return the font size
     * @throws IllegalStateException if the page or document is closed
     */
    fun getFontSize(charIndex: Int): Double {
        if (handleAlreadyClosed(isClosed = isClosed || doc.isClosed)) return 0.0
        synchronized(lock = PdfiumCore.lock) {
            return nativeGetFontSize(pagePtr = pagePtr, charIndex = charIndex)
        }
    }

    fun findStart(
        findWhat: String,
        flags: Set<FindFlags>,
        startIndex: Int,
    ): FindResult? {
        if (handleAlreadyClosed(isClosed = isClosed || doc.isClosed)) return null
        synchronized(PdfiumCore.lock) {
            val apiFlags = flags.fold(initial = 0) { acc, flag -> acc or flag.value }
            return FindResult(
                nativeFindStart(
                    textPagePtr = pagePtr,
                    findWhat = findWhat,
                    flags = apiFlags,
                    startIndex = startIndex
                )
            )
        }
    }

    fun loadWebLink(): PdfPageLink {
        check(value = !isClosed && !doc.isClosed) { "Already closed" }
        val linkPtr = nativeLoadWebLink(textPagePtr = pagePtr)
        return PdfPageLink(pageLinkPtr = linkPtr)
    }

    /**
     * Get character count of the page
     * @return the number of characters on the page
     * @throws IllegalStateException if the page or document is closed
     */
    val textPageCountChars: Int by lazy {
        check(value = !isClosed && !doc.isClosed) { "Already closed" }
        synchronized(lock = PdfiumCore.lock) {
            nativeTextCountChars(textPagePtr = pagePtr)
        }
    }

    /**
     * Close the page and release all resources
     */
    override fun close() {
        if (handleAlreadyClosed(isClosed || doc.isClosed)) return
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

enum class FindFlags(val value: Int) {
    MatchCase(value = 0x00000001),
    MatchWholeWord(value = 0x00000002),
    Consecutive(value = 0x00000004),
}

data class WordRangeRect(
    val rangeStart: Int,
    val rangeLength: Int,
    val rect: RectF,
)