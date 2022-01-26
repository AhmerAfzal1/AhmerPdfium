package com.ahmer.pdfviewer.scroll

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.ahmer.afzal.pdfviewer.R
import com.ahmer.pdfviewer.PDFView
import com.ahmer.pdfviewer.util.PdfUtils
import java.lang.Float.isInfinite
import java.lang.Float.isNaN

class DefaultScrollHandle @JvmOverloads constructor(
    context: Context,
    private val inverted: Boolean = false
) : RelativeLayout(context), ScrollHandle {

    private val mHidePageScroller = Runnable { hide() }
    private var mCurrentPosition = 0f
    private var mHandlerMiddle = 0f
    private var mPdfView: PDFView? = null
    private var mTextView: TextView = TextView(context)

    override fun setupLayout(pdfView: PDFView) {
        val mAlign: Int
        val mBackground: Drawable?
        val mHeight: Int
        val mWidth: Int
        // Determine handler position, default is right (when scrolling vertically) or bottom (when scrolling horizontally)
        if (pdfView.isSwipeVertical()) {
            mWidth = HANDLE_LONG
            mHeight = HANDLE_SHORT
            if (inverted) { // left
                mAlign = ALIGN_PARENT_LEFT
                mBackground =
                    ContextCompat.getDrawable(context, R.drawable.default_scroll_handle_left)
            } else { // right
                mAlign = ALIGN_PARENT_RIGHT
                mBackground =
                    ContextCompat.getDrawable(context, R.drawable.default_scroll_handle_right)
            }
        } else {
            mWidth = HANDLE_SHORT
            mHeight = HANDLE_LONG
            if (inverted) { // top
                mAlign = ALIGN_PARENT_TOP
                mBackground =
                    ContextCompat.getDrawable(context, R.drawable.default_scroll_handle_top)
            } else { // bottom
                mAlign = ALIGN_PARENT_BOTTOM
                mBackground =
                    ContextCompat.getDrawable(context, R.drawable.default_scroll_handle_bottom)
            }
        }
        background = mBackground
        val lp = LayoutParams(PdfUtils.getDP(context, mWidth), PdfUtils.getDP(context, mHeight))
        lp.setMargins(0, 0, 0, 0)
        val tvLp =
            LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        tvLp.addRule(CENTER_IN_PARENT, TRUE)
        addView(mTextView, tvLp)
        lp.addRule(mAlign)
        pdfView.addView(this, lp)
        this.mPdfView = pdfView
    }

    override fun destroyLayout() {
        mPdfView?.removeView(this)
    }

    override fun setScroll(position: Float) {
        if (!shown()) show() else handler.removeCallbacks(mHidePageScroller)
        if (mPdfView != null) {
            val mPos = if (mPdfView!!.isSwipeVertical()) mPdfView!!.height else mPdfView!!.width
            setPosition(mPos * position)
        }
    }

    private fun setPosition(position: Float) {
        var mPosition: Float = position
        if (isInfinite(mPosition) || isNaN(mPosition)) {
            return
        }
        val pdfViewSize: Float = if (mPdfView!!.isSwipeVertical()) {
            mPdfView!!.height.toFloat()
        } else {
            mPdfView!!.width.toFloat()
        }
        mPosition -= mHandlerMiddle
        if (mPosition < 0) {
            mPosition = 0f
        } else if (mPosition > pdfViewSize - PdfUtils.getDP(context, HANDLE_SHORT)) {
            mPosition = pdfViewSize - PdfUtils.getDP(context, HANDLE_SHORT)
        }
        if (mPdfView!!.isSwipeVertical()) y = mPosition else x = mPosition
        calculateMiddle()
        invalidate()
    }

    private fun calculateMiddle() {
        val mPdfViewSize: Float
        val mPos: Float
        val mViewSize: Float
        if (mPdfView!!.isSwipeVertical()) {
            mPos = y
            mViewSize = height.toFloat()
            mPdfViewSize = mPdfView!!.height.toFloat()
        } else {
            mPos = x
            mViewSize = width.toFloat()
            mPdfViewSize = mPdfView!!.width.toFloat()
        }
        mHandlerMiddle = (mPos + mHandlerMiddle) / mPdfViewSize * mViewSize
    }

    override fun hideDelayed() {
        handler.postDelayed(mHidePageScroller, 1000)
    }

    override fun setPageNumber(pageNumber: Int) {
        val mPageNumber = pageNumber.toString()
        if (mTextView.text != mPageNumber) {
            mTextView.text = mPageNumber
        }
    }

    override fun shown(): Boolean {
        return visibility == VISIBLE
    }

    override fun show() {
        visibility = VISIBLE
    }

    override fun hide() {
        visibility = INVISIBLE
    }

    fun setTextColor(color: Int) {
        mTextView.setTextColor(color)
    }

    /**
     * @param size text size in dp
     */
    fun setTextSize(size: Int) {
        mTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, size.toFloat())
    }

    private val isPDFViewReady: Boolean
        get() = mPdfView != null && mPdfView!!.getPageCount() > 0 && mPdfView!!.documentFitsView()

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isPDFViewReady) {
            return super.onTouchEvent(event)
        }
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                mPdfView!!.stopFling()
                handler.removeCallbacks(mHidePageScroller)
                mCurrentPosition = if (mPdfView!!.isSwipeVertical()) {
                    event.rawY - y
                } else {
                    event.rawX - x
                }
                if (mPdfView!!.isSwipeVertical()) {
                    setPosition(event.rawY - mCurrentPosition + mHandlerMiddle)
                    mPdfView!!.setPositionOffset(mHandlerMiddle / height.toFloat(), false)
                } else {
                    setPosition(event.rawX - mCurrentPosition + mHandlerMiddle)
                    mPdfView!!.setPositionOffset(mHandlerMiddle / width.toFloat(), false)
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (mPdfView!!.isSwipeVertical()) {
                    setPosition(event.rawY - mCurrentPosition + mHandlerMiddle)
                    mPdfView!!.setPositionOffset(mHandlerMiddle / height.toFloat(), false)
                } else {
                    setPosition(event.rawX - mCurrentPosition + mHandlerMiddle)
                    mPdfView!!.setPositionOffset(mHandlerMiddle / width.toFloat(), false)
                }
                return true
            }
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                hideDelayed()
                mPdfView!!.performPageSnap()
                return true
            }
        }
        performClick()
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        return super.performClick()
    }

    companion object {
        private const val DEFAULT_TEXT_SIZE = 16
        private const val HANDLE_LONG = 65
        private const val HANDLE_SHORT = 40
    }

    init {
        visibility = INVISIBLE
        setTextColor(Color.BLACK)
        setTextSize(DEFAULT_TEXT_SIZE)
    }
}