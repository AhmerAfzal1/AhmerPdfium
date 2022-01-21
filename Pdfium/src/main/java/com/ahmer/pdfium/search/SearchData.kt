package com.ahmer.pdfium.search

import android.text.Spannable
import com.ahmer.pdfium.Bookmark

data class SearchData(
    val chapter: Bookmark? = null,
    /**
     * number of page in range from 1 to pagesCount
     */
    val pageNumber: Int,
    val partOfText: Spannable
)
