package com.ahmer.pdfium

import java.util.ArrayList

class Bookmark {
    val children: List<Bookmark> = ArrayList()
    var title: String? = null
    var pageIdx: Long = 0
    var mNativePtr: Long = 0
    fun hasChildren(): Boolean {
        return children.isNotEmpty()
    }
}
