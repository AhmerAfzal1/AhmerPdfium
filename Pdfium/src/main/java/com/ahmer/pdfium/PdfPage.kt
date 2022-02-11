package com.ahmer.pdfium

import com.ahmer.pdfium.util.Size

class PdfPage {
    val size: Size? = null
    val horizontalOffset: Int
        get() = 0
    val scrollAxisOffset: Long
        get() = 0
    val pagePtr: Long
        get() = 0
}
