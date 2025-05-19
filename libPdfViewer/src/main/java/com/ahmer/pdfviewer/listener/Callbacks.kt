package com.ahmer.pdfviewer.listener

import android.view.MotionEvent
import com.ahmer.pdfviewer.link.LinkHandler
import com.ahmer.pdfviewer.model.LinkTapEvent

class Callbacks {

    /**
     * Call back object to call when clicking link
     */
    private var linkHandler: LinkHandler? = null

    /**
     * Call back object to call when the PDF is loaded
     */
    private var onLoadCompleteListener: OnLoadCompleteListener? = null

    /**
     * Call back object to call when the user does a long tap gesture
     */
    private var onLongPressListener: OnLongPressListener? = null

    /**
     * Call back object to call when the page has changed
     */
    private var onPageChangeListener: OnPageChangeListener? = null

    /**
     * Call back object to call when the page load error occurs
     */
    private var onPageErrorListener: OnPageErrorListener? = null

    /**
     * Call back object to call when the page is scrolled
     */
    private var onPageScrollListener: OnPageScrollListener? = null

    /**
     * Call back object to call when the document is initially rendered
     */
    private var onRenderListener: OnRenderListener? = null

    /**
     * Call back object to call when the user does a tap gesture
     */
    private var onTapListener: OnTapListener? = null

    /**
     * Call back object to call when the above layer is to drawn
     */
    var onDraw: OnDrawListener? = null
    var onDrawAll: OnDrawListener? = null

    /**
     * Call back object to call when document loading error occurs
     */
    var onError: OnErrorListener? = null

    fun callLinkHandler(event: LinkTapEvent?) = linkHandler?.handleLinkEvent(event)

    fun callOnLoadComplete(totalPages: Int) = onLoadCompleteListener?.loadComplete(totalPages)

    fun callOnLongPress(event: MotionEvent?) = onLongPressListener?.onLongPress(event)

    fun callOnPageChange(page: Int, totalPages: Int) = onPageChangeListener?.onPageChanged(page, totalPages)

    fun callOnPageScroll(currentPage: Int, offset: Float) = onPageScrollListener?.onPageScrolled(currentPage, offset)

    fun callOnRender(totalPages: Int) = onRenderListener?.onInitiallyRendered(totalPages)

    fun callOnTap(event: MotionEvent?): Boolean = onTapListener?.onTap(event) == true

    fun callOnPageError(page: Int, error: Throwable?): Boolean {
        if (onPageErrorListener != null) {
            onPageErrorListener?.onPageError(page, error)
            return true
        }
        return false
    }

    fun setLinkHandler(linkHandler: LinkHandler?) {
        this.linkHandler = linkHandler
    }

    fun setOnLoadComplete(listener: OnLoadCompleteListener?) {
        onLoadCompleteListener = listener
    }

    fun setOnLongPress(listener: OnLongPressListener?) {
        onLongPressListener = listener
    }

    fun setOnPageChange(listener: OnPageChangeListener?) {
        onPageChangeListener = listener
    }

    fun setOnPageError(listener: OnPageErrorListener?) {
        onPageErrorListener = listener
    }

    fun setOnPageScroll(listener: OnPageScrollListener?) {
        onPageScrollListener = listener
    }

    fun setOnRender(listener: OnRenderListener?) {
        onRenderListener = listener
    }

    fun setOnTap(listener: OnTapListener?) {
        onTapListener = listener
    }
}