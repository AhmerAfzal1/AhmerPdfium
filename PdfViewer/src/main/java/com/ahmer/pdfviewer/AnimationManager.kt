package com.ahmer.pdfviewer

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.animation.ValueAnimator.AnimatorUpdateListener
import android.graphics.PointF
import android.view.animation.DecelerateInterpolator
import android.widget.OverScroller

/**
 * This manager is used by the PDFView to launch animations.
 * It uses the ValueAnimator appeared in API 11 to start
 * an animation, and call moveTo() on the PDFView as a result
 * of each animation update.
 */
internal class AnimationManager(pdfView: PDFView) {

    private val mPdfView: PDFView = pdfView
    private val scroller: OverScroller = OverScroller(pdfView.context)
    private var animation: ValueAnimator? = null
    private var flinging = false
    private var pageFlinging = false

    fun startXAnimation(xFrom: Float, xTo: Float) {
        stopAll()
        animation = ValueAnimator.ofFloat(xFrom, xTo)
        val xAnimation = XAnimation()
        animation!!.interpolator = DecelerateInterpolator()
        animation!!.addUpdateListener(xAnimation)
        animation!!.addListener(xAnimation)
        animation!!.duration = 400
        animation!!.start()
    }

    fun startYAnimation(yFrom: Float, yTo: Float) {
        stopAll()
        animation = ValueAnimator.ofFloat(yFrom, yTo)
        val yAnimation = YAnimation()
        animation!!.interpolator = DecelerateInterpolator()
        animation!!.addUpdateListener(yAnimation)
        animation!!.addListener(yAnimation)
        animation!!.duration = 400
        animation!!.start()
    }

    fun startZoomAnimation(centerX: Float, centerY: Float, zoomFrom: Float, zoomTo: Float) {
        stopAll()
        animation = ValueAnimator.ofFloat(zoomFrom, zoomTo)
        animation!!.interpolator = DecelerateInterpolator()
        val zoomAnim = ZoomAnimation(centerX, centerY)
        animation!!.addUpdateListener(zoomAnim)
        animation!!.addListener(zoomAnim)
        animation!!.duration = 400
        animation!!.start()
    }

    fun startFlingAnimation(
        startX: Int,
        startY: Int,
        velocityX: Int,
        velocityY: Int,
        minX: Int,
        maxX: Int,
        minY: Int,
        maxY: Int
    ) {
        stopAll()
        flinging = true
        scroller.fling(startX, startY, velocityX, velocityY, minX, maxX, minY, maxY)
    }

    fun startPageFlingAnimation(targetOffset: Float) {
        if (mPdfView.isSwipeVertical) {
            startYAnimation(mPdfView.currentYOffset, targetOffset)
        } else {
            startXAnimation(mPdfView.currentXOffset, targetOffset)
        }
        pageFlinging = true
    }

    fun computeFling() {
        if (scroller.computeScrollOffset()) {
            mPdfView.moveTo(scroller.currX.toFloat(), scroller.currY.toFloat())
            mPdfView.loadPageByOffset()
        } else if (flinging) { // fling finished
            flinging = false
            mPdfView.loadPages()
            hideHandle()
            mPdfView.performPageSnap()
        }
    }

    fun stopAll() {
        if (animation != null) {
            animation!!.cancel()
            animation = null
        }
        stopFling()
    }

    fun stopFling() {
        flinging = false
        scroller.forceFinished(true)
    }

    fun isFlinging(): Boolean {
        return flinging || pageFlinging
    }

    private fun hideHandle() {
        if (mPdfView.scrollHandle != null) {
            mPdfView.scrollHandle!!.hideDelayed()
        }
    }

    internal inner class XAnimation : AnimatorListenerAdapter(),
        AnimatorUpdateListener {
        override fun onAnimationUpdate(animation: ValueAnimator) {
            val offset = animation.animatedValue as Float
            mPdfView.moveTo(offset, mPdfView.currentYOffset)
            mPdfView.loadPageByOffset()
        }

        override fun onAnimationCancel(animation: Animator) {
            mPdfView.loadPages()
            pageFlinging = false
            hideHandle()
        }

        override fun onAnimationEnd(animation: Animator) {
            mPdfView.loadPages()
            pageFlinging = false
            hideHandle()
        }
    }

    internal inner class YAnimation : AnimatorListenerAdapter(),
        AnimatorUpdateListener {
        override fun onAnimationUpdate(animation: ValueAnimator) {
            val offset = animation.animatedValue as Float
            mPdfView.moveTo(mPdfView.currentXOffset, offset)
            mPdfView.loadPageByOffset()
        }

        override fun onAnimationCancel(animation: Animator) {
            mPdfView.loadPages()
            pageFlinging = false
            hideHandle()
        }

        override fun onAnimationEnd(animation: Animator) {
            mPdfView.loadPages()
            pageFlinging = false
            hideHandle()
        }
    }

    internal inner class ZoomAnimation(private val centerX: Float, private val centerY: Float) :
        AnimatorUpdateListener, Animator.AnimatorListener {
        override fun onAnimationUpdate(animation: ValueAnimator) {
            val zoom = animation.animatedValue as Float
            mPdfView.zoomCenteredTo(zoom, PointF(centerX, centerY))
        }

        override fun onAnimationCancel(animation: Animator) {
            mPdfView.loadPages()
            hideHandle()
        }

        override fun onAnimationEnd(animation: Animator) {
            mPdfView.loadPages()
            mPdfView.performPageSnap()
            hideHandle()
        }

        override fun onAnimationRepeat(animation: Animator) {}
        override fun onAnimationStart(animation: Animator) {}
    }

}
