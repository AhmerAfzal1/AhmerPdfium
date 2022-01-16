package com.ahmer.pdfviewer

import android.graphics.PointF
import android.graphics.RectF
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.ScaleGestureDetector.OnScaleGestureListener
import android.view.View
import android.view.View.OnTouchListener
import com.ahmer.pdfium.util.SizeF
import com.ahmer.pdfviewer.model.LinkTapEvent
import com.ahmer.pdfviewer.scroll.ScrollHandle
import com.ahmer.pdfviewer.util.PdfConstants.Pinch.MAXIMUM_ZOOM
import com.ahmer.pdfviewer.util.PdfConstants.Pinch.MINIMUM_ZOOM
import com.ahmer.pdfviewer.util.SnapEdge
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * This Manager takes care of moving the PDFView,
 * set its zoom track user actions.
 */
internal class DragPinchManager(pdfView: PDFView, animationManager: AnimationManager) :
    GestureDetector.OnGestureListener, GestureDetector.OnDoubleTapListener, OnScaleGestureListener,
    OnTouchListener {
    private val mPdfView: PDFView = pdfView
    private val mAnimationManager: AnimationManager = animationManager
    private val mGestureDetector: GestureDetector = GestureDetector(pdfView.context, this)
    private val mScaleGestureDetector: ScaleGestureDetector =
        ScaleGestureDetector(pdfView.context, this)
    private var scrolling = false
    private var scaling = false
    private var enabled = false

    fun enable() {
        enabled = true
    }

    fun disable() {
        enabled = false
    }

    fun disableLongPress() {
        mGestureDetector.setIsLongpressEnabled(false)
    }

    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
        val onTapHandled: Boolean = mPdfView.callbacks.callOnTap(e)
        val linkTapped = checkLinkTapped(e.x, e.y)
        if (!onTapHandled && !linkTapped) {
            val ps: ScrollHandle? = mPdfView.scrollHandle
            if (!mPdfView.documentFitsView()) {
                if (ps != null) {
                    if (!ps.shown()) {
                        ps.show()
                    } else {
                        ps.hide()
                    }
                }
            }
        }
        mPdfView.performClick()
        return true
    }

    private fun checkLinkTapped(x: Float, y: Float): Boolean {
        val pdfFile: PdfFile = mPdfView.pdfFile ?: return false
        val mappedX: Float = -mPdfView.currentXOffset + x
        val mappedY: Float = -mPdfView.currentYOffset + y
        val page: Int = pdfFile.getPageAtOffset(
            if (mPdfView.isSwipeVertical) mappedY else mappedX,
            mPdfView.zoom
        )
        val pageSize: SizeF = pdfFile.getScaledPageSize(page, mPdfView.zoom)
        val pageX: Int
        val pageY: Int
        if (mPdfView.isSwipeVertical) {
            pageX = pdfFile.getSecondaryPageOffset(page, mPdfView.zoom).toInt()
            pageY = pdfFile.getPageOffset(page, mPdfView.zoom).toInt()
        } else {
            pageY = pdfFile.getSecondaryPageOffset(page, mPdfView.zoom).toInt()
            pageX = pdfFile.getPageOffset(page, mPdfView.zoom).toInt()
        }
        for (link in pdfFile.getPageLinks(page)) {
            val mapped: RectF = pdfFile.mapRectToDevice(
                page, pageX, pageY, pageSize.width.toInt(),
                pageSize.height.toInt(), link.bounds
            )
            mapped.sort()
            if (mapped.contains(mappedX, mappedY)) {
                mPdfView.callbacks.callLinkHandler(
                    LinkTapEvent(x, y, mappedX, mappedY, mapped, link)
                )
                return true
            }
        }
        return false
    }

    private fun startPageFling(
        downEvent: MotionEvent,
        ev: MotionEvent,
        velocityX: Float,
        velocityY: Float
    ) {
        if (!checkDoPageFling(velocityX, velocityY)) {
            return
        }
        val direction: Int = if (mPdfView.isSwipeVertical) {
            if (velocityY > 0) -1 else 1
        } else {
            if (velocityX > 0) -1 else 1
        }
        // get the focused page during the down event to ensure only a single page is changed
        val delta = if (mPdfView.isSwipeVertical) ev.y - downEvent.y else ev.x - downEvent.x
        val offsetX: Float = mPdfView.currentXOffset - delta * mPdfView.zoom
        val offsetY: Float = mPdfView.currentYOffset - delta * mPdfView.zoom
        val startingPage: Int = mPdfView.findFocusPage(offsetX, offsetY)
        val targetPage =
            0.coerceAtLeast((mPdfView.getPageCount() - 1).coerceAtMost(startingPage + direction))
        val edge: SnapEdge = mPdfView.findSnapEdge(targetPage)
        val offset: Float = mPdfView.snapOffsetForPage(targetPage, edge)
        mAnimationManager.startPageFlingAnimation(-offset)
    }

    override fun onDoubleTap(e: MotionEvent): Boolean {
        if (!mPdfView.isDoubleTapEnabled) {
            return false
        }
        when {
            mPdfView.zoom < mPdfView.midZoom -> {
                mPdfView.zoomWithAnimation(e.x, e.y, mPdfView.midZoom)
            }
            mPdfView.zoom < mPdfView.maxZoom -> {
                mPdfView.zoomWithAnimation(e.x, e.y, mPdfView.maxZoom)
            }
            else -> {
                mPdfView.resetZoomWithAnimation()
            }
        }
        return true
    }

    override fun onDoubleTapEvent(e: MotionEvent): Boolean {
        return false
    }

    override fun onDown(e: MotionEvent): Boolean {
        mAnimationManager.stopFling()
        return true
    }

    override fun onShowPress(e: MotionEvent) {}
    override fun onSingleTapUp(e: MotionEvent): Boolean {
        return false
    }

    override fun onScroll(
        e1: MotionEvent,
        e2: MotionEvent,
        distanceX: Float,
        distanceY: Float
    ): Boolean {
        scrolling = true
        if (mPdfView.isZooming() || mPdfView.isSwipeEnabled()) {
            mPdfView.moveRelativeTo(-distanceX, -distanceY)
        }
        if (!scaling || mPdfView.doRenderDuringScale()) {
            mPdfView.loadPageByOffset()
        }
        return true
    }

    private fun onScrollEnd(event: MotionEvent) {
        mPdfView.loadPages()
        hideHandle()
        if (!mAnimationManager.isFlinging()) {
            mPdfView.performPageSnap()
        }
    }

    override fun onLongPress(e: MotionEvent) {
        mPdfView.callbacks.callOnLongPress(e)
    }

    override fun onFling(
        e1: MotionEvent,
        e2: MotionEvent,
        velocityX: Float,
        velocityY: Float
    ): Boolean {
        if (!mPdfView.isSwipeEnabled()) {
            return false
        }
        if (mPdfView.isPageFlingEnabled()) {
            if (mPdfView.pageFillsScreen()) {
                onBoundedFling(velocityX, velocityY)
            } else {
                startPageFling(e1, e2, velocityX, velocityY)
            }
            return true
        }
        val xOffset = mPdfView.currentXOffset.toInt()
        val yOffset = mPdfView.currentYOffset.toInt()
        val minX: Float
        val minY: Float
        val pdfFile: PdfFile? = mPdfView.pdfFile
        if (mPdfView.isSwipeVertical) {
            minX = -(mPdfView.toCurrentScale(pdfFile!!.maxPageWidth!!) - mPdfView.width)
            minY = -(pdfFile.getDocLen(mPdfView.zoom) - mPdfView.height)
        } else {
            minX = -(pdfFile!!.getDocLen(mPdfView.zoom) - mPdfView.width)
            minY = -(mPdfView.toCurrentScale(pdfFile.maxPageHeight!!) - mPdfView.height)
        }
        mAnimationManager.startFlingAnimation(
            xOffset, yOffset, velocityX.toInt(),
            velocityY.toInt(),
            minX.toInt(), 0,
            (minY.toInt() - mPdfView.toCurrentScale(mPdfView.defaultOffset).roundToInt()),
            mPdfView.toCurrentScale(mPdfView.defaultOffset).roundToInt()
        )
        return true
    }

    private fun onBoundedFling(velocityX: Float, velocityY: Float) {
        val xOffset = mPdfView.currentXOffset.toInt()
        val yOffset = mPdfView.currentYOffset.toInt()
        val pdfFile: PdfFile? = mPdfView.pdfFile
        val pageStart: Float = -pdfFile!!.getPageOffset(mPdfView.currentPage, mPdfView.zoom)
        val pageEnd: Float =
            pageStart - pdfFile.getPageLength(mPdfView.currentPage, mPdfView.zoom)
        val minX: Float
        val minY: Float
        val maxX: Float
        val maxY: Float
        if (mPdfView.isSwipeVertical) {
            minX = -(mPdfView.toCurrentScale(pdfFile.maxPageWidth!!) - mPdfView.width)
            minY = pageEnd + mPdfView.height
            maxX = 0f
            maxY = pageStart
        } else {
            minX = pageEnd + mPdfView.width
            minY = -(mPdfView.toCurrentScale(pdfFile.maxPageHeight!!) - mPdfView.height)
            maxX = pageStart
            maxY = 0f
        }
        mAnimationManager.startFlingAnimation(
            xOffset, yOffset, velocityX.toInt(),
            velocityY.toInt(),
            minX.toInt(), maxX.toInt(), minY.toInt(), maxY.toInt()
        )
    }

    override fun onScale(detector: ScaleGestureDetector): Boolean {
        var dr = detector.scaleFactor
        val wantedZoom: Float = mPdfView.zoom * dr
        val minZoom: Float = MINIMUM_ZOOM.coerceAtMost(mPdfView.minZoom)
        val maxZoom: Float = MAXIMUM_ZOOM.coerceAtMost(mPdfView.maxZoom)
        if (wantedZoom < minZoom) {
            dr = minZoom / mPdfView.zoom
        } else if (wantedZoom > maxZoom) {
            dr = maxZoom / mPdfView.zoom
        }
        mPdfView.zoomCenteredRelativeTo(dr, PointF(detector.focusX, detector.focusY))
        return true
    }

    override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
        scaling = true
        return true
    }

    override fun onScaleEnd(detector: ScaleGestureDetector) {
        mPdfView.loadPages()
        hideHandle()
        scaling = false
    }

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        if (!enabled) {
            return false
        }
        var retVal = mScaleGestureDetector.onTouchEvent(event)
        retVal = mGestureDetector.onTouchEvent(event) || retVal
        if (event.action == MotionEvent.ACTION_UP) {
            if (scrolling) {
                scrolling = false
                onScrollEnd(event)
            }
        }
        return retVal
    }

    private fun hideHandle() {
        val scrollHandle: ScrollHandle? = mPdfView.scrollHandle
        if (scrollHandle != null) {
            if (scrollHandle.shown()) {
                scrollHandle.hideDelayed()
            }
        }
    }

    private fun checkDoPageFling(velocityX: Float, velocityY: Float): Boolean {
        val absX = abs(velocityX)
        val absY = abs(velocityY)
        return if (mPdfView.isSwipeVertical) absY > absX else absX > absY
    }

    init {
        pdfView.setOnTouchListener(this)
    }
}
