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

    fun callLinkHandler(event: LinkTapEvent) {
        if (linkHandler != null) {
            linkHandler?.handleLinkEvent(event)
        }
    }

    fun callOnLoadComplete(pagesCount: Int) {
        if (onLoadCompleteListener != null) {
            onLoadCompleteListener?.loadComplete(pagesCount)
        }
    }

    fun callOnLongPress(event: MotionEvent?) {
        if (onLongPressListener != null) {
            onLongPressListener?.onLongPress(event)
        }
    }

    fun callOnPageChange(page: Int, pagesCount: Int) {
        if (onPageChangeListener != null) {
            onPageChangeListener?.onPageChanged(page, pagesCount)
        }
    }

    fun callOnPageError(page: Int, error: Throwable?): Boolean {
        if (onPageErrorListener != null) {
            onPageErrorListener?.onPageError(page, error)
            return true
        }
        return false
    }

    fun callOnPageScroll(currentPage: Int, offset: Float) {
        if (onPageScrollListener != null) {
            onPageScrollListener?.onPageScrolled(currentPage, offset)
        }
    }

    fun callOnRender(pagesCount: Int) {
        if (onRenderListener != null) {
            onRenderListener?.onInitiallyRendered(pagesCount)
        }
    }

    fun callOnTap(event: MotionEvent?): Boolean {
        return onTapListener?.onTap(event) == true
    }

    fun setLinkHandler(linkHandler: LinkHandler?) {
        this.linkHandler = linkHandler
    }

    fun setOnLoadComplete(onLoadCompleteListener: OnLoadCompleteListener?) {
        this.onLoadCompleteListener = onLoadCompleteListener
    }

    fun setOnLongPress(onLongPressListener: OnLongPressListener?) {
        this.onLongPressListener = onLongPressListener
    }

    fun setOnPageChange(onPageChangeListener: OnPageChangeListener?) {
        this.onPageChangeListener = onPageChangeListener
    }

    fun setOnPageError(onPageErrorListener: OnPageErrorListener?) {
        this.onPageErrorListener = onPageErrorListener
    }

    fun setOnPageScroll(onPageScrollListener: OnPageScrollListener?) {
        this.onPageScrollListener = onPageScrollListener
    }

    fun setOnRender(onRenderListener: OnRenderListener?) {
        this.onRenderListener = onRenderListener
    }

    fun setOnTap(onTapListener: OnTapListener?) {
        this.onTapListener = onTapListener
    }
}