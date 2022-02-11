package com.ahmer.pdfviewer.model

import android.graphics.Bitmap
import android.graphics.RectF

class PagePart(
    val page: Int,
    val renderedBitmap: Bitmap?,
    val pageRelativeBounds: RectF,
    val isThumbnail: Boolean,
    var cacheOrder: Int
) {
    override fun equals(other: Any?): Boolean {
        if (other !is PagePart) {
            return false
        }
        return other.page == page
                && other.pageRelativeBounds.left == pageRelativeBounds.left
                && other.pageRelativeBounds.right == pageRelativeBounds.right
                && other.pageRelativeBounds.top == pageRelativeBounds.top
                && other.pageRelativeBounds.bottom == pageRelativeBounds.bottom
    }
}