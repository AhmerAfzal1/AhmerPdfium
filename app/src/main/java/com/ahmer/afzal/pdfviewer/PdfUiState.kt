package com.ahmer.afzal.pdfviewer

data class PdfUiState(
    val isAutoSpacing: Boolean = true,
    val isNightMode: Boolean = false,
    val isPageSnap: Boolean = true,
    val isViewHorizontal: Boolean = false,
    val spacing: Int = 5
)
