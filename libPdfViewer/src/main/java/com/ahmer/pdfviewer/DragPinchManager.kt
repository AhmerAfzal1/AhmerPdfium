package com.ahmer.pdfviewer

import android.graphics.PointF
import android.graphics.RectF
import android.util.Log
import android.view.GestureDetector
import android.view.GestureDetector.OnDoubleTapListener
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.ScaleGestureDetector.OnScaleGestureListener
import android.view.View
import android.view.View.OnTouchListener
import com.ahmer.pdfium.util.SizeF
import com.ahmer.pdfviewer.model.LinkTapEvent
import com.ahmer.pdfviewer.util.PdfConstants
import com.ahmer.pdfviewer.util.PdfConstants.Pinch.MAXIMUM_ZOOM
import com.ahmer.pdfviewer.util.PdfConstants.Pinch.MINIMUM_ZOOM
import com.ahmer.pdfviewer.util.SnapEdge
import kotlin.math.abs

/**
 * This Manager takes care of moving the PDFView, set its zoom track user actions.
 */
internal class DragPinchManager(
    private val pdfView: PDFView,
    private val animationManager: AnimationManager
) : GestureDetector.OnGestureListener, OnDoubleTapListener, OnScaleGestureListener, OnTouchListener {
    private val gestureDetector: GestureDetector = GestureDetector(pdfView.context, this)
    private val scaleGestureDetector: ScaleGestureDetector = ScaleGestureDetector(pdfView.context, this)
    private var isEnabled: Boolean = false
    private var isScaling: Boolean = false
    private var isScrolling: Boolean = false

    fun enable() {
        isEnabled = true
    }

    fun disable() {
        isEnabled = false
    }

    @Suppress("UsePropertyAccessSyntax")
    fun disableLongPress() {
        gestureDetector.setIsLongpressEnabled(false)
    }

    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
        val linkTapped: Boolean = checkLinkTapped(x = e.x, y = e.y)
        val onTapHandled: Boolean = pdfView.callbacks.callOnTap(event = e)

        if (!onTapHandled && !linkTapped) {
            pdfView.scrollHandle?.takeIf { !pdfView.documentFitsView() }?.let { handle ->
                if (handle.shown()) handle.hide() else handle.show()
            }
        }
        pdfView.performClick()
        return true
    }

    private fun checkLinkTapped(x: Float, y: Float): Boolean {
        val pdfFile: PdfFile = pdfView.pdfFile ?: return false
        val mappedX: Float = -pdfView.currentXOffset + x
        val mappedY: Float = -pdfView.currentYOffset + y
        val offset: Float = if (pdfView.isSwipeVertical) mappedY else mappedX
        val zoom: Float = pdfView.zoom
        val page: Int = pdfFile.getPageAtOffset(offset = offset, zoom = zoom)
        val pageSize: SizeF = pdfFile.getScaledPageSize(pageIndex = page, zoom = zoom)

        val (pageX, pageY) = if (pdfView.isSwipeVertical) {
            pdfFile.getSecondaryPageOffset(pageIndex = page, zoom = zoom).toInt() to
                    pdfFile.getPageOffset(pageIndex = page, zoom = zoom).toInt()
        } else {
            pdfFile.getPageOffset(pageIndex = page, zoom = zoom).toInt() to
                    pdfFile.getSecondaryPageOffset(pageIndex = page, zoom = zoom).toInt()
        }

        val linkPosX: Float = abs(x = mappedX - pageX)
        val linkPosY: Float = abs(x = mappedY - pageY)

        pdfFile.getPageLinks(pageIndex = page, size = pageSize, posX = linkPosX, posY = linkPosY)
            .firstOrNull { link ->
                Log.v(PdfConstants.TAG, "Link Bound: ${link.bounds}")
                Log.v(PdfConstants.TAG, "Link Uri: ${link.uri}")
                Log.v(PdfConstants.TAG, "Link Page: ${link.destPage}")
                val mappedRect: RectF = pdfFile.mapRectToDevice(
                    pageIndex = page,
                    startX = pageX,
                    startY = pageY,
                    sizeX = pageSize.width.toInt(),
                    sizeY = pageSize.height.toInt(),
                    rect = link.bounds
                ).apply { sort() }
                mappedRect.contains(mappedX, mappedY)
            }?.let { link ->
                val linkTapEvent = LinkTapEvent(
                    originalX = x,
                    originalY = y,
                    mappedX = mappedX,
                    mappedY = mappedY,
                    mappedLinkRect = pdfFile.mapRectToDevice(
                        pageIndex = page,
                        startX = pageX,
                        startY = pageY,
                        sizeX = pageSize.width.toInt(),
                        sizeY = pageSize.height.toInt(),
                        rect = link.bounds
                    ).apply { sort() },
                    link = link
                )
                pdfView.callbacks.callLinkHandler(event = linkTapEvent)
                return true
            }
        return false
    }

    private fun startPageFling(downEvent: MotionEvent, ev: MotionEvent, velocityX: Float, velocityY: Float) {
        if (!checkDoPageFling(velocityX = velocityX, velocityY = velocityY)) return

        val delta: Float = if (pdfView.isSwipeVertical) ev.y - downEvent.y else ev.x - downEvent.x
        val direction: Int = if (pdfView.isSwipeVertical) {
            if (velocityY > 0) -1 else 1
        } else {
            if (velocityX > 0) -1 else 1
        }
        val zoom: Float = pdfView.zoom
        val offsetX: Float = pdfView.currentXOffset - delta * zoom
        val offsetY: Float = pdfView.currentYOffset - delta * zoom
        val startingPage: Int = pdfView.findFocusPage(xOffset = offsetX, yOffset = offsetY)
        val targetPage: Int = startingPage + direction.coerceIn(minimumValue = 0, maximumValue = pdfView.pagesCount - 1)
        val edge: SnapEdge = pdfView.findSnapEdge(page = targetPage)
        val offset: Float = pdfView.snapOffsetForPage(pageIndex = targetPage, edge = edge)
        animationManager.startPageFlingAnimation(targetOffset = -offset)
    }

    override fun onDoubleTap(e: MotionEvent): Boolean {
        if (!pdfView.isDoubleTapEnabled) return false
        val zoom: Float = pdfView.zoom

        when {
            zoom < pdfView.midZoom -> pdfView.zoomWithAnimation(centerX = e.x, centerY = e.y, scale = pdfView.midZoom)
            zoom < pdfView.maxZoom -> pdfView.zoomWithAnimation(centerX = e.x, centerY = e.y, scale = pdfView.maxZoom)
            else -> pdfView.resetZoomWithAnimation()
        }
        return true
    }

    override fun onDoubleTapEvent(e: MotionEvent): Boolean = false

    override fun onDown(e: MotionEvent): Boolean {
        animationManager.stopFling()
        return true
    }

    override fun onShowPress(e: MotionEvent) {}
    override fun onSingleTapUp(e: MotionEvent): Boolean = false

    override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
        isScrolling = true
        if (pdfView.isZooming || pdfView.isSwipeEnabled) {
            pdfView.moveRelativeTo(dx = -distanceX, dy = -distanceY)
        }
        if (!isScaling || pdfView.isRenderDuringScale) pdfView.loadPageByOffset()
        return true
    }

    private fun onScrollEnd() {
        pdfView.loadPages()
        hideHandle()
        if (!animationManager.isFlinging()) pdfView.performPageSnap()
    }

    override fun onLongPress(e: MotionEvent) {
        pdfView.callbacks.callOnLongPress(event = e)
    }

    override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
        if (!pdfView.isSwipeEnabled) return false
        val zoom: Float = pdfView.zoom

        return if (pdfView.isPageFlingEnabled) {
            if (pdfView.pageFillsScreen()) {
                onBoundedFling(velocityX = velocityX, velocityY = velocityY)
            } else {
                e1?.let {
                    startPageFling(downEvent = it, ev = e2, velocityX = velocityX, velocityY = velocityY)
                }
            }
            true
        } else {
            pdfView.pdfFile?.let { pdfFile ->
                val (minX, minY) = if (pdfView.isSwipeVertical) {
                    -(pdfView.toCurrentScale(size = pdfFile.maxPageWidth) - pdfView.width) to
                            -(pdfFile.docLength(zoom = zoom) - pdfView.height)
                } else {
                    -(pdfFile.docLength(zoom = zoom) - pdfView.width) to
                            -(pdfView.toCurrentScale(size = pdfFile.maxPageHeight) - pdfView.height)
                }

                animationManager.startFlingAnimation(
                    startX = pdfView.currentXOffset.toInt(),
                    startY = pdfView.currentYOffset.toInt(),
                    velocityX = velocityX.toInt(),
                    velocityY = velocityY.toInt(),
                    minX = minX.toInt(),
                    maxX = 0,
                    minY = minY.toInt(),
                    maxY = 0
                )
                true
            } ?: false
        }
    }

    private fun onBoundedFling(velocityX: Float, velocityY: Float) {
        data class Bounds(val minX: Float, val minY: Float, val maxX: Float, val maxY: Float)
        pdfView.pdfFile?.let { pdfFile ->
            val currentPage: Int = pdfView.currentPage
            val zoom: Float = pdfView.zoom
            val pageStart: Float = -pdfFile.getPageOffset(pageIndex = currentPage, zoom = zoom)
            val pageEnd: Float = pageStart - pdfFile.getPageLength(pageIndex = currentPage, zoom = zoom)

            val bounds: Bounds = if (pdfView.isSwipeVertical) {
                Bounds(
                    minX = -(pdfView.toCurrentScale(size = pdfFile.maxPageWidth) - pdfView.width),
                    maxY = pageEnd + pdfView.height,
                    maxX = 0f,
                    minY = pageStart
                )
            } else {
                Bounds(
                    minX = pageEnd + pdfView.width,
                    maxY = -(pdfView.toCurrentScale(size = pdfFile.maxPageHeight) - pdfView.height),
                    maxX = pageStart,
                    minY = 0f
                )
            }
            val (minX, minY, maxX, maxY) = bounds

            animationManager.startFlingAnimation(
                startX = pdfView.currentXOffset.toInt(),
                startY = pdfView.currentYOffset.toInt(),
                velocityX = velocityX.toInt(),
                velocityY = velocityY.toInt(),
                minX = minX.toInt(),
                maxX = maxX.toInt(),
                minY = minY.toInt(),
                maxY = maxY.toInt()
            )
        }
    }

    override fun onScale(detector: ScaleGestureDetector): Boolean {
        val maxZoom: Float = MAXIMUM_ZOOM.coerceAtMost(maximumValue = pdfView.maxZoom)
        val minZoom: Float = MINIMUM_ZOOM.coerceAtMost(maximumValue = pdfView.minZoom)
        val zoom: Float = pdfView.zoom
        val wantedZoom: Float = zoom * detector.scaleFactor

        val scaleFactor: Float = when {
            wantedZoom < minZoom -> minZoom / zoom
            wantedZoom > maxZoom -> maxZoom / zoom
            else -> detector.scaleFactor
        }

        pdfView.zoomCenteredRelativeTo(zoom = scaleFactor, pivot = PointF(detector.focusX, detector.focusY))
        return true
    }

    override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
        isScaling = true
        return true
    }

    override fun onScaleEnd(detector: ScaleGestureDetector) {
        pdfView.loadPages()
        hideHandle()
        isScaling = false
    }

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        if (!isEnabled) return false

        val scaleGesture: Boolean = scaleGestureDetector.onTouchEvent(event)
        val gesture: Boolean = gestureDetector.onTouchEvent(event)

        if (event.action == MotionEvent.ACTION_UP && isScrolling) {
            isScrolling = false
            onScrollEnd()
        }

        v.performClick()
        return scaleGesture || gesture
    }

    private fun hideHandle() {
        pdfView.scrollHandle?.takeIf { it.shown() }?.hideDelayed()
    }

    private fun checkDoPageFling(velocityX: Float, velocityY: Float): Boolean {
        val absX: Float = abs(x = velocityX)
        val absY: Float = abs(x = velocityY)
        return if (pdfView.isSwipeVertical) absY > absX else absX > absY
    }

    init {
        pdfView.setOnTouchListener(this)
    }
}