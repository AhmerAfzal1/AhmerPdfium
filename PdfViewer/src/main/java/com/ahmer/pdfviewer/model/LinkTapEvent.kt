package com.ahmer.pdfviewer.model

import android.graphics.RectF
import com.ahmer.pdfium.Link

class LinkTapEvent(
    val originalX: Float,
    val originalY: Float,
    val documentX: Float,
    val documentY: Float,
    val mappedLinkRect: RectF,
    private val link: Link
) {

    fun getLink(): Link {
        return link
    }
}