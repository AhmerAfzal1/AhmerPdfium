package com.ahmer.pdfviewer

import android.graphics.PointF
import android.view.GestureDetector
import android.view.GestureDetector.OnDoubleTapListener
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.ScaleGestureDetector.OnScaleGestureListener
import android.view.View
import android.view.View.OnTouchListener
import com.ahmer.pdfium.util.SizeF
import com.ahmer.pdfviewer.model.LinkTapEvent
import com.ahmer.pdfviewer.util.PdfConstants.Pinch.MAXIMUM_ZOOM
import com.ahmer.pdfviewer.util.PdfConstants.Pinch.MINIMUM_ZOOM
import kotlin.math.abs

/**
 * This Manager takes care of moving the PDFView, set its zoom track user actions.
 */
internal class DragPinchManager(
    private val pdfView: PDFView, private val animationManager: AnimationManager
) : GestureDetector.OnGestureListener, OnDoubleTapListener, OnScaleGestureListener,
    OnTouchListener {

    private val mGestureDetector = GestureDetector(pdfView.context, this)
    private val mScaleGestureDetector = ScaleGestureDetector(pdfView.context, this)
    private var isEnabled: Boolean = false
    private var isScaling: Boolean = false
    private var isScrolling: Boolean = false

    fun enable() {
        isEnabled = true
    }

    fun disable() {
        isEnabled = false
    }

    fun disableLongPress() {
        mGestureDetector.setIsLongpressEnabled(false)
    }

    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
        val mLinkTapped: Boolean = checkLinkTapped(e.x, e.y)
        val mOnTapHandled: Boolean = pdfView.callbacks.callOnTap(e)
        if (!mOnTapHandled && !mLinkTapped) {
            val mScrollHandle = pdfView.getScrollHandle()
            if (mScrollHandle != null && !pdfView.documentFitsView()) {
                if (!mScrollHandle.shown()) mScrollHandle.show() else mScrollHandle.hide()
            }
        }
        pdfView.performClick()
        return true
    }

    private fun checkLinkTapped(x: Float, y: Float): Boolean {
        val mPdfFile: PdfFile = pdfView.pdfFile ?: return false
        val mMappedX: Float = -pdfView.getCurrentXOffset() + x
        val mMappedY: Float = -pdfView.getCurrentYOffset() + y
        val mOffset: Float = if (pdfView.isSwipeVertical()) mMappedY else mMappedX
        val mPage: Int = mPdfFile.getPageAtOffset(mOffset, pdfView.getZoom())
        val mPageSize: SizeF = mPdfFile.getScaledPageSize(mPage, pdfView.getZoom())
        val mPageX: Int
        val mPageY: Int

        if (pdfView.isSwipeVertical()) {
            mPageX = mPdfFile.getSecondaryPageOffset(mPage, pdfView.getZoom()).toInt()
            mPageY = mPdfFile.getPageOffset(mPage, pdfView.getZoom()).toInt()
        } else {
            mPageY = mPdfFile.getSecondaryPageOffset(mPage, pdfView.getZoom()).toInt()
            mPageX = mPdfFile.getPageOffset(mPage, pdfView.getZoom()).toInt()
        }
        for (mLink in mPdfFile.getPageLinks(mPage)) {
            val mMapped = mPdfFile.mapRectToDevice(
                mPage, mPageX, mPageY, mPageSize.width.toInt(),
                mPageSize.height.toInt(), mLink.bounds
            )
            mMapped.sort()
            if (mMapped.contains(mMappedX, mMappedY)) {
                val mLinkTapEvent = LinkTapEvent(x, y, mMappedX, mMappedY, mMapped, mLink)
                pdfView.callbacks.callLinkHandler(mLinkTapEvent)
                return true
            }
        }
        return false
    }

    private fun startPageFling(
        downEvent: MotionEvent, ev: MotionEvent, velocityX: Float, velocityY: Float
    ) {
        if (!checkDoPageFling(velocityX, velocityY)) return
        val mDirection: Int = if (pdfView.isSwipeVertical()) {
            if (velocityY > 0) -1 else 1
        } else {
            if (velocityX > 0) -1 else 1
        }
        // Get the focused page during the down event to ensure only a single page is changed
        val mDelta = if (pdfView.isSwipeVertical()) ev.y - downEvent.y else ev.x - downEvent.x
        val mOffsetX = pdfView.getCurrentXOffset() - mDelta * pdfView.getZoom()
        val mOffsetY = pdfView.getCurrentYOffset() - mDelta * pdfView.getZoom()
        val mStartingPage = pdfView.findFocusPage(mOffsetX, mOffsetY)
        val mTargetPage =
            0.coerceAtLeast((pdfView.getPageCount() - 1).coerceAtMost(mStartingPage + mDirection))
        val mEdge = pdfView.findSnapEdge(mTargetPage)
        val mOffset = pdfView.snapOffsetForPage(mTargetPage, mEdge)
        animationManager.startPageFlingAnimation(-mOffset)
    }

    override fun onDoubleTap(e: MotionEvent): Boolean {
        if (!pdfView.isDoubleTapEnabled()) return false
        when {
            pdfView.getZoom() < pdfView.getMidZoom() -> {
                pdfView.zoomWithAnimation(e.x, e.y, pdfView.getMidZoom())
            }
            pdfView.getZoom() < pdfView.getMaxZoom() -> {
                pdfView.zoomWithAnimation(e.x, e.y, pdfView.getMaxZoom())
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
        isScrolling = true
        if (pdfView.isZooming() || pdfView.isSwipeEnabled()) {
            pdfView.moveRelativeTo(-distanceX, -distanceY)
        }
        if (!isScaling || pdfView.isRenderDuringScale()) pdfView.loadPageByOffset()
        return true
    }

    private fun onScrollEnd(event: MotionEvent) {
        pdfView.loadPages()
        hideHandle()
        if (!animationManager.isFlinging()) pdfView.performPageSnap()
    }

    override fun onLongPress(e: MotionEvent) {
        pdfView.callbacks.callOnLongPress(e)
    }

    override fun onFling(e1: MotionEvent, e2: MotionEvent, velocityX: Float, velocityY: Float):
            Boolean {
        if (!pdfView.isSwipeEnabled()) return false
        if (pdfView.isPageFlingEnabled()) {
            if (pdfView.pageFillsScreen()) {
                onBoundedFling(velocityX, velocityY)
            } else {
                startPageFling(e1, e2, velocityX, velocityY)
            }
            return true
        }

        val mMinX: Float
        val mMinY: Float
        val mOffsetX: Int = pdfView.getCurrentXOffset().toInt()
        val mOffsetY: Int = pdfView.getCurrentYOffset().toInt()
        val mPdfFile: PdfFile? = pdfView.pdfFile

        if (pdfView.isSwipeVertical()) {
            mMinX = -(pdfView.toCurrentScale(mPdfFile!!.maxPageWidth) - pdfView.width)
            mMinY = -(mPdfFile.getDocLen(pdfView.getZoom()) - pdfView.height)
        } else {
            mMinX = -(mPdfFile!!.getDocLen(pdfView.getZoom()) - pdfView.width)
            mMinY = -(pdfView.toCurrentScale(mPdfFile.maxPageHeight) - pdfView.height)
        }
        animationManager.startFlingAnimation(
            mOffsetX, mOffsetY, velocityX.toInt(), velocityY.toInt(), mMinX.toInt(), 0,
            mMinY.toInt(), 0
        )
        return true
    }

    private fun onBoundedFling(velocityX: Float, velocityY: Float) {
        val mMaxX: Float
        val mMaxY: Float
        val mMinX: Float
        val mMinY: Float
        val mOffsetX: Int = pdfView.getCurrentXOffset().toInt()
        val mOffsetY: Int = pdfView.getCurrentYOffset().toInt()
        val mPdfFile: PdfFile? = pdfView.pdfFile
        val mPageStart = -mPdfFile!!.getPageOffset(pdfView.getCurrentPage(), pdfView.getZoom())
        val mPageEnd =
            mPageStart - mPdfFile.getPageLength(pdfView.getCurrentPage(), pdfView.getZoom())

        if (pdfView.isSwipeVertical()) {
            mMinX = -(pdfView.toCurrentScale(mPdfFile.maxPageWidth) - pdfView.width)
            mMinY = mPageEnd + pdfView.height
            mMaxX = 0f
            mMaxY = mPageStart
        } else {
            mMinX = mPageEnd + pdfView.width
            mMinY = -(pdfView.toCurrentScale(mPdfFile.maxPageHeight) - pdfView.height)
            mMaxX = mPageStart
            mMaxY = 0f
        }
        animationManager.startFlingAnimation(
            mOffsetX, mOffsetY, velocityX.toInt(), velocityY.toInt(),
            mMinX.toInt(), mMaxX.toInt(), mMinY.toInt(), mMaxY.toInt()
        )
    }

    override fun onScale(detector: ScaleGestureDetector): Boolean {
        var mDetector: Float = detector.scaleFactor
        val mMaxZoom: Float = MAXIMUM_ZOOM.coerceAtMost(pdfView.getMaxZoom())
        val mMinZoom: Float = MINIMUM_ZOOM.coerceAtMost(pdfView.getMinZoom())
        val mWantedZoom: Float = pdfView.getZoom() * mDetector
        if (mWantedZoom < mMinZoom) {
            mDetector = mMinZoom / pdfView.getZoom()
        } else if (mWantedZoom > mMaxZoom) {
            mDetector = mMaxZoom / pdfView.getZoom()
        }
        pdfView.zoomCenteredRelativeTo(mDetector, PointF(detector.focusX, detector.focusY))
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
        var mScaleGesture: Boolean = mScaleGestureDetector.onTouchEvent(event)
        mScaleGesture = mGestureDetector.onTouchEvent(event) || mScaleGesture
        if (event.action == MotionEvent.ACTION_UP) {
            if (isScrolling) {
                isScrolling = false
                onScrollEnd(event)
            }
        }
        return mScaleGesture
    }

    private fun hideHandle() {
        val mScrollHandle = pdfView.getScrollHandle()
        if (mScrollHandle != null && mScrollHandle.shown()) {
            mScrollHandle.hideDelayed()
        }
    }

    private fun checkDoPageFling(velocityX: Float, velocityY: Float): Boolean {
        val mAbsX: Float = abs(velocityX)
        val mAbsY: Float = abs(velocityY)
        return if (pdfView.isSwipeVertical()) mAbsY > mAbsX else mAbsX > mAbsY
    }

    init {
        pdfView.setOnTouchListener(this)
    }
}