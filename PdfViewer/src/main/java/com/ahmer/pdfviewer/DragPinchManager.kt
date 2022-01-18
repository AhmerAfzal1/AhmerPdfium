package com.ahmer.pdfviewer

import android.annotation.SuppressLint
import android.graphics.PointF
import android.view.GestureDetector
import android.view.GestureDetector.OnDoubleTapListener
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.ScaleGestureDetector.OnScaleGestureListener
import android.view.View
import android.view.View.OnTouchListener
import com.ahmer.pdfviewer.model.LinkTapEvent
import com.ahmer.pdfviewer.util.PdfConstants.Pinch.MAXIMUM_ZOOM
import com.ahmer.pdfviewer.util.PdfConstants.Pinch.MINIMUM_ZOOM
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * This Manager takes care of moving the PDFView,
 * set its zoom track user actions.
 */
internal class DragPinchManager @SuppressLint("ClickableViewAccessibility") constructor(
    private val pdfView: PDFView,
    private val animationManager: AnimationManager
) : GestureDetector.OnGestureListener, OnDoubleTapListener, OnScaleGestureListener,
    OnTouchListener {
    private val gestureDetector: GestureDetector = GestureDetector(pdfView.context, this)
    private val scaleGestureDetector: ScaleGestureDetector =
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
        gestureDetector.setIsLongpressEnabled(false)
    }

    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
        val onTapHandled = pdfView.callbacks.callOnTap(e)
        val linkTapped = checkLinkTapped(e.x, e.y)
        if (!onTapHandled && !linkTapped) {
            val ps = pdfView.scrollHandle
            if (ps != null && !pdfView.documentFitsView()) {
                if (!ps.shown()) {
                    ps.show()
                } else {
                    ps.hide()
                }
            }
        }
        pdfView.performClick()
        return true
    }

    private fun checkLinkTapped(x: Float, y: Float): Boolean {
        val pdfFile = pdfView.pdfFile ?: return false
        val mappedX = -pdfView.currentXOffset + x
        val mappedY = -pdfView.currentYOffset + y
        val page =
            pdfFile.getPageAtOffset(if (pdfView.isSwipeVertical) mappedY else mappedX, pdfView.zoom)
        val pageSize = pdfFile.getScaledPageSize(page, pdfView.zoom)
        val pageX: Int
        val pageY: Int
        if (pdfView.isSwipeVertical) {
            pageX = pdfFile.getSecondaryPageOffset(page, pdfView.zoom).toInt()
            pageY = pdfFile.getPageOffset(page, pdfView.zoom).toInt()
        } else {
            pageY = pdfFile.getSecondaryPageOffset(page, pdfView.zoom).toInt()
            pageX = pdfFile.getPageOffset(page, pdfView.zoom).toInt()
        }
        for (link in pdfFile.getPageLinks(page)) {
            val mapped = pdfFile.mapRectToDevice(
                page, pageX, pageY, pageSize.width.toInt(),
                pageSize.height.toInt(), link.bounds
            )
            mapped?.sort()
            if (mapped?.contains(mappedX, mappedY) == true) {
                pdfView.callbacks.callLinkHandler(
                    LinkTapEvent(
                        x, y, mappedX, mappedY, mapped,
                        link
                    )
                )
                return true
            }
        }
        return false
    }

    private fun startPageFling(
        downEvent: MotionEvent, ev: MotionEvent,
        velocityX: Float, velocityY: Float
    ) {
        if (!checkDoPageFling(velocityX, velocityY)) {
            return
        }
        val direction: Int = if (pdfView.isSwipeVertical) {
            if (velocityY > 0) -1 else 1
        } else {
            if (velocityX > 0) -1 else 1
        }
        // Get the focused page during the down event to ensure only a single page is changed
        val delta = if (pdfView.isSwipeVertical) ev.y - downEvent.y else ev.x - downEvent.x
        val offsetX = pdfView.currentXOffset - delta * pdfView.zoom
        val offsetY = pdfView.currentYOffset - delta * pdfView.zoom
        val startingPage = pdfView.findFocusPage(offsetX, offsetY)
        val targetPage = max(0, min(pdfView.getPageCount() - 1, startingPage + direction))
        val edge = pdfView.findSnapEdge(targetPage)
        val offset = pdfView.snapOffsetForPage(targetPage, edge)
        animationManager.startPageFlingAnimation(-offset)
    }

    override fun onDoubleTap(e: MotionEvent): Boolean {
        if (!pdfView.isDoubletapEnabled) {
            return false
        }
        when {
            pdfView.zoom < pdfView.midZoom -> {
                pdfView.zoomWithAnimation(e.x, e.y, pdfView.midZoom)
            }
            pdfView.zoom < pdfView.maxZoom -> {
                pdfView.zoomWithAnimation(e.x, e.y, pdfView.maxZoom)
            }
            else -> {
                pdfView.resetZoomWithAnimation()
            }
        }
        return true
    }

    override fun onDoubleTapEvent(e: MotionEvent): Boolean {
        return false
    }

    override fun onDown(e: MotionEvent): Boolean {
        animationManager.stopFling()
        return true
    }

    override fun onShowPress(e: MotionEvent) {}
    override fun onSingleTapUp(e: MotionEvent): Boolean {
        return false
    }

    override fun onScroll(e1: MotionEvent, e2: MotionEvent, distanceX: Float, distanceY: Float):
            Boolean {
        scrolling = true
        if (pdfView.isZooming() || pdfView.isSwipeEnabled()) {
            pdfView.moveRelativeTo(-distanceX, -distanceY)
        }
        if (!scaling || pdfView.doRenderDuringScale()) {
            pdfView.loadPageByOffset()
        }
        return true
    }

    private fun onScrollEnd(event: MotionEvent) {
        pdfView.loadPages()
        hideHandle()
        if (!animationManager.isFlinging()) {
            pdfView.performPageSnap()
        }
    }

    override fun onLongPress(e: MotionEvent) {
        pdfView.callbacks.callOnLongPress(e)
    }

    override fun onFling(e1: MotionEvent, e2: MotionEvent, velocityX: Float, velocityY: Float):
            Boolean {
        if (!pdfView.isSwipeEnabled()) {
            return false
        }
        if (pdfView.isPageFlingEnabled()) {
            if (pdfView.pageFillsScreen()) {
                onBoundedFling(velocityX, velocityY)
            } else {
                startPageFling(e1, e2, velocityX, velocityY)
            }
            return true
        }
        val xOffset = pdfView.currentXOffset.toInt()
        val yOffset = pdfView.currentYOffset.toInt()
        val minX: Float
        val minY: Float
        val pdfFile = pdfView.pdfFile
        if (pdfView.isSwipeVertical) {
            minX = -(pdfView.toCurrentScale(pdfFile?.maxPageWidth ?: 0f) - pdfView.width)
            minY = -((pdfFile?.getDocLen(pdfView.zoom) ?: 0f) - pdfView.height)
        } else {
            minX = -((pdfFile?.getDocLen(pdfView.zoom) ?: 0f) - pdfView.width)
            minY = -(pdfView.toCurrentScale(pdfFile?.maxPageHeight ?: 0f) - pdfView.height)
        }
        animationManager.startFlingAnimation(
            xOffset, yOffset, velocityX.toInt(), velocityY.toInt(), minX.toInt(), 0, minY.toInt(), 0
        )
        return true
    }

    private fun onBoundedFling(velocityX: Float, velocityY: Float) {
        val xOffset = pdfView.currentXOffset.toInt()
        val yOffset = pdfView.currentYOffset.toInt()
        val pdfFile = pdfView.pdfFile
        val pageStart = -(pdfFile?.getPageOffset(pdfView.currentPage, pdfView.zoom) ?: 0f)
        val pageEnd = pageStart - (pdfFile?.getPageLength(pdfView.currentPage, pdfView.zoom) ?: 0f)
        val minX: Float
        val minY: Float
        val maxX: Float
        val maxY: Float
        if (pdfView.isSwipeVertical) {
            minX = -(pdfView.toCurrentScale(pdfFile?.maxPageWidth ?: 0f) - pdfView.width)
            minY = pageEnd + pdfView.height
            maxX = 0f
            maxY = pageStart
        } else {
            minX = pageEnd + pdfView.width
            minY = -(pdfView.toCurrentScale(pdfFile?.maxPageHeight ?: 0f) - pdfView.height)
            maxX = pageStart
            maxY = 0f
        }
        animationManager.startFlingAnimation(
            xOffset, yOffset, velocityX.toInt(), velocityY.toInt(),
            minX.toInt(), maxX.toInt(), minY.toInt(), maxY.toInt()
        )
    }

    override fun onScale(detector: ScaleGestureDetector): Boolean {
        var dr = detector.scaleFactor
        val wantedZoom = pdfView.zoom * dr
        val minZoom = min(MINIMUM_ZOOM, pdfView.minZoom)
        val maxZoom = min(MAXIMUM_ZOOM, pdfView.maxZoom)
        if (wantedZoom < minZoom) {
            dr = minZoom / pdfView.zoom
        } else if (wantedZoom > maxZoom) {
            dr = maxZoom / pdfView.zoom
        }
        pdfView.zoomCenteredRelativeTo(dr, PointF(detector.focusX, detector.focusY))
        return true
    }

    override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
        scaling = true
        return true
    }

    override fun onScaleEnd(detector: ScaleGestureDetector) {
        pdfView.loadPages()
        hideHandle()
        scaling = false
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(v: View, event: MotionEvent): Boolean {
        if (!enabled) {
            return false
        }
        var retVal = scaleGestureDetector.onTouchEvent(event)
        retVal = gestureDetector.onTouchEvent(event) || retVal
        if (event.action == MotionEvent.ACTION_UP) {
            if (scrolling) {
                scrolling = false
                onScrollEnd(event)
            }
        }
        return retVal
    }

    private fun hideHandle() {
        val scrollHandle = pdfView.scrollHandle
        if (scrollHandle != null && scrollHandle.shown()) {
            scrollHandle.hideDelayed()
        }
    }

    private fun checkDoPageFling(velocityX: Float, velocityY: Float): Boolean {
        val absX = abs(velocityX)
        val absY = abs(velocityY)
        return if (pdfView.isSwipeVertical) absY > absX else absX > absY
    }

    init {
        pdfView.setOnTouchListener(this)
    }
}