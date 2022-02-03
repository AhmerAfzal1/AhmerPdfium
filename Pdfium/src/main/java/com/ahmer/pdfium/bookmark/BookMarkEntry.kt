package com.ahmer.pdfium.bookmark

import com.ahmer.pdfium.R
import com.ahmer.pdfium.treeview.TreeViewAdapter.LayoutItemType

class BookMarkEntry(var entryName: String, var page: Int) : LayoutItemType {

    override val layoutId: Int
        get() = R.layout.bookmark_item

    override fun toString(): String {
        return "BookMarkEntry{" +
                "page=" + page +
                ", entryName='" + entryName + '\'' +
                '}'
    }
}