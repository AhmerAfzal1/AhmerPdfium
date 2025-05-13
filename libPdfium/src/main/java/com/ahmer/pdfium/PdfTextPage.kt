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
    val charCount: Int by lazy {
        synchronized(lock = PdfiumCore.lock) {
            nativeTextCountChars(textPagePtr = pagePtr).also {
                if (it < 0) throw IllegalStateException("Failed to get character count")
            }
        }
    }

    private val pageLinkPtr: Long by lazy {
        synchronized(lock = PdfiumCore.lock) {
            nativeLoadWebLink(textPagePtr = pagePtr).also {
                if (it == 0L) throw IllegalStateException("Failed to load page links")
            }
        }
    }

    /**
     * Gets the number of web links present in the PDF page.
     *
     * @return Total count of web links.
     */
    val webLinksCount: Int by lazy {
        synchronized(lock = PdfiumCore.lock) {
            nativeCountWebLinks(pageLinkPtr = pageLinkPtr)
        }
    }

    /**
     * Retrieves text content from the page using legacy buffer approach.
     *
     * @param startIndex the index of the first character to get
     * @param length the number of characters to get
     * @return the extracted text
     * @throws IllegalStateException if the page or document is closed
     */
    fun getTextLegacy(startIndex: Int, length: Int): String? {
        require(value = startIndex >= 0) { "Start index cannot be negative" }
        require(value = length >= 0) { "Length cannot be negative" }
        require(value = startIndex + length <= charCount) { "Requested range exceeds character count" }
        synchronized(lock = PdfiumCore.lock) {
            return try {
                val buffer = ShortArray(size = length + 1)
                val chars = nativeTextGetText(
                    textPagePtr = pagePtr,
                    startIndex = startIndex,
                    count = length,
                    result = buffer
                )

                if (chars > 0) {
                    ByteBuffer.allocate((chars - 1) * 2).apply {
                        order(ByteOrder.LITTLE_ENDIAN)
                        buffer.take(n = chars - 1).forEach { putShort(it) }
                    }.array().let {
                        String(bytes = it, charset = StandardCharsets.UTF_16LE)
                    }
                } else ""
            } catch (e: Exception) {
                Log.e(TAG, "Error retrieving text (legacy): ${e.message}", e)
                null
            }
        }
    }

    /**
     * Retrieves text content from the page using optimized byte array approach.
     *
     * @param startIndex the index of the first character to get
     * @param length the number of characters to get
     * @return the extracted text
     * @throws IllegalStateException if the page or document is closed
     */
    fun getText(startIndex: Int = 0, length: Int = charCount): String? {
        require(value = startIndex >= 0) { "Start index cannot be negative" }
        require(value = length >= 0) { "Length cannot be negative" }
        require(value = startIndex + length <= charCount) { "Requested range exceeds character count" }

        synchronized(lock = PdfiumCore.lock) {
            return try {
                ByteArray(size = length * 2).let { buffer ->
                    val chars = nativeTextGetTextByteArray(
                        textPagePtr = pagePtr,
                        startIndex = startIndex,
                        count = length,
                        result = buffer
                    )
                    if (chars <= 0) return ""
                    String(bytes = buffer, charset = StandardCharsets.UTF_16LE)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error retrieving text: ${e.message}", e)
                null
            }
        }
    }

    /**
     * Gets the Unicode character at specified index.
     *
     * @param index the index of the character to get
     * @return the unicode character
     * @throws IllegalStateException if the page or document is closed
     */
    fun getUnicodeChar(index: Int): Char {
        require(value = index in 0 until charCount) { "Index $index out of bounds [0, $charCount)" }
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
    fun getCharBox(index: Int): RectF? {
        require(value = index in 0 until charCount) { "Index $index out of bounds [0, $charCount)" }
        synchronized(lock = PdfiumCore.lock) {
            return try {
                nativeTextGetCharBox(textPagePtr = pagePtr, index = index).let { data ->
                    RectF().apply {
                        left = data[0].toFloat()
                        right = data[1].toFloat()
                        bottom = data[2].toFloat()
                        top = data[3].toFloat()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting character bounds: ${e.message}", e)
                null
            }
        }
    }

    /**
     * Finds character index at specified position.
     *
     * @param x horizontal page coordinate
     * @param y vertical page coordinate
     * @param xTolerance horizontal search radius (must be ≥ 0)
     * @param yTolerance vertical search radius (must be ≥ 0)
     * @return character index or -1 if not found/error
     * @throws IllegalStateException if the page or document is closed
     */
    fun findCharIndexAtPos(x: Double, y: Double, xTolerance: Double, yTolerance: Double): Int {
        require(value = xTolerance >= 0) { "X tolerance cannot be negative" }
        require(value = yTolerance >= 0) { "Y tolerance cannot be negative" }
        synchronized(lock = PdfiumCore.lock) {
            return try {
                nativeTextGetCharIndexAtPos(
                    textPagePtr = pagePtr,
                    x = x,
                    y = y,
                    xTolerance = xTolerance,
                    yTolerance = yTolerance
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to find character at position ($x,$y): ${e.message}", e)
                -1
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
    fun countTextRects(startIndex: Int, count: Int): Int {
        require(value = startIndex >= 0) { "Start index cannot be negative" }
        require(value = count > 0) { "Count must be positive" }
        synchronized(lock = PdfiumCore.lock) {
            return try {
                nativeTextCountRects(
                    textPagePtr = pagePtr,
                    startIndex = startIndex,
                    count = count
                )
            } catch (e: Exception) {
                val msg =
                    "Failed to count rectangles for range [$startIndex, ${startIndex + count}]: ${e.message}"
                Log.e(TAG, msg, e)
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
    fun getTextRect(rectIndex: Int): RectF? {
        synchronized(lock = PdfiumCore.lock) {
            return try {
                nativeTextGetRect(textPagePtr = pagePtr, rectIndex = rectIndex).let { data ->
                    RectF().apply {
                        left = data[0].toFloat()
                        top = data[1].toFloat()
                        right = data[2].toFloat()
                        bottom = data[3].toFloat()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get rectangle at index $rectIndex: ${e.message}", e)
                null
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
    fun getTextRangeRects(wordRanges: IntArray): List<WordRangeRect>? {
        synchronized(lock = PdfiumCore.lock) {
            return try {
                nativeTextGetRects(textPagePtr = pagePtr, wordRanges = wordRanges)?.let { data ->
                    List(size = data.size / 6) { i ->
                        val offset = i * 6
                        WordRangeRect(
                            rangeStart = data[offset + 4].toInt(),
                            rangeLength = data[offset + 5].toInt(),
                            rect = RectF(
                                data[offset].toFloat(),
                                data[offset + 1].toFloat(),
                                data[offset + 2].toFloat(),
                                data[offset + 3].toFloat()
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get range rectangles: ${e.message}", e)
                null
            }
        }
    }

    /**
     * Get the text bounded by the given rectangle
     *
     * @param rect the rectangle to bound the text
     * @param length the maximum number of characters to get
     * @return the text bounded by the rectangle
     * @throws IllegalStateException if the page or document is closed
     */
    fun extractTextInArea(rect: RectF, length: Int): String? {
        synchronized(lock = PdfiumCore.lock) {
            return try {
                val buffer = ShortArray(size = length + 1)
                val textRect = nativeTextGetBoundedText(
                    textPagePtr = pagePtr,
                    left = rect.left.toDouble(),
                    top = rect.top.toDouble(),
                    right = rect.right.toDouble(),
                    bottom = rect.bottom.toDouble(),
                    arr = buffer
                )
                if (textRect > 0) {
                    ByteBuffer.allocate((textRect - 1) * 2).apply {
                        order(ByteOrder.LITTLE_ENDIAN)
                        for (i in 0 until textRect - 1) {
                            putShort(buffer[i])
                        }
                    }.array().let {
                        String(bytes = it, charset = StandardCharsets.UTF_16LE)
                    }
                } else ""
            } catch (e: Exception) {
                Log.e(TAG, "Failed to extract text in area $rect: ${e.message}", e)
                null
            }
        }
    }

    /**
     * Get character font size in PostScript points (1/72th of an inch).
     *
     * @param charIndex the index of the character to get
     * @return the font size
     * @throws IllegalStateException if the page or document is closed
     */
    fun getFontSize(charIndex: Int): Double {
        require(value = charIndex in 0 until charCount) { "Invalid character index" }
        synchronized(lock = PdfiumCore.lock) {
            return try {
                nativeGetFontSize(pagePtr = pagePtr, charIndex = charIndex)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get font size for character $charIndex: ${e.message}", e)
                Double.NaN
            }
        }
    }

    /**
     * Starts text search operation.
     *
     * @param query text to search for
     * @param flags search configuration flags
     * @param startIndex starting character index
     * @return search result handle or null on error
     */
    fun startTextSearch(
        query: String,
        flags: Set<FindFlags> = emptySet(),
        startIndex: Int = 0
    ): FindResult? {
        require(value = query.isNotEmpty()) { "Search query cannot be empty" }
        require(value = startIndex >= 0) { "Start index cannot be negative" }
        synchronized(lock = PdfiumCore.lock) {
            return try {
                val flag = flags.fold(initial = 0) { acc, flag -> acc or flag.value }
                nativeFindStart(
                    textPagePtr = pagePtr,
                    findWhat = query,
                    flags = flag,
                    startIndex = startIndex
                ).takeIf { it != 0L }?.let { FindResult(handle = it) }

            } catch (e: Exception) {
                Log.e(TAG, "Failed to start search for '$query': ${e.message}", e)
                null
            }
        }
    }


    /**
     * Retrieves the URL text for a given web link on this PDF page.
     *
     * @param linkIndex index of the web link to query.
     * @param charCount expected number of characters in the URL.
     * @return url string if successful, empty string for invalid links, or null on error.
     */
    fun getLinkUrl(linkIndex: Int, charCount: Int): String? {
        require(value = linkIndex in 0 until webLinksCount) { "Invalid web link index" }
        synchronized(lock = PdfiumCore.lock) {
            return try {
                val buffer = ByteArray(size = charCount * 2)
                val bytesWritten = nativeGetURL(
                    pageLinkPtr = pageLinkPtr,
                    index = linkIndex,
                    count = charCount,
                    result = buffer,
                )
                when {
                    bytesWritten <= 0 -> ""
                    else -> String(bytes = buffer, charset = StandardCharsets.UTF_16LE)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get URL for link $linkIndex: ${e.message}", e)
                null
            }
        }
    }

    /**
     * Retrieves the number of rectangular areas associated with a specific link.
     *
     * @param linkIndex Index of the link to examine.
     * @return Count of rectangular regions for the specified link.
     */
    fun countRects(linkIndex: Int): Int {
        require(value = linkIndex in 0 until webLinksCount) { "Invalid web link index" }
        synchronized(lock = PdfiumCore.lock) {
            return try {
                nativeCountRects(pageLinkPtr = pageLinkPtr, index = linkIndex)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to count regions for link $linkIndex: ${e.message}", e)
                -1
            }
        }
    }

    /**
     * Retrieves the bounding rectangle for a specific link region.
     *
     * @param linkIndex Index of the target link.
     * @param rectIndex Index of the rectangular region within the link.
     * @return [RectF] representing the link's bounding box.
     */
    fun getLinkRect(linkIndex: Int, rectIndex: Int): RectF? {
        require(value = linkIndex in 0 until webLinksCount) { "Invalid web link index" }
        require(value = rectIndex in 0 until countRects(linkIndex)) { "Rect index cannot be negative" }
        synchronized(lock = PdfiumCore.lock) {
            return try {
                nativeGetRect(
                    pageLinkPtr = pageLinkPtr,
                    linkIndex = linkIndex,
                    rectIndex = rectIndex
                ).let { data ->
                    RectF().apply {
                        left = data[0]
                        top = data[1]
                        right = data[2]
                        bottom = data[3]
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get region $rectIndex for link $linkIndex: ${e.message}", e)
                null
            }
        }
    }

    /**
     * Retrieves the text range associated with a specific link.
     *
     * @param linkIndex Index of the target link.
     * @return Pair representing the start and end indices of the linked text.
     */
    fun getWebLinkTextRange(linkIndex: Int): Pair<Int, Int>? {
        require(value = linkIndex in 0 until webLinksCount) { "Invalid web link index" }
        synchronized(lock = PdfiumCore.lock) {
            return try {
                nativeGetTextRange(pageLinkPtr = pageLinkPtr, index = linkIndex).let {
                    if (it.size >= 2) it[0] to it[1] else null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get text range for link $linkIndex: ${e.message}", e)
                null
            }
        }
    }

    /**
     * Close the page and release all resources
     */
    override fun close() {
        synchronized(lock = PdfiumCore.lock) {
            pageMap[pageIndex]?.let {
                if (--it.count > 0) return
                pageMap.remove(key = pageIndex)
            }
            nativeCloseTextPage(pagePtr = pagePtr)
            nativeClosePageLink(pageLinkPtr)
            pageMap.clear()
        }
    }

    companion object {
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

        @JvmStatic
        private external fun nativeClosePageLink(pageLinkPtr: Long)

        @JvmStatic
        private external fun nativeCountRects(pageLinkPtr: Long, index: Int): Int

        @JvmStatic
        private external fun nativeCountWebLinks(pageLinkPtr: Long): Int

        @JvmStatic
        private external fun nativeGetRect(
            pageLinkPtr: Long, linkIndex: Int, rectIndex: Int
        ): FloatArray

        @JvmStatic
        private external fun nativeGetTextRange(pageLinkPtr: Long, index: Int): IntArray

        @JvmStatic
        private external fun nativeGetURL(
            pageLinkPtr: Long, index: Int, count: Int, result: ByteArray
        ): Int
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
) {
    init {
        require(value = rangeStart >= 0) { "Invalid rangeStart: $rangeStart" }
        require(value = rangeLength > 0) { "Invalid rangeLength: $rangeLength" }
    }
}