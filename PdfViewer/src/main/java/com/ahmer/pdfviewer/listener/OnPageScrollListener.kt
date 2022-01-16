package com.ahmer.pdfviewer.listener

/**
 * Implements this interface to receive events from PDFView
 * when a page has been scrolled
 */
interface OnPageScrollListener {
    /**
     * Called on every move while scrolling
     *
     * @param page           current page index
     * @param positionOffset see [com.ahmer.pdfviewer.PDFView.getPositionOffset]
     */
    fun onPageScrolled(page: Int, positionOffset: Float)
}