package com.ahmer.pdfviewer.scroll

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import android.view.MotionEvent
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.ahmer.pdfviewer.PDFView
import com.ahmer.pdfviewer.R
import com.ahmer.pdfviewer.util.PdfUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

/**
 * Default scroll handle implementation for PDFView navigation.
 *
 * @property context Android context for resource access
 * @property inverted Controls handle orientation (default false = right/bottom placement)
 */
class DefaultScrollHandle @JvmOverloads constructor(
    private val context: Context,
    private val inverted: Boolean = false,
) : RelativeLayout(context), ScrollHandle {
    private val coroutineScope: CoroutineScope = CoroutineScope(context = Dispatchers.Main)
    private val pageNumberText: TextView = TextView(context)
    private var autoHideJob: Job? = null
    private var currentTouchPosition: Float = 0f
    private var handleCenterOffset: Float = 0f
    private var pdfView: PDFView? = null

    override fun setupLayout(pdfView: PDFView) {
        val isVertical: Boolean = pdfView.isSwipeVertical
        val width: Int = if (isVertical) HANDLE_LONG else HANDLE_SHORT
        val height: Int = if (isVertical) HANDLE_SHORT else HANDLE_LONG
        val align: Int = when {
            isVertical && inverted -> ALIGN_PARENT_LEFT
            isVertical -> ALIGN_PARENT_RIGHT
            inverted -> ALIGN_PARENT_TOP
            else -> ALIGN_PARENT_BOTTOM
        }
        val backgroundRes: Int = when {
            isVertical && inverted -> R.drawable.default_scroll_handle_left
            isVertical -> R.drawable.default_scroll_handle_right
            inverted -> R.drawable.default_scroll_handle_top
            else -> R.drawable.default_scroll_handle_bottom
        }
        background = ContextCompat.getDrawable(context, backgroundRes)
        LayoutParams(
            PdfUtils.getDP(context = context, dp = width),
            PdfUtils.getDP(context = context, dp = height)
        ).apply {
            addRule(align)
            pdfView.addView(this@DefaultScrollHandle, this)
        }
        this.pdfView = pdfView
    }

    override fun destroyLayout() {
        pdfView?.removeView(this@DefaultScrollHandle)
        coroutineScope.cancel()
    }

    override fun setScroll(position: Float) {
        if (!shown()) show() else autoHideJob?.cancel()
        pdfView?.let {
            val viewSize: Int = if (it.isSwipeVertical) it.height else it.width
            setPosition(viewSize * position)
        }
    }

    private fun setPosition(position: Float) {
        if (position.isInfinite() || position.isNaN()) return
        pdfView?.let { v ->
            val viewSize: Float = if (v.isSwipeVertical) v.height.toFloat() else v.width.toFloat()
            val maxPosition: Float = viewSize - PdfUtils.getDP(context = context, dp = HANDLE_SHORT)
            val adjustedPosition: Float = (position - handleCenterOffset).coerceIn(
                minimumValue = 0f,
                maximumValue = maxPosition
            )

            if (v.isSwipeVertical) y = adjustedPosition else x = adjustedPosition
            handleCenterOffset = if (v.isSwipeVertical) {
                (y + handleCenterOffset) / viewSize * height
            } else {
                (x + handleCenterOffset) / viewSize * width
            }
            invalidate()
        }
    }

    override fun hideDelayed() {
        autoHideJob?.cancel()
        autoHideJob = coroutineScope.launch {
            delay(duration = 1.seconds)
            hide()
        }
    }

    override fun setPageNumber(pageNumber: Int) {
        val page: String = pageNumber.toString()
        if (pageNumberText.text != page) pageNumberText.text = page
    }

    override fun shown(): Boolean {
        return isVisible
    }

    override fun show() {
        visibility = VISIBLE
    }

    override fun hide() {
        visibility = INVISIBLE
    }

    fun setTextColor(color: Int) {
        pageNumberText.setTextColor(color)
    }

    /**
     * @param size text size in dp
     */
    fun setTextSize(size: Int) {
        pageNumberText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, size.toFloat())
    }

    private val isPDFViewReady: Boolean
        get() = pdfView?.let { it.pagesCount > 0 && !it.documentFitsView() } ?: false

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isPDFViewReady) return super.onTouchEvent(event)

        pdfView?.let { view ->
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                    view.stopFling()
                    autoHideJob?.cancel()
                    currentTouchPosition = if (view.isSwipeVertical) event.rawY - y else event.rawX - x
                    val position: Float = if (view.isSwipeVertical) {
                        event.rawY - currentTouchPosition + handleCenterOffset
                    } else {
                        event.rawX - currentTouchPosition + handleCenterOffset
                    }
                    setPosition(position = position)
                    view.setPositionOffset(
                        progress = if (view.isSwipeVertical) handleCenterOffset / height else handleCenterOffset / width,
                        moveHandle = false
                    )
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    val position: Float = if (view.isSwipeVertical) {
                        event.rawY - currentTouchPosition + handleCenterOffset
                    } else {
                        event.rawX - currentTouchPosition + handleCenterOffset
                    }
                    setPosition(position = position)
                    view.setPositionOffset(
                        progress = if (view.isSwipeVertical) handleCenterOffset / height else handleCenterOffset / width,
                        moveHandle = false
                    )
                    return true
                }

                MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                    hideDelayed()
                    view.performPageSnap()
                    return true
                }

                else -> {
                    return super.onTouchEvent(event)
                }
            }
        }
        return super.onTouchEvent(event)
    }

    companion object {
        private const val DEFAULT_TEXT_SIZE: Int = 16
        private const val HANDLE_LONG: Int = 65
        private const val HANDLE_SHORT: Int = 40
    }

    init {
        visibility = INVISIBLE
        setTextColor(color = Color.BLACK)
        setTextSize(size = DEFAULT_TEXT_SIZE)
        addView(pageNumberText, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            addRule(CENTER_IN_PARENT, TRUE)
        })
    }
}