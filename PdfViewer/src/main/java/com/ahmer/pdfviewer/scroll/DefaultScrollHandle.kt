package com.ahmer.pdfviewer.scroll

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
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
    @get:JvmName("getAdapterContext") val context: Context,
    private val inverted: Boolean = false
) : RelativeLayout(context), ScrollHandle {

    companion object {
        private const val HANDLE_LONG = 65
        private const val HANDLE_SHORT = 40
        private const val DEFAULT_TEXT_SIZE = 16
    }

    private val textView: TextView = TextView(context)
    private val hidePageScrollerRunnable = Runnable { hide() }
    private var relativeHandlerMiddle = 0f
    private var pdfView: PDFView? = null
    private var currentPos = 0f

    override fun setupLayout(pdfView: PDFView?) {
        var align: Int = 0
        var width: Int = 0
        var height: Int = 0
        var background: Drawable? = null
        // determine handler position, default is right (when scrolling vertically) or bottom (when scrolling horizontally)
        if (pdfView != null) {
            if (pdfView.isSwipeVertical) {
                width = HANDLE_LONG
                height = HANDLE_SHORT
                if (inverted) { // left
                    align = ALIGN_PARENT_LEFT
                    background =
                        ContextCompat.getDrawable(context, R.drawable.default_scroll_handle_left)
                } else { // right
                    align = ALIGN_PARENT_RIGHT
                    background =
                        ContextCompat.getDrawable(context, R.drawable.default_scroll_handle_right)
                }
            } else {
                width = HANDLE_SHORT
                height = HANDLE_LONG
                if (inverted) { // top
                    align = ALIGN_PARENT_TOP
                    background =
                        ContextCompat.getDrawable(context, R.drawable.default_scroll_handle_top)
                } else { // bottom
                    align = ALIGN_PARENT_BOTTOM
                    background =
                        ContextCompat.getDrawable(context, R.drawable.default_scroll_handle_bottom)
                }
            }
        }
        setBackground(background)
        val lp = LayoutParams(PdfUtils.getDP(context, width), PdfUtils.getDP(context, height))
        lp.setMargins(0, 0, 0, 0)
        val tvLp =
            LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        tvLp.addRule(CENTER_IN_PARENT, TRUE)
        addView(textView, tvLp)
        lp.addRule(align)
        pdfView?.addView(this, lp)
        this.pdfView = pdfView
    }

    override fun destroyLayout() {
        pdfView?.removeView(this)
    }

    override fun setScroll(position: Float) {
        if (!shown()) {
            show()
        } else {
            handler.removeCallbacks(hidePageScrollerRunnable)
        }
        if (pdfView != null) {
            setPosition((if (pdfView!!.isSwipeVertical) pdfView!!.height else pdfView!!.width) * position)
        }
    }

    private fun setPosition(pos: Float) {
        var newPos = pos
        if (isInfinite(newPos) || isNaN(newPos)) {
            return
        }
        val pdfViewSize: Int? = if (pdfView?.isSwipeVertical == true) {
            pdfView!!.height
        } else {
            pdfView?.width
        }
        newPos -= relativeHandlerMiddle
        if (newPos < 0) {
            newPos = 0f
        } else if (pdfViewSize != null) {
            if (newPos > pdfViewSize - PdfUtils.getDP(context, HANDLE_SHORT)) {
                newPos = (pdfViewSize - PdfUtils.getDP(context, HANDLE_SHORT)).toFloat()
            }
        }
        if (pdfView?.isSwipeVertical == true) {
            y = newPos
        } else {
            x = newPos
        }
        calculateMiddle()
        invalidate()
    }

    private fun calculateMiddle() {
        val pos: Float
        val viewSize: Float
        val pdfViewSize: Float
        if (pdfView?.isSwipeVertical == true) {
            pos = y
            viewSize = height.toFloat()
            pdfViewSize = pdfView?.height?.toFloat()!!
        } else {
            pos = x
            viewSize = width.toFloat()
            pdfViewSize = pdfView?.width?.toFloat()!!
        }
        relativeHandlerMiddle = (pos + relativeHandlerMiddle) / pdfViewSize * viewSize
    }

    override fun hideDelayed() {
        handler.postDelayed(hidePageScrollerRunnable, 1000)
    }

    override fun setPageNum(pageNum: Int) {
        val text = pageNum.toString()
        if (textView.text != text) {
            textView.text = text
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
        textView.setTextColor(color)
    }

    /**
     * @param size text size in dp
     */
    fun setTextSize(size: Int) {
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, size.toFloat())
    }

    fun isPDFViewReady(): Boolean {
        return pdfView != null && pdfView!!.getPageCount() > 0 && !pdfView!!.documentFitsView()
    }


    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isPDFViewReady()) {
            return super.onTouchEvent(event)
        }
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                pdfView?.stopFling()
                handler.removeCallbacks(hidePageScrollerRunnable)
                currentPos = if (pdfView?.isSwipeVertical == true) {
                    event.rawY - y
                } else {
                    event.rawX - x
                }
                if (pdfView?.isSwipeVertical == true) {
                    setPosition(event.rawY - currentPos + relativeHandlerMiddle)
                    pdfView!!.setPositionOffset(relativeHandlerMiddle / height.toFloat(), false)
                } else {
                    setPosition(event.rawX - currentPos + relativeHandlerMiddle)
                    pdfView?.setPositionOffset(relativeHandlerMiddle / width.toFloat(), false)
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (pdfView?.isSwipeVertical == true) {
                    setPosition(event.rawY - currentPos + relativeHandlerMiddle)
                    pdfView!!.setPositionOffset(relativeHandlerMiddle / height.toFloat(), false)
                } else {
                    setPosition(event.rawX - currentPos + relativeHandlerMiddle)
                    pdfView?.setPositionOffset(relativeHandlerMiddle / width.toFloat(), false)
                }
                return true
            }
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                hideDelayed()
                pdfView?.performPageSnap()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    init {
        visibility = INVISIBLE
        setTextColor(Color.BLACK)
        setTextSize(DEFAULT_TEXT_SIZE)
    }
}

