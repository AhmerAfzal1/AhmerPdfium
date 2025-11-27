package com.ahmer.pdfium

import android.graphics.RectF
import java.io.Closeable

/**
 * Data class representing a search match with its position and bounding rectangles.
 *
 * @property startIndex The starting character index of the match in the page text
 * @property length The number of characters in the match
 * @property rects List of bounding rectangles for the matched text (may span multiple lines)
 */
data class SearchMatch(
    val startIndex: Int,
    val length: Int,
    val rects: List<RectF>
)

class FindResult(
    val handle: FindHandle,
) : Closeable {
    private external fun nativeCloseFind(findHandle: Long)
    private external fun nativeFindNext(findHandle: Long): Boolean
    private external fun nativeFindPrev(findHandle: Long): Boolean
    private external fun nativeGetSchCount(findHandle: Long): Int
    private external fun nativeGetSchResultIndex(findHandle: Long): Int

    /**
     * Finds the next occurrence of the search text in the PDF document.
     *
     * @return `true` if another match was found, `false` if no more matches exist
     * @throws IllegalStateException if the search handle is invalid or search wasn't started
     * @see nativeFindNext
     */
    fun findNext(): Boolean {
        synchronized(lock = PdfiumCore.lock) {
            return nativeFindNext(findHandle = handle)
        }
    }

    /**
     * Finds the previous occurrence of the search text in the PDF document.
     *
     * @return `true` if a previous match was found, `false` if at beginning of document
     * @throws IllegalStateException if the search handle is invalid or search wasn't started
     * @see nativeFindPrev
     */
    fun findPrev(): Boolean {
        synchronized(lock = PdfiumCore.lock) {
            return nativeFindPrev(findHandle = handle)
        }
    }

    /**
     * Gets the starting character index of the current search match.
     *
     * @return Zero-based character index of the match start position
     * @throws IllegalStateException if no active match exists or handle is invalid
     * @see nativeGetSchResultIndex
     */
    fun getSchResultIndex(): Int {
        synchronized(lock = PdfiumCore.lock) {
            return nativeGetSchResultIndex(findHandle = handle)
        }
    }

    /**
     * Gets the length of the current matched text in characters.
     *
     * @return Number of characters in the current match (0 if no match)
     * @throws IllegalStateException if search handle is invalid
     * @see nativeGetSchCount
     */
    fun getSchCount(): Int {
        synchronized(lock = PdfiumCore.lock) {
            return nativeGetSchCount(findHandle = handle)
        }
    }

    /**
     * Releases all resources associated with this search operation.
     *
     * @note The handle becomes invalid after this call
     * @see nativeCloseFind
     */

    fun closeFind() {
        synchronized(lock = PdfiumCore.lock) {
            nativeCloseFind(findHandle = handle)
        }
    }

    /**
     * Gets the bounding rectangles for the current search match.
     * Useful for highlighting the matched text.
     *
     * @param textPage The text page to get rectangles from
     * @return List of RectF representing the bounding boxes of the match, or null on error
     */
    fun getSearchResultRects(textPage: PdfTextPage): List<RectF>? {
        synchronized(lock = PdfiumCore.lock) {
            val startIndex = getSchResultIndex()
            val count = getSchCount()
            if (startIndex < 0 || count <= 0) {
                return null
            }

            val rectCount = textPage.countTextRects(startIndex, count)
            if (rectCount <= 0) {
                return null
            }

            return (0 until rectCount).mapNotNull { textPage.getTextRect(it) }
        }
    }

    /**
     * Finds all matches of the search query on the page and returns their rectangles.
     *
     * Note: This method iterates through all matches from the current position to the end.
     * The search position is not reset after this call.
     *
     * @param textPage The text page to search
     * @return List of SearchMatch objects, or empty list if no matches
     */
    fun findAllMatches(textPage: PdfTextPage): List<SearchMatch> {
        synchronized(lock = PdfiumCore.lock) {
            val matches = mutableListOf<SearchMatch>()

            while (findNext()) {
                val startIndex = getSchResultIndex()
                val count = getSchCount()
                if (startIndex >= 0 && count > 0) {
                    val rectCount = textPage.countTextRects(startIndex, count)
                    if (rectCount > 0) {
                        val rects = (0 until rectCount).mapNotNull { textPage.getTextRect(it) }
                        if (rects.isNotEmpty()) {
                            matches.add(SearchMatch(startIndex, count, rects))
                        }
                    }
                }
            }

            return matches
        }
    }

    override fun close() {
        nativeCloseFind(findHandle = handle)
    }
}