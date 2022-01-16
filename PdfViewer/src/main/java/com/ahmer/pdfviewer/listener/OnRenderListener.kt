package com.ahmer.pdfviewer.listener

interface OnRenderListener {
    /**
     * Called only once, when document is rendered
     *
     * @param nbPages number of pages
     */
    fun onInitiallyRendered(nbPages: Int)
}