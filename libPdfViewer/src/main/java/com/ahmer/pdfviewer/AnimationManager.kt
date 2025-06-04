package com.ahmer.pdfviewer

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.PointF
import android.view.animation.DecelerateInterpolator
import android.widget.OverScroller

/**
 * This manager is used by the PDFView to launch animations. It uses the ValueAnimator appeared in
 * API 11 to start an animation, and call moveTo() on the PDFView as a result of each animation update.
 */
internal class AnimationManager(private val pdfView: PDFView) {
    private val overScroller: OverScroller = OverScroller(pdfView.context)
    private var isFlinging: Boolean = false
    private var isPageFlinging: Boolean = false
    private var currentAnimator: ValueAnimator? = null

    private fun hideHandle() {
        pdfView.getScrollHandle()?.hideDelayed()
    }

    fun startXAnimation(xFrom: Float, xTo: Float) {
        stopAll()
        val listener = XAnimationListener()
        currentAnimator = ValueAnimator.ofFloat(xFrom, xTo).apply {
            interpolator = DecelerateInterpolator()
            addUpdateListener(listener)
            addListener(listener)
            duration = FLING_DURATION
            start()
        }
    }

    fun startYAnimation(yFrom: Float, yTo: Float) {
        stopAll()
        val listener = YAnimationListener()
        currentAnimator = ValueAnimator.ofFloat(yFrom, yTo).apply {
            interpolator = DecelerateInterpolator()
            addUpdateListener(listener)
            addListener(listener)
            duration = FLING_DURATION
            start()
        }
    }

    fun startZoomAnimation(centerX: Float, centerY: Float, zoomFrom: Float, zoomTo: Float) {
        stopAll()
        val listener = ZoomAnimationListener(centerX = centerX, centerY = centerY)
        currentAnimator = ValueAnimator.ofFloat(zoomFrom, zoomTo).apply {
            interpolator = DecelerateInterpolator()
            addUpdateListener(listener)
            addListener(listener)
            duration = FLING_DURATION
            start()
        }
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
        isFlinging = true
        overScroller.fling(startX, startY, velocityX, velocityY, minX, maxX, minY, maxY)
    }

    fun startPageFlingAnimation(targetOffset: Float) {
        if (pdfView.isSwipeVertical()) {
            startYAnimation(yFrom = pdfView.getCurrentYOffset(), yTo = targetOffset)
        } else {
            startXAnimation(xFrom = pdfView.getCurrentXOffset(), xTo = targetOffset)
        }
        isPageFlinging = true
    }

    fun computeFling() {
        if (overScroller.computeScrollOffset()) {
            pdfView.moveTo(offsetX = overScroller.currX.toFloat(), offsetY = overScroller.currY.toFloat())
            pdfView.loadPageByOffset()
        } else if (isFlinging) {
            isFlinging = false
            pdfView.loadPages()
            hideHandle()
            pdfView.performPageSnap()
        }
    }

    fun stopAll() {
        currentAnimator?.cancel()
        currentAnimator = null
        stopFling()
    }

    fun stopFling() {
        isFlinging = false
        overScroller.forceFinished(true)
    }

    fun isFlinging(): Boolean = isFlinging || isPageFlinging

    private companion object {
        const val FLING_DURATION: Long = 400L
    }

    private inner class XAnimationListener : AnimatorListenerAdapter(),
        ValueAnimator.AnimatorUpdateListener {
        override fun onAnimationUpdate(animation: ValueAnimator) {
            pdfView.moveTo(offsetX = animation.animatedValue as Float, offsetY = pdfView.getCurrentYOffset())
            pdfView.loadPageByOffset()
        }

        override fun onAnimationCancel(animation: Animator) {
            isPageFlinging = false
            pdfView.loadPages()
            hideHandle()
        }

        override fun onAnimationEnd(animation: Animator) {
            isPageFlinging = false
            pdfView.loadPages()
            hideHandle()
        }
    }

    private inner class YAnimationListener : AnimatorListenerAdapter(),
        ValueAnimator.AnimatorUpdateListener {
        override fun onAnimationUpdate(animation: ValueAnimator) {
            pdfView.moveTo(offsetX = pdfView.getCurrentXOffset(), offsetY = animation.animatedValue as Float)
            pdfView.loadPageByOffset()
        }

        override fun onAnimationCancel(animation: Animator) {
            isPageFlinging = false
            pdfView.loadPages()
            hideHandle()
        }

        override fun onAnimationEnd(animation: Animator) {
            isPageFlinging = false
            pdfView.loadPages()
            hideHandle()
        }
    }

    private inner class ZoomAnimationListener(
        private val centerX: Float,
        private val centerY: Float
    ) : AnimatorListenerAdapter(), ValueAnimator.AnimatorUpdateListener {
        override fun onAnimationUpdate(animation: ValueAnimator) {
            pdfView.zoomCenteredTo(zoom = animation.animatedValue as Float, pivot = PointF(centerX, centerY))
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
    }
}