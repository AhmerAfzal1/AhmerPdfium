package com.ahmer.pdfium

import android.graphics.RectF
import android.os.ParcelFileDescriptor
import android.util.ArrayMap

class PdfDocument(val nativeDocPtr: Long) {
    val nativePagesPtr: MutableMap<Int, Long> = ArrayMap()
    var parcelFileDescriptor: ParcelFileDescriptor? = null

    fun hasPage(index: Int): Boolean {
        return nativePagesPtr.containsKey(index)
    }

    data class Bookmark(
        val children: MutableList<Bookmark> = ArrayList(),
        var nativePtr: Long = 0L,
        var pageIdx: Long = 0L,
        var title: String? = null
    ) {
        fun hasChildren(): Boolean {
            return children.isNotEmpty()
        }
    }

    data class Link(val bounds: RectF?, val destPageIndex: Int?, val uri: String?)

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