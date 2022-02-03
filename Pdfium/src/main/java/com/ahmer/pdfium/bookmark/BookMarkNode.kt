package com.ahmer.pdfium.bookmark

import androidx.annotation.NonNull
import com.ahmer.pdfium.treeview.TreeViewNode

class BookMarkNode(@NonNull content: BookMarkEntry?) : TreeViewNode<BookMarkEntry?>(content) {
    fun add(title: String?, pageIdx: Int): BookMarkNode {
        val ret = BookMarkNode(BookMarkEntry(title!!, pageIdx))
        addChild(ret)
        return ret
    }

    fun addToParent(title: String?, pageIdx: Int): BookMarkNode {
        if (parent == null) {
            return add(title, pageIdx)
        }
        val ret = BookMarkNode(BookMarkEntry(title!!, pageIdx))
        parent!!.addChild(ret)
        return ret
    }
}