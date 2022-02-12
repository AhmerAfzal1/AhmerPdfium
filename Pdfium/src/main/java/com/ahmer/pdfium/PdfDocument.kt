package com.ahmer.pdfium

import android.graphics.RectF
import android.os.ParcelFileDescriptor
import android.util.ArrayMap

class PdfDocument {
    val mNativePagesPtr: MutableMap<Int, Long> = ArrayMap()
    val mNativeTextPtr: MutableMap<Int, Long> = ArrayMap()
    var mNativeDocPtr: Long = 0L
    var parcelFileDescriptor: ParcelFileDescriptor? = null

    fun hasPage(index: Int): Boolean {
        return mNativePagesPtr.containsKey(index)
    }

    fun hasText(index: Int): Boolean {
        return mNativeTextPtr.containsKey(index)
    }

    data class Bookmark(
        val children: MutableList<Bookmark> = ArrayList(),
        var mNativePtr: Long = 0L,
        var pageIdx: Long = 0L,
        var title: String? = null
    ) {
        fun hasChildren(): Boolean {
            return children.isNotEmpty()
        }
    }

    data class Link(val bounds: RectF, val destPageIdx: Int, val uri: String)

    data class Meta(
        var title: String? = null,
        var author: String? = null,
        var subject: String? = null,
        var keywords: String? = null,
        var creator: String? = null,
        var producer: String? = null,
        var creationDate: String? = null,
        var modDate: String? = null,
        var totalPages: Int = 0
    )
}