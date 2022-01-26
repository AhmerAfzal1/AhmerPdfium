package com.ahmer.pdfviewer.listener

interface OnTextParseListener {
    fun onTextParseSuccess(pageIndex: Int, text: String)
    fun onTextParseError(pageIndex: Int)
}