package com.ahmer.pdfium

import android.graphics.RectF
import android.util.Log
import java.io.Closeable
import java.nio.charset.StandardCharsets

/**
 * Represents a collection of links within a PDF page.
 * Provides thread-safe access to link-related information through native PDFium methods.
 *
 * @property pageLinkPtr Native pointer to the page link resources.
 */
class PdfPageLink(
    private val pageLinkPtr: Long,
) : Closeable {

    /**
     * Retrieves the number of web links present in the PDF page.
     *
     * @return Total count of web links.
     */
    val webLinkCount: Int
        get() = synchronized(lock = PdfiumCore.lock) {
            nativeCountWebLinks(pageLinkPtr = pageLinkPtr)
        }

    /**
     * Retrieves the URL for a web link at the specified index.
     *
     * @param linkIndex Index of the web link to query.
     * @param charCount Expected number of characters in the URL.
     * @return URL string if successful, empty string for invalid links, or null on error.
     */
    fun getURL(linkIndex: Int, charCount: Int): String? {
        synchronized(lock = PdfiumCore.lock) {
            try {
                val buffer = ByteArray(size = charCount * 2)
                val bytesWritten = nativeGetURL(
                    pageLinkPtr = pageLinkPtr,
                    index = linkIndex,
                    count = charCount,
                    result = buffer,
                )
                return when {
                    bytesWritten <= 0 -> ""
                    else -> String(bytes = buffer, charset = StandardCharsets.UTF_16LE)
                }
            } catch (e: NullPointerException) {
                Log.e(TAG, "Context reference missing", e)
                return null
            } catch (e: Exception) {
                Log.e(TAG, "Native operation failed", e)
                return null
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
        synchronized(lock = PdfiumCore.lock) {
            return nativeCountRects(pageLinkPtr = pageLinkPtr, index = linkIndex)
        }
    }

    /**
     * Retrieves the bounding rectangle for a specific link region.
     *
     * @param linkIndex Index of the target link.
     * @param rectIndex Index of the rectangular region within the link.
     * @return [RectF] representing the link's bounding box.
     */
    fun getRect(linkIndex: Int, rectIndex: Int): RectF {
        synchronized(lock = PdfiumCore.lock) {
            val rectValues = nativeGetRect(
                pageLinkPtr = pageLinkPtr,
                linkIndex = linkIndex,
                rectIndex = rectIndex
            )

            return RectF().apply {
                left = rectValues[0]
                top = rectValues[1]
                right = rectValues[2]
                bottom = rectValues[3]
            }
        }
    }

    /**
     * Retrieves the text range associated with a specific link.
     *
     * @param linkIndex Index of the target link.
     * @return Pair representing the start and end indices of the linked text.
     */
    fun getTextRange(linkIndex: Int): Pair<Int, Int> {
        synchronized(lock = PdfiumCore.lock) {
            return nativeGetTextRange(pageLinkPtr = pageLinkPtr, index = linkIndex).let { range ->
                Pair(first = range[0], second = range[1])
            }
        }
    }

    /**
     * Closes the native page link handle.
     */
    override fun close() {
        nativeClosePageLink(pageLinkPtr = pageLinkPtr)
    }

    companion object {
        private val TAG: String? = PdfPageLink::class.java.name

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