package com.ahmer.pdfviewer.listener

interface OnRenderListener {
    /**
     * Called only once, when document is rendered
     * @param totalPages number of pages
     */
    fun onInitiallyRendered(totalPages: Int)
}