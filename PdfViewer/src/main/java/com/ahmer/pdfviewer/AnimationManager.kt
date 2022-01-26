package com.ahmer.pdfviewer

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.animation.ValueAnimator.AnimatorUpdateListener
import android.graphics.PointF
import android.view.animation.DecelerateInterpolator
import android.widget.OverScroller

/**
 * This manager is used by the PDFView to launch animations. It uses the ValueAnimator appeared in
 * API 11 to start an animation, and call moveTo() on the PDFView as a result of each animation update.
 */
internal class AnimationManager(private val pdfView: PDFView) {

    private val mFlingDuration: Long = 400
    private val mOverScroller: OverScroller = OverScroller(pdfView.context)
    private var isFlinging = false
    private var isPageFlinging = false
    private var mAnimation: ValueAnimator? = null

    fun startXAnimation(xFrom: Float, xTo: Float) {
        stopAll()
        val xAnimation = XAnimation()
        mAnimation = ValueAnimator.ofFloat(xFrom, xTo).apply {
            interpolator = DecelerateInterpolator()
            addUpdateListener(xAnimation)
            addListener(xAnimation)
            duration = mFlingDuration
            start()
        }
    }

    fun startYAnimation(yFrom: Float, yTo: Float) {
        stopAll()
        val yAnimation = YAnimation()
        mAnimation = ValueAnimator.ofFloat(yFrom, yTo).apply {
            interpolator = DecelerateInterpolator()
            addUpdateListener(yAnimation)
            addListener(yAnimation)
            duration = mFlingDuration
            start()
        }
    }

    fun startZoomAnimation(centerX: Float, centerY: Float, zoomFrom: Float, zoomTo: Float) {
        stopAll()
        val zoomAnimation = ZoomAnimation(centerX, centerY)
        mAnimation = ValueAnimator.ofFloat(zoomFrom, zoomTo).apply {
            interpolator = DecelerateInterpolator()
            addUpdateListener(zoomAnimation)
            addListener(zoomAnimation)
            duration = mFlingDuration
            start()
        }
    }

    fun startFlingAnimation(
        startX: Int, startY: Int, velocityX: Int, velocityY: Int, minX: Int, maxX: Int,
        minY: Int, maxY: Int
    ) {
        stopAll()
        isFlinging = true
        mOverScroller.fling(startX, startY, velocityX, velocityY, minX, maxX, minY, maxY)
    }

    fun startPageFlingAnimation(targetOffset: Float) {
        if (pdfView.isSwipeVertical()) {
            startYAnimation(pdfView.getCurrentYOffset(), targetOffset)
        } else {
            startXAnimation(pdfView.getCurrentXOffset(), targetOffset)
        }
        isPageFlinging = true
    }

    fun computeFling() {
        if (mOverScroller.computeScrollOffset()) {
            pdfView.moveTo(mOverScroller.currX.toFloat(), mOverScroller.currY.toFloat())
            pdfView.loadPageByOffset()
        } else if (isFlinging) { // Fling finished
            isFlinging = false
            pdfView.loadPages()
            hideHandle()
            pdfView.performPageSnap()
        }
    }

    fun stopAll() {
        if (mAnimation != null) {
            mAnimation?.cancel()
            mAnimation = null
        }
        stopFling()
    }

    fun stopFling() {
        isFlinging = false
        mOverScroller.forceFinished(true)
    }

    fun isFlinging(): Boolean {
        return isFlinging || isPageFlinging
    }

    private fun hideHandle() {
        pdfView.getScrollHandle()?.hideDelayed()
    }

    internal inner class XAnimation : AnimatorListenerAdapter(), AnimatorUpdateListener {
        override fun onAnimationUpdate(animation: ValueAnimator) {
            val offset = animation.animatedValue as Float
            pdfView.moveTo(offset, pdfView.getCurrentYOffset())
            pdfView.loadPageByOffset()
        }

        override fun onAnimationCancel(animation: Animator) {
            pdfView.loadPages()
            isPageFlinging = false
            hideHandle()
        }

        override fun onAnimationEnd(animation: Animator) {
            pdfView.loadPages()
            isPageFlinging = false
            hideHandle()
        }
    }

    internal inner class YAnimation : AnimatorListenerAdapter(), AnimatorUpdateListener {

        override fun onAnimationUpdate(animation: ValueAnimator) {
            val offset = animation.animatedValue as Float
            pdfView.moveTo(pdfView.getCurrentXOffset(), offset)
            pdfView.loadPageByOffset()
        }

        override fun onAnimationCancel(animation: Animator) {
            pdfView.loadPages()
            isPageFlinging = false
            hideHandle()
        }

        override fun onAnimationEnd(animation: Animator) {
            pdfView.loadPages()
            isPageFlinging = false
            hideHandle()
        }
    }

    internal inner class ZoomAnimation(private val centerX: Float, private val centerY: Float) :
        AnimatorUpdateListener, Animator.AnimatorListener {

        override fun onAnimationUpdate(animation: ValueAnimator) {
            val zoom = animation.animatedValue as Float
            pdfView.zoomCenteredTo(zoom, PointF(centerX, centerY))
        }

        override fun onAnimationCancel(animation: Animator) {
            pdfView.loadPages()
            hideHandle()
        }

        override fun onAnimationEnd(animation: Animator) {
            pdfView.loadPages()
            pdfView.performPageSnap()
            hideHandle()
        }

        override fun onAnimationRepeat(animation: Animator) {}
        override fun onAnimationStart(animation: Animator) {}
    }
}