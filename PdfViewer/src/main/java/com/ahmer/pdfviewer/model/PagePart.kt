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
        return other.page == page && other.pageRelativeBounds.left == pageRelativeBounds.left && other.pageRelativeBounds.right == pageRelativeBounds.right && other.pageRelativeBounds.top == pageRelativeBounds.top && other.pageRelativeBounds.bottom == pageRelativeBounds.bottom
    }

    override fun hashCode(): Int {
        var result = page
        result = 31 * result + renderedBitmap.hashCode()
        result = 31 * result + pageRelativeBounds.hashCode()
        result = 31 * result + isThumbnail.hashCode()
        result = 31 * result + cacheOrder
        return result
    }
}