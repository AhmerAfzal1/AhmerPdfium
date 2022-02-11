package com.ahmer.pdfviewer.model

class SearchRecord(val pageIdx: Int, val findStart: Int) {
    var currentPage = -1
    var data: Any? = null
}