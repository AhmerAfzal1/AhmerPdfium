package com.ahmer.pdfviewer.exception

class PageRenderingException(val page: Int, cause: Throwable?) : Exception(cause)