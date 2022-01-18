package com.ahmer.pdfium.search

import android.graphics.Rect

interface SearchHandler {
    val startIndex: Int
    val stopIndex: Int
    val results: Array<Rect?>?
}