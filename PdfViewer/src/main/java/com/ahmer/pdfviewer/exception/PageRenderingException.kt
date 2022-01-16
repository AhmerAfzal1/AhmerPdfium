package com.ahmer.pdfviewer.exception

import java.lang.Exception

class PageRenderingException(private val page: Int, cause: Throwable?) : Exception(cause){

    @JvmName("getPage")
    fun getPage(): Int {
        return page
    }
}