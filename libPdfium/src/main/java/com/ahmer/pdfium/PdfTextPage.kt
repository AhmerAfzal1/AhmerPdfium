package com.ahmer.pdfium

import android.graphics.RectF
import android.util.Log
import dalvik.annotation.optimization.FastNative
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

typealias FindHandle = Long

/**
 * Wrapper for native PDFium text page functionality providing text operations.
 *
 * @property doc Parent PDF document
 * @property pageIndex Index of this page in the document
 * @property pagePtr Native page pointer
 * @property pageMap Mutable map tracking page usage counts
 */
class PdfTextPage(
    val doc: PdfDocument,
    val pageIndex: Int,
    val pagePtr: Long,
    val pageMap: MutableMap<Int, PdfDocument.PageCount>,
) : Closeable {

    /**
     * Get the number of characters on this page.
     *
     * @return the number of characters on the page
     * @throws IllegalStateException if the page or document is closed
     */
    val textCharCount: Int by lazy {
        synchronized(lock = PdfiumCore.lock) {
            nativeTextCountChars(textPagePtr = pagePtr)
        }
    }

    /**
     * Get the text on the page
     *
     * @param startIndex the index of the first character to get
     * @param length the number of characters to get
     * @return the extracted text
     * @throws IllegalStateException if the page or document is closed
     */
    fun textPageGetTextLegacy(startIndex: Int, length: Int): String? {
        synchronized(lock = PdfiumCore.lock) {
            try {
                val buffer = ShortArray(size = length + 1)
                val chars = nativeTextGetText(
                    textPagePtr = pagePtr,
                    startIndex = startIndex,
                    count = length,
                    result = buffer
                )

                if (chars <= 0) return ""

                ByteBuffer.allocate((chars - 1) * 2).apply {
                    order(ByteOrder.LITTLE_ENDIAN)
                    buffer.take(n = chars - 1).forEach { putShort(it) }
                }.let {
                    return String(bytes = it.array(), charset = StandardCharsets.UTF_16LE)
                }
            } catch (e: NullPointerException) {
                Log.e(TAG, "Context may be null", e)
                return null
            } catch (e: Exception) {
                Log.e(TAG, "Exception throw from native", e)
                return null
            }
        }
    }

    fun textPageGetText(startIndex: Int, length: Int): String? {
        synchronized(lock = PdfiumCore.lock) {
            try {
                val bytes = ByteArray(size = length * 2)
                val chars = nativeTextGetTextByteArray(
                    textPagePtr = pagePtr,
                    startIndex = startIndex,
                    count = length,
                    result = bytes
                )

                if (chars <= 0) return ""
                return String(bytes = bytes, charset = StandardCharsets.UTF_16LE)
            } catch (e: NullPointerException) {
                Log.e(TAG, "Context may be null", e)
                return null
            } catch (e: Exception) {
                Log.e(TAG, "Exception throw from native", e)
                return null
            }
        }
    }

    /**
     * Get a unicode character on the page
     *
     * @param index the index of the character to get
     * @return the character
     * @throws IllegalStateException if the page or document is closed
     */
    fun textPageGetUnicode(index: Int): Char {
        synchronized(lock = PdfiumCore.lock) {
            return nativeTextGetUnicode(textPagePtr = pagePtr, index = index).toChar()
        }
    }

    /**
     * Get the bounding box for character on the page
     *
     * @param index the index of the character to get
     * @return the bounding box
     * @throws IllegalStateException if the page or document is closed
     */
    fun textPageGetCharBox(index: Int): RectF? {
        synchronized(lock = PdfiumCore.lock) {
            try {
                val charBoxData = nativeTextGetCharBox(textPagePtr = pagePtr, index = index)
                return RectF().apply {
                    left = charBoxData[0].toFloat()
                    right = charBoxData[1].toFloat()
                    bottom = charBoxData[2].toFloat()
                    top = charBoxData[3].toFloat()
                }
            } catch (e: NullPointerException) {
                Log.e(TAG, "Context may be null", e)
                return null
            } catch (e: Exception) {
                Log.e(TAG, "Exception throw from native", e)
                return null
            }
        }
    }

    /**
     * Finds character index at specified position.
     *
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
                return -1
            }
        }
    }

    /**
     * Counts text rectangles in specified range
     *
     * @param startIndex the index of the first character to get
     * @param count the number of characters to get
     * @return the number of rectangles
     * @throws IllegalStateException if the page or document is closed
     */
    fun textPageCountRects(startIndex: Int, count: Int): Int {
        synchronized(lock = PdfiumCore.lock) {
            try {
                return nativeTextCountRects(
                    textPagePtr = pagePtr,
                    startIndex = startIndex,
                    count = count
                )
            } catch (e: NullPointerException) {
                Log.e(TAG, "Context may be null", e)
                return -1
            } catch (e: Exception) {
                Log.e(TAG, "Exception throw from native", e)
                return -1
            }
        }
    }

    /**
     * Get the bounding box of a text on the page
     *
     * @param rectIndex the index of the rectangle to get
     * @return the bounding box
     * @throws IllegalStateException if the page or document is closed
     */
    fun textPageGetRect(rectIndex: Int): RectF? {
        synchronized(lock = PdfiumCore.lock) {
            try {
                val rectData = nativeTextGetRect(textPagePtr = pagePtr, rectIndex = rectIndex)
                return RectF().apply {
                    left = rectData[0].toFloat()
                    top = rectData[1].toFloat()
                    right = rectData[2].toFloat()
                    bottom = rectData[3].toFloat()
                }
            } catch (e: NullPointerException) {
                Log.e(TAG, "Context may be null", e)
                return null
            } catch (e: Exception) {
                Log.e(TAG, "Exception throw from native", e)
                return null
            }
        }
    }

    /**
     * Get the bounding box of a range of texts on the page
     *
     * @param wordRanges an array of word ranges to get the bounding boxes for.
     * Even indices are the start index, odd indices are the length
     * @return list of bounding boxes with their start and length
     * @throws IllegalStateException if the page or document is closed
     */
    fun textPageGetRectsForRanges(wordRanges: IntArray): List<WordRangeRect>? {
        synchronized(lock = PdfiumCore.lock) {
            try {
                val data: DoubleArray = nativeTextGetRects(
                    textPagePtr = pagePtr,
                    wordRanges = wordRanges
                ) ?: return null
                val results = mutableListOf<WordRangeRect>()

                for (i in data.indices step RANGE_RECT_DATA_SIZE) {
                    results.add(
                        WordRangeRect(
                            rangeStart = data[i + RANGE_START_OFFSET].toInt(),
                            rangeLength = data[i + RANGE_LENGTH_OFFSET].toInt(),
                            rect = RectF().apply {
                                left = data[i + LEFT_OFFSET].toFloat()
                                top = data[i + TOP_OFFSET].toFloat()
                                right = data[i + RIGHT_OFFSET].toFloat()
                                bottom = data[i + BOTTOM_OFFSET].toFloat()
                            }
                        )
                    )
                }
                return results
            } catch (e: Exception) {
                Log.e(TAG, "Exception throw from native: ${e.message}", e)
                return null
            }
        }
    }

    /**
     * Get the text bounded by the given rectangle
     * @param rect the rectangle to bound the text
     * @param length the maximum number of characters to get
     * @return the text bounded by the rectangle
     * @throws IllegalStateException if the page or document is closed
     */
    fun textPageGetBoundedText(rect: RectF, length: Int): String? {
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
                if (textRect <= 0) return ""

                val byteBuffer = ByteBuffer.allocate((textRect - 1) * 2)
                byteBuffer.order(ByteOrder.LITTLE_ENDIAN)
                for (i in 0 until textRect - 1) {
                    byteBuffer.putShort(buffer[i])
                }
                return String(bytes = byteBuffer.array(), charset = StandardCharsets.UTF_16LE)
            } catch (e: NullPointerException) {
                Log.e(TAG, "Context may be null", e)
                return null
            } catch (e: Exception) {
                Log.e(TAG, "Exception throw from native", e)
                return null
            }
        }
    }

    /**
     * Get character font size in PostScript points (1/72th of an inch).
     * @param charIndex the index of the character to get
     * @return the font size
     * @throws IllegalStateException if the page or document is closed
     */
    fun getFontSize(charIndex: Int): Double {
        synchronized(lock = PdfiumCore.lock) {
            return nativeGetFontSize(pagePtr = pagePtr, charIndex = charIndex)
        }
    }

    /**
     * Starts text search operation.
     *
     * @param findWhat Text to search for
     * @param flags Search configuration flags
     * @param startIndex Starting character index
     * @return Search result handle or null on error
     */
    fun findStart(
        findWhat: String,
        flags: Set<FindFlags>,
        startIndex: Int,
    ): FindResult? {
        synchronized(lock = PdfiumCore.lock) {
            try {
                val flag = flags.fold(initial = 0) { acc, flag -> acc or flag.value }
                return FindResult(
                    nativeFindStart(
                        textPagePtr = pagePtr,
                        findWhat = findWhat,
                        flags = flag,
                        startIndex = startIndex
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error in findStart: ${e.message}")
                return null
            }
        }
    }

    /**
     * Loads web link annotations for the page.
     *
     * @return Page link object
     * @throws IllegalStateException If page is closed
     */
    fun loadWebLink(): PdfPageLink {
        return PdfPageLink(pageLinkPtr = nativeLoadWebLink(textPagePtr = pagePtr))
    }

    /**
     * Get character count of the page
     *
     * @return the number of characters on the page
     * @throws IllegalStateException if the page or document is closed
     */
    val textPageCountChars: Int by lazy {
        synchronized(lock = PdfiumCore.lock) {
            nativeTextCountChars(textPagePtr = pagePtr)
        }
    }

    /**
     * Close the page and release all resources
     */
    override fun close() {
        synchronized(lock = PdfiumCore.lock) {
            pageMap[pageIndex]?.let {
                if (it.count > 1) {
                    it.count--
                    return
                }
            }
            pageMap.remove(key = pageIndex)
            nativeCloseTextPage(pagePtr = pagePtr)
            pageMap.clear()
        }
    }

    companion object {
        private const val LEFT_OFFSET = 0
        private const val TOP_OFFSET = 1
        private const val RIGHT_OFFSET = 2
        private const val BOTTOM_OFFSET = 3
        private const val RANGE_START_OFFSET = 4
        private const val RANGE_LENGTH_OFFSET = 5
        private const val RANGE_RECT_DATA_SIZE = 6
        private val TAG: String? = PdfTextPage::class.java.name

        @JvmStatic
        private external fun nativeCloseTextPage(pagePtr: Long)

        @JvmStatic
        private external fun nativeFindStart(
            textPagePtr: Long, findWhat: String, flags: Int, startIndex: Int,
        ): Long

        @JvmStatic
        @FastNative
        private external fun nativeGetFontSize(pagePtr: Long, charIndex: Int): Double

        @JvmStatic
        private external fun nativeLoadWebLink(textPagePtr: Long): Long

        @JvmStatic
        @FastNative
        private external fun nativeTextCountChars(textPagePtr: Long): Int

        @JvmStatic
        @FastNative
        private external fun nativeTextCountRects(
            textPagePtr: Long, startIndex: Int, count: Int
        ): Int

        @JvmStatic
        @FastNative
        private external fun nativeTextGetBoundedText(
            textPagePtr: Long, left: Double, top: Double,
            right: Double, bottom: Double, arr: ShortArray
        ): Int

        @JvmStatic
        @FastNative
        private external fun nativeTextGetCharBox(textPagePtr: Long, index: Int): DoubleArray

        @JvmStatic
        private external fun nativeTextGetCharIndexAtPos(
            textPagePtr: Long, x: Double, y: Double, xTolerance: Double, yTolerance: Double
        ): Int

        @JvmStatic
        @FastNative
        private external fun nativeTextGetRect(textPagePtr: Long, rectIndex: Int): DoubleArray

        @JvmStatic
        @FastNative
        private external fun nativeTextGetRects(
            textPagePtr: Long, wordRanges: IntArray
        ): DoubleArray?

        @JvmStatic
        private external fun nativeTextGetText(
            textPagePtr: Long, startIndex: Int, count: Int, result: ShortArray
        ): Int

        @JvmStatic
        private external fun nativeTextGetTextByteArray(
            textPagePtr: Long, startIndex: Int, count: Int, result: ByteArray
        ): Int

        @JvmStatic
        @FastNative
        private external fun nativeTextGetUnicode(textPagePtr: Long, index: Int): Int
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