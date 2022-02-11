package com.ahmer.pdfviewer

import android.graphics.PointF
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.Log
import android.view.GestureDetector
import android.view.GestureDetector.OnDoubleTapListener
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.ScaleGestureDetector.OnScaleGestureListener
import android.view.View
import android.view.View.OnTouchListener
import com.ahmer.pdfium.util.Size
import com.ahmer.pdfium.util.SizeF
import com.ahmer.pdfviewer.exception.PageRenderingException
import com.ahmer.pdfviewer.model.LinkTapEvent
import com.ahmer.pdfviewer.util.PdfConstants
import com.ahmer.pdfviewer.util.PdfConstants.Pinch.MAXIMUM_ZOOM
import com.ahmer.pdfviewer.util.PdfConstants.Pinch.MINIMUM_ZOOM
import kotlin.math.abs

/**
 * This Manager takes care of moving the PDFView, set its zoom track user actions.
 */
class DragPinchManager(
    private val pdfView: PDFView, private val animationManager: AnimationManager
) : GestureDetector.OnGestureListener, OnDoubleTapListener, OnScaleGestureListener,
    OnTouchListener {

    private val lock = Any()
    var currentTextPtr: Long = 0
    var lastX = 0f
    var lastY = 0f
    var orgX = 0f
    var orgY = 0f
    var draggingHandle: Drawable? = null
    var lineHeight = 0f
    var viewPagerToGuardLastX = 0f
    var viewPagerToGuardLastY = 0f
    var sCursorPosStart = PointF()
    var pageBreakIterator: BreakIteratorHelper? = null
    var allText: String? = null
    var scrollValue = 0f

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
        var mOnTapHandled = false

        if (pdfView.hasSelection) {
            pdfView.clearSelection()
        } else {
            mOnTapHandled = pdfView.callbacks.callOnTap(e)
        }
        val mLinkTapped: Boolean = checkLinkTapped(e.x, e.y)
        if (!mOnTapHandled && !mLinkTapped) {
            val mScrollHandle = pdfView.getScrollHandle()
            if (mScrollHandle != null && !pdfView.documentFitsView()) {
                if (!mScrollHandle.shown()) mScrollHandle.show() else mScrollHandle.hide()
            }
        }
        pdfView.performClick()
        return true
    }

    fun getCharIdxAtPos(x: Float, y: Float, tolFactor: Int): Int {
        val pdfFile = pdfView.pdfFile ?: return -1
        val mappedX = -pdfView.getCurrentXOffset() + x
        val mappedY = -pdfView.getCurrentYOffset() + y
        val page = pdfFile.getPageAtOffset(
            if (pdfView.isSwipeVertical()) mappedY else mappedX,
            pdfView.getZoom()
        )
        val pageSize = pdfFile.getScaledPageSize(page, pdfView.getZoom())
        val pageIndex = pdfFile.documentPage(page)
        val pagePtr: Long? = pdfFile.pdfDocument.mNativePagesPtr[pageIndex]
        Log.e("pageIndex", pageIndex.toString())
        val tid: Long = prepareText()
        if (pdfView.isNotCurrentPage(tid)) {
            return -1
        }
        if (tid != 0L) {
            //int charIdx = pdfiumCore.nativeGetCharIndexAtPos(tid, posX, posY, 10.0, 10.0);
            val pageX = pdfFile.getSecondaryPageOffset(page, pdfView.getZoom()).toInt()
            val pageY = pdfFile.getPageOffset(page, pdfView.getZoom()).toInt()
            return pdfFile.pdfiumCore.nativeGetCharIndexAtCoord(
                pagePtr!!, pageSize.width.toDouble(), pageSize.height.toDouble(), tid,
                abs(mappedX - pageX).toDouble(), abs(mappedY - pageY).toDouble(),
                10.0 * tolFactor, 10.0 * tolFactor
            )
        }
        return -1
    }

    fun getCharIdxAt(x: Float, y: Float, tolFactor: Int): Int {
        val pdfFile = pdfView.pdfFile ?: return -1
        val page: Int = pdfView.getCurrentPage()
        val pageSize = pdfFile.getPageSize(page)
        val pageIndex = pdfFile.documentPage(page)
        val pagePtr: Long? = pdfFile.pdfDocument.mNativePagesPtr[pageIndex]
        Log.e("pageIndex", pageIndex.toString())
        val tid: Long = prepareText()
        if (pdfView.isNotCurrentPage(tid)) {
            return -1
        }
        if (tid != 0L) {
            //int charIdx = pdfiumCore.nativeGetCharIndexAtPos(tid, posX, posY, 10.0, 10.0);
            val pageX = pdfFile.getSecondaryPageOffset(page, pdfView.getZoom()).toInt()
            val pageY = pdfFile.getPageOffset(page, pdfView.getZoom()).toInt()
            return pdfFile.pdfiumCore.nativeGetCharIndexAtCoord(
                pagePtr!!, pageSize.width.toDouble(), pageSize.height.toDouble(), tid,
                x.toDouble(), y.toDouble(), 10.0 * tolFactor, 10.0 * tolFactor
            )
        }
        return -1
    }

    private fun wordTapped(x: Float, y: Float, tolFactor: Float): Boolean {
        val pdfFile = pdfView.pdfFile ?: return false
        val mappedX = -pdfView.getCurrentXOffset() + x
        val mappedY = -pdfView.getCurrentYOffset() + y
        val page = pdfFile.getPageAtOffset(
            if (pdfView.isSwipeVertical()) mappedY else mappedX,
            pdfView.getZoom()
        )
        val pageSize = pdfFile.getScaledPageSize(page, pdfView.getZoom())
        val pageIndex = pdfFile.documentPage(page)
        val pagePtr: Long? = pdfFile.pdfDocument.mNativePagesPtr[pageIndex]
        val tid: Long = prepareText()
        currentTextPtr = tid
        if (tid != 0L) {
            //int charIdx = pdfiumCore.nativeGetCharIndexAtPos(tid, posX, posY, 10.0, 10.0);
            val pageX = pdfFile.getSecondaryPageOffset(page, pdfView.getZoom()).toInt()
            val pageY = pdfFile.getPageOffset(page, pdfView.getZoom()).toInt()
            val charIdx = pdfFile.pdfiumCore.nativeGetCharIndexAtCoord(
                pagePtr!!, pageSize.width.toDouble(), pageSize.height.toDouble(), tid,
                abs(mappedX - pageX).toDouble(), abs(mappedY - pageY).toDouble(),
                10.0 * tolFactor, 10.0 * tolFactor
            )
            var ret: String? = null
            if (charIdx >= 0) {
                val ed = pageBreakIterator!!.following(charIdx)
                val st = pageBreakIterator!!.previous()
                try {
                    ret = allText!!.substring(st, ed)
                    pdfView.setSelectionAtPage(pageIndex, st, ed)
                    //Toast.makeText(pdfView.getContext(), String.valueOf(ret), Toast.LENGTH_SHORT).show();
                    return true
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        return false
    }

    fun getSelRects(rectPagePool: ArrayList<RectF>, selSt: Int, selEd: Int) {
        var selectionStart = selSt
        var selectionEnd = selEd
        val mappedX = -pdfView.getCurrentXOffset() + lastX
        val mappedY = -pdfView.getCurrentYOffset() + lastY
        val page = pdfView.pdfFile!!.getPageAtOffset(
            if (pdfView.isSwipeVertical()) mappedY else mappedX,
            pdfView.getZoom()
        )
        val tid: Long = prepareText()
        if (pdfView.isNotCurrentPage(tid)) {
            return
        }
        rectPagePool.clear()
        if (tid != 0L) {
            if (selectionEnd == -1) {
                selectionEnd = allText!!.length
            }
            if (selectionEnd < selectionStart) {
                val tmp = selectionStart
                selectionStart = selectionEnd
                selectionEnd = tmp
            }
            selectionEnd -= selectionStart
            if (selectionEnd > 0) {
                val pagePtr: Long? = pdfView.pdfFile!!.pdfDocument.mNativePagesPtr[page]
                val pageX =
                    pdfView.pdfFile!!.getSecondaryPageOffset(page, pdfView.getZoom()).toInt()
                val pageY = pdfView.pdfFile!!.getPageOffset(page, pdfView.getZoom()).toInt()
                pdfView.pdfiumCore?.getPageSize(pdfView.pdfFile!!.pdfDocument, page)
                val size = pdfView.pdfFile!!.getPageSize(page)
                val rectCount: Int? = pdfView.pdfiumCore?.getTextRects(
                    pagePtr!!, 0, 0, Size(size.width.toInt(), size.height.toInt()),
                    rectPagePool, tid, selectionStart, selectionEnd
                )
                Log.v(
                    PdfConstants.TAG, "getTextRects: $selectionStart$$selectionEnd$$rectCount$$rectPagePool"
                 )
                if (rectCount != null) {
                    if (rectCount >= 0 && rectPagePool.size > rectCount) {
                        rectPagePool.subList(rectCount, rectPagePool.size).clear()
                    }
                }
            }
        }
    }

    fun prepareText(): Long {
        val mappedX = -pdfView.getCurrentXOffset() + lastX
        val mappedY = -pdfView.getCurrentYOffset() + lastY
        val page = pdfView.pdfFile!!.getPageAtOffset(
            if (pdfView.isSwipeVertical()) mappedY else mappedX,
            pdfView.getZoom()
        )
        return prepareText(page)
    }

    fun prepareText(page: Int): Long {
        val tid = loadText(page)
        if (tid != -1L) {
            allText = pdfView.pdfiumCore!!.nativeGetText(tid!!)
            if (pageBreakIterator == null) {
                pageBreakIterator = BreakIteratorHelper()
            }
            pageBreakIterator!!.setText(allText)
        }
        return tid
    }

    fun loadText(): Long? {
        val mappedX = -pdfView.getCurrentXOffset() + lastX
        val mappedY = -pdfView.getCurrentYOffset() + lastY
        if (pdfView.pdfFile == null) return 0L
        val page = pdfView.pdfFile!!.getPageAtOffset(
            if (pdfView.isSwipeVertical()) mappedY else mappedX,
            pdfView.getZoom()
        )
        return loadText(page)
    }

    fun loadText(page: Int): Long? {
        synchronized(lock) {
            if (!pdfView.pdfFile!!.pdfDocument.hasPage(page)) {
                try {
                    pdfView.pdfFile!!.openPage(page)
                } catch (e: PageRenderingException) {
                    e.printStackTrace()
                }
            }
            val pagePtr: Long? = pdfView.pdfFile!!.pdfDocument.mNativePagesPtr[page]
            if (!pdfView.pdfFile!!.pdfDocument.hasText(page)) {
                val openTextPtr: Long = pdfView.pdfiumCore!!.openText(pagePtr!!)
                pdfView.pdfFile!!.pdfDocument.mNativeTextPtr[page] = openTextPtr
            }
        }
        return pdfView.pdfFile!!.pdfDocument.mNativeTextPtr[page]
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
        if (pdfView.startInDrag) return true
        isScrolling = true
        if (pdfView.isZooming() || pdfView.isSwipeEnabled()) {
            pdfView.moveRelativeTo(-distanceX, -distanceY)
        }
        if (!isScaling || pdfView.isRenderDuringScale()) pdfView.loadPageByOffset()
        scrollValue = distanceY
        Log.e(PdfConstants.TAG, "ScrollY: $distanceY")
        return true
    }

    private fun onScrollEnd(event: MotionEvent) {
        pdfView.loadPages()
        hideHandle()
        if (!animationManager.isFlinging()) pdfView.performPageSnap()
        if (scrollValue <= -10 && pdfView.getCurrentPage() == 0) {
            if (pdfView.hideView != null) pdfView.hideView!!.visibility = View.VISIBLE
        }
    }

    override fun onLongPress(e: MotionEvent) {
        if (pdfView.hasSelection) pdfView.clearSelection()
        if (wordTapped(e.x, e.y, 2f)) {
            pdfView.hasSelection = true
            if (pdfView.onSelection != null) {
                pdfView.onSelection!!.onSelection(true)
            }
            draggingHandle = pdfView.handleRight
            sCursorPosStart.set(pdfView.handleRightPos.right, pdfView.handleRightPos.bottom)
        }
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
        val mPdfFile = pdfView.pdfFile

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
        val mMappedX = -pdfView.getCurrentXOffset() + lastX
        val mMappedY = -pdfView.getCurrentYOffset() + lastY
        val mMaxX: Float
        val mMaxY: Float
        val mMinX: Float
        val mMinY: Float
        val mOffsetX: Int = pdfView.getCurrentXOffset().toInt()
        val mOffsetY: Int = pdfView.getCurrentYOffset().toInt()
        val mPdfFile: PdfFile? = pdfView.pdfFile
        val mPage: Int = mPdfFile!!.getPageAtOffset(
            if (pdfView.isSwipeVertical()) mMappedY else mMappedX, pdfView.getZoom()
        )
        val mPageStart = -mPdfFile.getPageOffset(mPage, pdfView.getZoom())
        val mPageEnd = mPageStart - mPdfFile.getPageLength(mPage, pdfView.getZoom())

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
        lastX = event.x
        lastY = event.y
        pdfView.redrawSel()
        if (event.action == MotionEvent.ACTION_UP) {
            if (draggingHandle != null) draggingHandle = null
            pdfView.startInDrag = false
            if (isScrolling) {
                isScrolling = false
                onScrollEnd(event)
            }
        } else if (event.action == MotionEvent.ACTION_DOWN) {
            orgX = lastX.also { viewPagerToGuardLastX = it }
            orgY = lastY.also { viewPagerToGuardLastY = it }
            if (pdfView.hasSelection) {
                if (pdfView.handleLeft!!.bounds.contains(orgX.toInt(), orgY.toInt())) {
                    draggingHandle = pdfView.handleLeft
                    sCursorPosStart[pdfView.handleLeftPos.left] = pdfView.handleLeftPos.bottom
                } else if (pdfView.handleRight!!.bounds.contains(orgX.toInt(), orgY.toInt())) {
                    draggingHandle = pdfView.handleRight
                    sCursorPosStart[pdfView.handleRightPos.right] = pdfView.handleRightPos.bottom
                }
            }
        } else if (event.action == MotionEvent.ACTION_MOVE) {
            dragHandle(event.x, event.y)
            viewPagerToGuardLastX = lastX
            viewPagerToGuardLastY = lastY
        }
        return true;
    }

    private fun dragHandle(x: Float, y: Float) {
        if (draggingHandle != null) {
            pdfView.startInDrag = true
            lineHeight =
                if (draggingHandle === pdfView.handleLeft) pdfView.lineHeightLeft else pdfView.lineHeightRight
            val posX = sCursorPosStart.x + (lastX - orgX) / pdfView.getZoom()
            val posY = sCursorPosStart.y + (lastY - orgY) / pdfView.getZoom()
            pdfView.sCursorPos.set(posX, posY)
            val isLeft = draggingHandle === pdfView.handleLeft
            val mappedX = -pdfView.getCurrentXOffset() + x
            val mappedY = -pdfView.getCurrentYOffset() + y
            val page = pdfView.pdfFile!!.getPageAtOffset(
                if (pdfView.isSwipeVertical()) mappedY else mappedX,
                pdfView.getZoom()
            )
            val pageIndex = pdfView.pdfFile!!.documentPage(page)
            var charIdx = -1
            val pageI = pdfView
            // long pagePtr = pageI.pdfFile.pdfDocument.mNativePagesPtr.get(pageI.getCurrentPage());
            //posY -= pageI.getCurrentXOffset();
            //posX -= pageI.getCurrentXOffset();
            charIdx = getCharIdxAtPos(x, y - lineHeight, 10)
            pdfView.selectionPaintView!!.supressRecalcInval = true
            Log.e("charIdx", charIdx.toString())
            if (charIdx >= 0) {
                if (isLeft) {
                    if (pageIndex != pdfView.selPageSt || charIdx != pdfView.selStart) {
                        pdfView.selPageSt = pageIndex
                        pdfView.selStart = charIdx
                        pdfView.selectionPaintView!!.resetSel()
                    }
                } else {
                    charIdx += 1
                    if (pageIndex != pdfView.selPageEd || charIdx != pdfView.selEnd) {
                        pdfView.selPageEd = pageIndex
                        pdfView.selEnd = charIdx
                        pdfView.selectionPaintView!!.resetSel()
                    }
                }
            }
            pdfView.redrawSel()
            // Toast.makeText(pdfView.getContext(),   pdfView. getSelection(), Toast.LENGTH_SHORT).show();
            //   pdfView.text.setText(pdfView.getSelection());
            pdfView.selectionPaintView!!.supressRecalcInval = false
        }
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