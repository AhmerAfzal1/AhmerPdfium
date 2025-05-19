package com.ahmer.pdfviewer.model

import android.graphics.Bitmap
import android.graphics.RectF
import java.util.Objects

class PagePart(
    val page: Int,
    val renderedBitmap: Bitmap?,
    val pageBounds: RectF,
    val isThumbnail: Boolean,
    var cacheOrder: Int
) {
    override fun equals(other: Any?): Boolean {
        if (other !is PagePart) {
            return false
        }
        return (other.page == page
                && other.pageBounds.left == pageBounds.left
                && other.pageBounds.right == pageBounds.right
                && other.pageBounds.top == pageBounds.top
                && other.pageBounds.bottom == pageBounds.bottom)
    }

    override fun hashCode(): Int {
        return Objects.hash(page, pageBounds.left, pageBounds.right, pageBounds.top, pageBounds.bottom)
    }
}