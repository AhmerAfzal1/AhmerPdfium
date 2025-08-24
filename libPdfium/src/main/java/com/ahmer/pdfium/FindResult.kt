package com.ahmer.pdfium

import java.io.Closeable

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

    override fun close() {
        nativeCloseFind(findHandle = handle)
    }
}