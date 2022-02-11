package com.ahmer.pdfviewer.model

import android.graphics.RectF
import com.ahmer.pdfium.PdfDocument

class LinkTapEvent(
    val originalX: Float,
    val originalY: Float,
    val documentX: Float,
    val documentY: Float,
    val mappedLinkRect: RectF,
    val link: PdfDocument.Link
)