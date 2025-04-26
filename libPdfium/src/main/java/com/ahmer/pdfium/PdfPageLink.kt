package com.ahmer.pdfium

import android.graphics.RectF
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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
    suspend fun countWebLinks(): Int = withContext(context = Dispatchers.IO) {
        mutexLock.withLock {
            nativeCountWebLinks(pageLinkPtr = pageLinkPtr)
        }
    }

    /**
     * Retrieves the URL for a web link at the specified index.
     *
     * @param linkIndex Index of the web link to query.
     * @param charCount Expected number of characters in the URL.
     * @return URL string if successful, empty string for invalid links, or null on error.
     */
    suspend fun getURL(linkIndex: Int, charCount: Int): String? =
        withContext(context = Dispatchers.IO) {
            mutexLock.withLock {
                try {
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
                } catch (e: NullPointerException) {
                    Log.e(TAG, "Context reference missing", e)
                    null
                } catch (e: Exception) {
                    Log.e(TAG, "Native operation failed", e)
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
    suspend fun countRects(linkIndex: Int): Int = withContext(context = Dispatchers.IO) {
        mutexLock.withLock {
            nativeCountRects(pageLinkPtr = pageLinkPtr, index = linkIndex)
        }
    }

    /**
     * Retrieves the bounding rectangle for a specific link region.
     *
     * @param linkIndex Index of the target link.
     * @param rectIndex Index of the rectangular region within the link.
     * @return [RectF] representing the link's bounding box.
     */
    suspend fun getRect(linkIndex: Int, rectIndex: Int): RectF =
        withContext(context = Dispatchers.IO) {
            mutexLock.withLock {
                val rectValues = nativeGetRect(
                    pageLinkPtr = pageLinkPtr,
                    linkIndex = linkIndex,
                    rectIndex = rectIndex
                )

                RectF().apply {
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
    suspend fun getTextRange(linkIndex: Int): Pair<Int, Int> =
        withContext(context = Dispatchers.IO) {
            mutexLock.withLock {
                nativeGetTextRange(pageLinkPtr = pageLinkPtr, index = linkIndex).let { rangeArray ->
                    Pair(first = rangeArray[0], second = rangeArray[1])
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
        private val mutexLock: Mutex = Mutex()
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