package com.ahmer.pdfviewer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PaintFlagsDrawFilter
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.os.HandlerThread
import android.util.AttributeSet
import android.util.Log
import android.widget.RelativeLayout
import com.ahmer.pdfium.PdfDocument
import com.ahmer.pdfium.PdfiumCore
import com.ahmer.pdfium.util.Size
import com.ahmer.pdfium.util.SizeF
import com.ahmer.pdfviewer.exception.PageRenderingException
import com.ahmer.pdfviewer.link.DefaultLinkHandler
import com.ahmer.pdfviewer.link.LinkHandler
import com.ahmer.pdfviewer.listener.Callbacks
import com.ahmer.pdfviewer.listener.OnDrawListener
import com.ahmer.pdfviewer.listener.OnErrorListener
import com.ahmer.pdfviewer.listener.OnLoadCompleteListener
import com.ahmer.pdfviewer.listener.OnLongPressListener
import com.ahmer.pdfviewer.listener.OnPageChangeListener
import com.ahmer.pdfviewer.listener.OnPageErrorListener
import com.ahmer.pdfviewer.listener.OnPageScrollListener
import com.ahmer.pdfviewer.listener.OnRenderListener
import com.ahmer.pdfviewer.listener.OnTapListener
import com.ahmer.pdfviewer.model.PagePart
import com.ahmer.pdfviewer.scroll.ScrollHandle
import com.ahmer.pdfviewer.source.AssetSource
import com.ahmer.pdfviewer.source.ByteArraySource
import com.ahmer.pdfviewer.source.DocumentSource
import com.ahmer.pdfviewer.source.FileSource
import com.ahmer.pdfviewer.source.InputStreamSource
import com.ahmer.pdfviewer.source.UriSource
import com.ahmer.pdfviewer.util.FitPolicy
import com.ahmer.pdfviewer.util.MathUtils
import com.ahmer.pdfviewer.util.PdfConstants
import com.ahmer.pdfviewer.util.PdfUtils
import com.ahmer.pdfviewer.util.ScrollDir
import com.ahmer.pdfviewer.util.SnapEdge
import com.ahmer.pdfviewer.util.State
import java.io.File
import java.io.InputStream

/**
 * It supports animations, zoom, cache, and swipe.
 *
 * To fully understand this class you must know its principles :
 * - The PDF document is seen as if we always want to draw all the pages.
 * - The thing is that we only draw the visible parts.
 * - All parts are the same size, this is because we can't interrupt a native page rendering,
 * so we need these renderings to be as fast as possible, and be able to interrupt them
 * as soon as we can.
 * - The parts are loaded when the current offset or the current zoom level changes
 *
 * Important :
 * - DocumentPage = A page of the PDF document.
 * - UserPage = A page as defined by the user.
 * By default, they're the same. But the user can change the pages order
 * using [.load]. In this
 * particular case, a userPage of 5 can refer to a documentPage of 17.
 */
class PDFView(context: Context?, set: AttributeSet?) : RelativeLayout(context, set) {
    private val mAntialiasFilter =
        PaintFlagsDrawFilter(0, Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    /**
     * Pages numbers used when calling onDrawAllListener
     */
    private val onDrawPagesNumber: MutableList<Int> = ArrayList(10)

    /**
     * True if annotations should be rendered
     * False otherwise
     */
    private var isAnnotation: Boolean = false

    /**
     * Add dynamic spacing to fit each page separately on the screen.
     */
    private var isAutoSpacing: Boolean = false

    /**
     * True if bitmap should use ARGB_8888 format and take more memory
     * False if bitmap should be compressed by using RGB_565 format and take less memory
     */
    private var isBestQuality: Boolean = false
    private var isDoubleTapEnabled: Boolean = true

    /**
     * Antialiasing and bitmap filtering
     */
    private var isEnableAntialiasing: Boolean = true
    private var isEnableSwipe: Boolean = true
    private var isFitEachPage: Boolean = false

    /**
     * Holds info whether view has been added to layout and has width and height
     */
    private var isHasSize: Boolean = false
    private var isNightMode: Boolean = false

    /**
     * Fling a single page at a time
     */
    private var isPageFling: Boolean = true
    private var isPageSnap: Boolean = true

    /**
     * True if the PDFView has been recycled
     */
    private var isRecycled: Boolean = true

    /**
     * True if the view should render during scaling
     * False otherwise
     */
    private var isRenderDuringScale: Boolean = false
    private var isScrollHandleInit: Boolean = false

    /**
     * True if should scroll through pages vertically instead of horizontally
     */
    private var isSwipeVertical: Boolean = true

    /**
     * Animation manager manage all offset and zoom animation
     */
    private var mAnimationManager: AnimationManager? = null

    /**
     * If you picture all the pages side by side in their optimal width and taking into account the
     * zoom level, the current offset is the position of the left border of the screen in this
     * big picture
     */
    private var mCurrentOffsetX: Float = 0f

    /**
     * If you picture all the pages side by side in their optimal width, and taking into account the
     * zoom level, the current offset is the position of the left border of the screen in this
     * big picture
     */
    private var mCurrentOffsetY: Float = 0f

    /**
     * The index of the current sequence
     */
    var mCurrentPage: Int = 0

    /**
     * Paint object for drawing debug stuff
     */
    private var mDebugPaint: Paint? = null
    private var mDecodingTask: DecodingTask? = null
    private var mDefaultPage: Int = 0

    /**
     * Drag manager manage all touch events
     */
    private var mDragPinchManager: DragPinchManager? = null
    private var mMaxZoom: Float = DEFAULT_MAX_SCALE
    private var mMidZoom: Float = DEFAULT_MID_SCALE
    private var mMinZoom: Float = DEFAULT_MIN_SCALE

    /**
     * Policy for fitting pages to screen
     */
    private var mPageFitPolicy: FitPolicy = FitPolicy.WIDTH
    private var mPagesLoader: PagesLoader? = null

    /**
     * Paint object for drawing
     */
    private var mPaint: Paint? = null
    var pdfiumCore: PdfiumCore? = null

    /**
     * The thread [.renderingHandler] will run on
     */
    private var mRenderingHandlerThread: HandlerThread? = null
    private var mScrollDir = ScrollDir.NONE
    private var mScrollHandle: ScrollHandle? = null

    /**
     * Spacing between pages, in px
     */
    private var mSpacingPx: Int = 0

    /**
     * Current state of the view
     */
    private var mState: State = State.DEFAULT

    /**
     * Holds last used Configurator that should be loaded when view has size
     */
    private var mWaitingDocumentConfigurator: Configurator? = null

    /**
     * The zoom level, always >= 1
     */
    private var mZoom: Float = 1f

    /**
     * Rendered parts go to the cache manager
     */
    var cacheManager: CacheManager? = null
    var callbacks: Callbacks = Callbacks()
    var pdfFile: PdfFile? = null

    /**
     * Handler always waiting in the background and rendering tasks
     */
    var renderingHandler: RenderingHandler? = null

    /**
     * Checks if whole document can be displayed on screen, doesn't include zoom
     * @return true if whole document can displayed at once, false otherwise
     */
    fun documentFitsView(): Boolean {
        val mLength: Float = pdfFile!!.getDocLen(1f)
        return if (isSwipeVertical) mLength < height else mLength < width
    }

    /**
     * Draw a given PagePart on the canvas
     */
    private fun drawPart(canvas: Canvas, part: PagePart) {
        // Can seem strange, but avoid lot of calls
        val mPageRelativeBounds: RectF = part.pageRelativeBounds
        val mRenderedBitmap: Bitmap? = part.renderedBitmap
        if (mRenderedBitmap != null && mRenderedBitmap.isRecycled) return
        // Move to the target page
        val mLocalTranslationX: Float
        val mLocalTranslationY: Float
        val mSize = pdfFile!!.getPageSize(part.page)
        if (isSwipeVertical) {
            mLocalTranslationY = pdfFile!!.getPageOffset(part.page, mZoom)
            val mMaxWidth: Float = pdfFile!!.maxPageWidth
            mLocalTranslationX = toCurrentScale(mMaxWidth - mSize.width) / 2
        } else {
            mLocalTranslationX = pdfFile!!.getPageOffset(part.page, mZoom)
            val mMaxHeight: Float = pdfFile!!.maxPageHeight
            mLocalTranslationY = toCurrentScale(mMaxHeight - mSize.height) / 2
        }
        canvas.translate(mLocalTranslationX, mLocalTranslationY)
        val mHeight: Float = toCurrentScale(mPageRelativeBounds.height() * mSize.height)
        val mWidth: Float = toCurrentScale(mPageRelativeBounds.width() * mSize.width)
        val mOffsetX: Float = toCurrentScale(mPageRelativeBounds.left * mSize.width)
        val mOffsetY: Float = toCurrentScale(mPageRelativeBounds.top * mSize.height)
        val mSrcRect: Rect? = mRenderedBitmap?.let { Rect(0, 0, it.width, it.height) }
        val mTranslationX: Float = mCurrentOffsetX + mLocalTranslationX
        val mTranslationY: Float = mCurrentOffsetY + mLocalTranslationY
        // If we use float values for this rectangle, there will be a possible gap between page
        // parts, especially when the zoom level is high.
        val mDstRect = RectF(mOffsetX, mOffsetY, (mOffsetX + mWidth), (mOffsetY + mHeight))
        // Check if bitmap is in the screen
        if (mTranslationX + mDstRect.left >= width || mTranslationX + mDstRect.right <= 0 || mTranslationY + mDstRect.top >= height || mTranslationY + mDstRect.bottom <= 0) {
            canvas.translate(-mLocalTranslationX, -mLocalTranslationY)
            return
        }
        canvas.drawBitmap(mRenderedBitmap!!, mSrcRect, mDstRect, mPaint)
        if (PdfConstants.DEBUG_MODE) {
            mDebugPaint?.color = if (part.page % 2 == 0) Color.RED else Color.BLUE
            canvas.drawRect(mDstRect, mDebugPaint!!)
        }
        // Restore the canvas position
        canvas.translate(-mLocalTranslationX, -mLocalTranslationY)
    }

    private fun drawWithListener(canvas: Canvas, page: Int, listener: OnDrawListener?) {
        if (listener != null) {
            val mTranslateX: Float
            val mTranslateY: Float
            if (isSwipeVertical) {
                mTranslateX = 0f
                mTranslateY = pdfFile!!.getPageOffset(page, mZoom)
            } else {
                mTranslateY = 0f
                mTranslateX = pdfFile!!.getPageOffset(page, mZoom)
            }
            canvas.translate(mTranslateX, mTranslateY)
            val mSize: SizeF = pdfFile!!.getPageSize(page)
            listener.onLayerDrawn(
                canvas, toCurrentScale(mSize.width), toCurrentScale(mSize.height), page
            )
            canvas.translate(-mTranslateX, -mTranslateY)
        }
    }

    fun findFocusPage(xOffset: Float, yOffset: Float): Int {
        val mCurrentOffset: Float = if (isSwipeVertical) yOffset else xOffset
        val mLength: Float = if (isSwipeVertical) height.toFloat() else width.toFloat()
        // Make sure first and last page can be found
        if (mCurrentOffset > -1) {
            return 0
        } else if (mCurrentOffset < -pdfFile!!.getDocLen(mZoom) + mLength + 1) {
            return pdfFile!!.pagesCount - 1
        }
        // Else find page in center
        val mCenter: Float = mCurrentOffset - mLength / 2f
        return pdfFile!!.getPageAtOffset(-mCenter, mZoom)
    }

    /**
     * Find the edge to snap to when showing the specified page
     */
    fun findSnapEdge(page: Int): SnapEdge {
        if (!isPageSnap || page < 0) return SnapEdge.NONE
        val mCurrentOffset: Float = if (isSwipeVertical) mCurrentOffsetY else mCurrentOffsetX
        val mLength: Int = if (isSwipeVertical) height else width
        val mOffset: Float = -pdfFile!!.getPageOffset(page, mZoom)
        val mPageLength: Float = pdfFile!!.getPageLength(page, mZoom)
        return when {
            mLength >= mPageLength -> {
                SnapEdge.CENTER
            }

            mCurrentOffset >= mOffset -> {
                SnapEdge.START
            }

            mOffset - mPageLength > mCurrentOffset - mLength -> {
                SnapEdge.END
            }

            else -> {
                SnapEdge.NONE
            }
        }
    }

    fun fitToWidth(page: Int) {
        if (mState != State.SHOWN) {
            Log.v(PdfConstants.TAG, "Cannot fit, document not rendered yet")
            return
        }
        zoomTo(width / pdfFile!!.getPageSize(page).width)
        jumpTo(page)
    }

    fun getCurrentPage(): Int {
        return mCurrentPage
    }

    fun getCurrentXOffset(): Float {
        return mCurrentOffsetX
    }

    fun getCurrentYOffset(): Float {
        return mCurrentOffsetY
    }

    /**
     * Returns null if document is not loaded
     */
    fun documentMeta(): PdfDocument.Meta? {
        return pdfFile?.metaData()
    }

    fun getMaxZoom(): Float {
        return mMaxZoom
    }

    fun getMidZoom(): Float {
        return mMidZoom
    }

    fun getMinZoom(): Float {
        return mMinZoom
    }

    /**
     * Get page number at given offset
     *
     * @param positionOffset scroll offset between 0 and 1
     * @return page number at given offset, starting from 0
     */
    fun getPageAtPositionOffset(positionOffset: Float): Int {
        return pdfFile!!.getPageAtOffset(pdfFile!!.getDocLen(mZoom) * positionOffset, mZoom)
    }

    fun getPageCount(): Int {
        return if (pdfFile == null) 0 else pdfFile!!.pagesCount
    }

    fun getPageFitPolicy(): FitPolicy {
        return mPageFitPolicy
    }

    /**
     * Get the page rotation
     * @param pageIndex the page index
     * @return the rotation
     */
    fun getPageRotation(pageIndex: Int): Int {
        return pdfFile?.getPageRotation(pageIndex)!!
    }

    fun getPageSize(pageIndex: Int): SizeF {
        return if (pdfFile == null) SizeF(0f, 0f) else pdfFile!!.getPageSize(pageIndex)
    }

    /**
     * Get current position as ratio of document length to visible area.
     * 0 means that document start is visible, 1 that document end is visible
     * @return offset between 0 and 1
     */
    fun getPositionOffset(): Float {
        val offset: Float = if (isSwipeVertical) {
            -mCurrentOffsetY / (pdfFile!!.getDocLen(mZoom) - height)
        } else {
            -mCurrentOffsetX / (pdfFile!!.getDocLen(mZoom) - width)
        }
        return MathUtils.limit(offset, 0f, 1f)
    }

    fun getScrollHandle(): ScrollHandle? {
        return mScrollHandle
    }

    fun getSpacingPx(): Int {
        return mSpacingPx
    }

    /**
     * Will be empty until document is loaded
     */
    fun bookmarks(): List<PdfDocument.Bookmark> {
        return pdfFile?.bookmarks() ?: emptyList()
    }

    fun pagesCount(): Int {
        return pdfFile?.totalPagesCount() ?: 0
    }

    fun getZoom(): Float {
        return mZoom
    }

    fun isAnnotationRendering(): Boolean {
        return isAnnotation
    }

    fun isAntialiasing(): Boolean {
        return isEnableAntialiasing
    }

    fun isAutoSpacingEnabled(): Boolean {
        return isAutoSpacing
    }

    fun isBestQuality(): Boolean {
        return isBestQuality
    }

    fun isDoubleTapEnabled(): Boolean {
        return isDoubleTapEnabled
    }

    fun isFitEachPage(): Boolean {
        return isFitEachPage
    }

    fun isNightMode(): Boolean {
        return isNightMode
    }

    fun isPageFlingEnabled(): Boolean {
        return isPageFling
    }

    fun isPageSnap(): Boolean {
        return isPageSnap
    }

    fun isRecycled(): Boolean {
        return isRecycled
    }

    fun isRenderDuringScale(): Boolean {
        return isRenderDuringScale
    }

    fun isSwipeEnabled(): Boolean {
        return isEnableSwipe
    }

    fun isSwipeVertical(): Boolean {
        return isSwipeVertical
    }

    fun isZooming(): Boolean {
        return mZoom != mMinZoom
    }

    /**
     * Go to the given page.
     * @param page Page index.
     */
    @JvmOverloads
    fun jumpTo(page: Int, withAnimation: Boolean = false) {
        var mPage = page
        if (pdfFile == null) return
        mPage = pdfFile!!.determineValidPageNumberFrom(mPage)
        var mOffset: Float = if (mPage == 0) 0f else -pdfFile!!.getPageOffset(mPage, mZoom)
        mOffset += pdfFile!!.getPageSpacing(mPage, mZoom) / 2f
        if (isSwipeVertical) {
            if (withAnimation) {
                mAnimationManager?.startYAnimation(mCurrentOffsetY, mOffset)
            } else {
                moveTo(mCurrentOffsetX, mOffset)
            }
        } else {
            if (withAnimation) {
                mAnimationManager?.startXAnimation(mCurrentOffsetX, mOffset)
            } else {
                moveTo(mOffset, mCurrentOffsetY)
            }
        }
        showPage(mPage)
    }

    private fun load(docSource: DocumentSource, password: String?, userPages: IntArray? = null) {
        check(value = isRecycled) { "Don't call load on a PDF View without recycling it first." }
        isRecycled = false
        // Start decoding document
        mDecodingTask = pdfiumCore?.let { DecodingTask(docSource, password, userPages, this) }
        mDecodingTask?.execute()
    }

    /**
     * Called when the PDF is loaded
     */
    fun loadComplete(pdfFile: PdfFile) {
        mState = State.LOADED
        this.pdfFile = pdfFile
        if (mRenderingHandlerThread == null) return
        if (mRenderingHandlerThread?.isAlive != true) mRenderingHandlerThread?.start()
        renderingHandler = mRenderingHandlerThread?.looper?.let {
            RenderingHandler(it, this)
        }
        renderingHandler?.start()
        if (mScrollHandle != null) {
            mScrollHandle?.setupLayout(this)
            isScrollHandleInit = true
        }
        mDragPinchManager?.enable()
        callbacks.callOnLoadComplete(pdfFile.pagesCount)
        jumpTo(mDefaultPage, false)
    }

    fun loadError(t: Throwable?) {
        mState = State.ERROR
        // Store reference, because callbacks will be cleared in recycle() method
        val onErrorListener = callbacks.onError
        recycle()
        invalidate()
        onErrorListener?.onError(t) ?: Log.e(PdfConstants.TAG, "Load PDF error: ", t)
    }

    fun loadPageByOffset() {
        if (pdfFile == null || pdfFile?.pagesCount == 0) return
        val mOffset: Float
        val mCenterOfScreen: Float
        if (isSwipeVertical) {
            mOffset = mCurrentOffsetY
            mCenterOfScreen = height.toFloat() / 2
        } else {
            mOffset = mCurrentOffsetX
            mCenterOfScreen = width.toFloat() / 2
        }
        val mPage: Int = pdfFile!!.getPageAtOffset(-(mOffset - mCenterOfScreen), mZoom)
        if (mPage >= 0 && mPage <= pdfFile!!.pagesCount - 1 && mPage != getCurrentPage()) {
            showPage(mPage)
        } else {
            loadPages()
        }
    }

    /**
     * Load all the parts around the center of the screen,
     * taking into account X and Y offsets, zoom level, and
     * the current page displayed
     */
    fun loadPages() {
        if (pdfFile == null || renderingHandler == null) return
        // Cancel all current tasks
        renderingHandler?.removeMessages(RenderingHandler.MSG_RENDER_PART_TASK)
        cacheManager?.makeANewSet()
        mPagesLoader?.loadPages()
        reDraw()
    }

    /**
     * Move relatively to the current position.
     *
     * @param dx The X difference you want to apply.
     * @param dy The Y difference you want to apply.
     * @see .moveTo
     */
    fun moveRelativeTo(dx: Float, dy: Float) {
        moveTo(mCurrentOffsetX + dx, mCurrentOffsetY + dy)
    }

    /**
     * Move to the given X and Y offsets, but check them ahead of time
     * to be sure not to go outside the the big strip.
     *
     * @param offsetX    The big strip X offset to use as the left border of the screen.
     * @param offsetY    The big strip Y offset to use as the right border of the screen.
     * @param moveHandle whether to move scroll handle or not
     */

    @JvmOverloads
    fun moveTo(offsetX: Float, offsetY: Float, moveHandle: Boolean = true) {
        var mOffsetX: Float = offsetX
        var mOffsetY: Float = offsetY
        val mPositionOffset: Float = getPositionOffset()
        if (isSwipeVertical) {
            // Check X offset
            val mScaledPageWidth = toCurrentScale(pdfFile!!.maxPageWidth)
            if (mScaledPageWidth < width) {
                mOffsetX = width / 2f - mScaledPageWidth / 2
            } else {
                if (mOffsetX > 0) {
                    mOffsetX = 0f
                } else if (mOffsetX + mScaledPageWidth < width) {
                    mOffsetX = width - mScaledPageWidth
                }
            }

            // Check Y offset
            val mContentHeight = pdfFile!!.getDocLen(mZoom)
            if (mContentHeight < height) { // Whole document height visible on screen
                mOffsetY = (height - mContentHeight) / 2
            } else {
                if (mOffsetY > 0) { // Top visible
                    mOffsetY = 0f
                } else if (mOffsetY + mContentHeight < height) { // bottom visible
                    mOffsetY = -mContentHeight + height
                }
            }
            mScrollDir = when {
                mOffsetY < mCurrentOffsetY -> ScrollDir.END
                mOffsetY > mCurrentOffsetY -> ScrollDir.START
                else -> ScrollDir.NONE
            }
        } else {
            // Check Y offset
            val mScaledPageHeight = toCurrentScale(pdfFile!!.maxPageHeight)
            if (mScaledPageHeight < height) {
                mOffsetY = height / 2f - mScaledPageHeight / 2
            } else {
                if (mOffsetY > 0) {
                    mOffsetY = 0f
                } else if (mOffsetY + mScaledPageHeight < height) {
                    mOffsetY = height - mScaledPageHeight
                }
            }

            // Check X offset
            val mContentWidth = pdfFile!!.getDocLen(mZoom)
            if (mContentWidth < width) { // Whole document width visible on screen
                mOffsetX = (width - mContentWidth) / 2
            } else {
                if (mOffsetX > 0) { // Left visible
                    mOffsetX = 0f
                } else if (mOffsetX + mContentWidth < width) { // Right visible
                    mOffsetX = -mContentWidth + width
                }
            }
            mScrollDir = when {
                mOffsetX < mCurrentOffsetX -> ScrollDir.END
                mOffsetX > mCurrentOffsetX -> ScrollDir.START
                else -> ScrollDir.NONE
            }
        }
        mCurrentOffsetX = mOffsetX
        mCurrentOffsetY = mOffsetY
        if (moveHandle && mScrollHandle != null && !documentFitsView()) {
            mScrollHandle?.setScroll(mPositionOffset)
        }
        callbacks.callOnPageScroll(getCurrentPage(), mPositionOffset)
        reDraw()
        val mPageNo = findFocusPage(mCurrentOffsetX, mCurrentOffsetY)
        if (mPageNo >= 0 && mPageNo < pdfFile!!.pagesCount && mPageNo != getCurrentPage()) {
            showPage(mPageNo)
        }
    }

    /**
     * Called when a rendering task is over and a PagePart has been freshly created.
     * @param part The created PagePart.
     */
    fun onBitmapRendered(part: PagePart) {
        // When it is first rendered part
        if (mState == State.LOADED) {
            mState = State.SHOWN
            callbacks.callOnRender(pdfFile!!.pagesCount)
        }
        if (part.isThumbnail) cacheManager?.cacheThumbnail(part) else cacheManager?.cachePart(part)
        reDraw()
    }

    fun onPageError(ex: PageRenderingException) {
        if (!callbacks.callOnPageError(ex.page, ex.cause)) {
            Log.e(PdfConstants.TAG, "Cannot open page: " + ex.page + " due to: " + ex.cause, ex)
        }
    }

    /**
     * @return true if single page fills the entire screen in the scrolling direction
     */
    fun pageFillsScreen(): Boolean {
        val mStart = -pdfFile!!.getPageOffset(mCurrentPage, mZoom)
        val mEnd = mStart - pdfFile!!.getPageLength(mCurrentPage, mZoom)
        return if (isSwipeVertical()) {
            mStart > mCurrentOffsetY && mEnd < mCurrentOffsetY - height
        } else {
            mStart > mCurrentOffsetX && mEnd < mCurrentOffsetX - width
        }
    }

    /**
     * Animate to the nearest snapping position for the current SnapPolicy
     */
    fun performPageSnap() {
        if (!isPageSnap || pdfFile == null || pdfFile?.pagesCount == 0) return
        val mCenterPage: Int = findFocusPage(mCurrentOffsetX, mCurrentOffsetY)
        val mEdge: SnapEdge = findSnapEdge(mCenterPage)
        if (mEdge == SnapEdge.NONE) return
        val mOffset: Float = snapOffsetForPage(mCenterPage, mEdge)
        if (isSwipeVertical) {
            mAnimationManager?.startYAnimation(mCurrentOffsetY, -mOffset)
        } else {
            mAnimationManager?.startXAnimation(mCurrentOffsetX, -mOffset)
        }
    }

    fun recycle() {
        mWaitingDocumentConfigurator = null
        mAnimationManager?.stopAll()
        mDragPinchManager?.disable()
        // Stop tasks
        if (renderingHandler != null) {
            renderingHandler?.stop()
            renderingHandler?.removeMessages(RenderingHandler.MSG_RENDER_PART_TASK)
        }
        mDecodingTask?.cancel()
        // Clear caches
        cacheManager!!.recycle()
        if (mScrollHandle != null && isScrollHandleInit) mScrollHandle?.destroyLayout()
        if (pdfFile != null) {
            pdfFile?.dispose()
            pdfFile = null
        }
        renderingHandler = null
        mScrollHandle = null
        isScrollHandleInit = false
        mCurrentOffsetY = 0f
        mCurrentOffsetX = mCurrentOffsetY
        mZoom = 1f
        isRecycled = true
        callbacks = Callbacks()
        mState = State.DEFAULT
    }

    private fun reDraw() {
        invalidate()
    }

    fun resetZoom() {
        zoomTo(mMinZoom)
    }

    fun resetZoomWithAnimation() {
        zoomWithAnimation(mMinZoom)
    }

    fun setAnnotation(isAnnotation: Boolean) {
        this.isAnnotation = isAnnotation
    }

    fun setAntialiasing(isEnableAntialiasing: Boolean) {
        this.isEnableAntialiasing = isEnableAntialiasing
    }

    fun setAutoSpacing(autoSpacing: Boolean) {
        this.isAutoSpacing = autoSpacing
    }

    fun setBestQuality(bestQuality: Boolean) {
        this.isBestQuality = bestQuality
    }

    fun setDefaultPage(defaultPage: Int) {
        this.mDefaultPage = defaultPage
    }

    fun setDoubleTap(isDoubleTapEnabled: Boolean) {
        this.isDoubleTapEnabled = isDoubleTapEnabled
    }

    fun setFitEachPage(fitEachPage: Boolean) {
        this.isFitEachPage = fitEachPage
    }

    fun setMaxZoom(maxZoom: Float) {
        this.mMaxZoom = maxZoom
    }

    fun setMidZoom(midZoom: Float) {
        this.mMidZoom = midZoom
    }

    fun setMinZoom(minZoom: Float) {
        this.mMinZoom = minZoom
    }

    fun setNightMode(nightMode: Boolean) {
        this.isNightMode = nightMode
    }

    fun setPageFitPolicy(pageFitPolicy: FitPolicy) {
        this.mPageFitPolicy = pageFitPolicy
    }

    fun setPageFling(pageFling: Boolean) {
        this.isPageFling = pageFling
    }

    fun setPageSnap(pageSnap: Boolean) {
        this.isPageSnap = pageSnap
    }

    fun setPositionOffset(progress: Float) {
        setPositionOffset(progress, true)
    }

    /**
     * @param progress   must be between 0 and 1
     * @param moveHandle whether to move scroll handle
     * @see PDFView.getPositionOffset
     */
    fun setPositionOffset(progress: Float, moveHandle: Boolean) {
        if (isSwipeVertical) {
            moveTo(mCurrentOffsetX, (-pdfFile!!.getDocLen(mZoom) + height) * progress, moveHandle)
        } else {
            moveTo((-pdfFile!!.getDocLen(mZoom) + width) * progress, mCurrentOffsetY, moveHandle)
        }
        loadPageByOffset()
    }

    fun setRenderDuringScale(isRenderDuringScale: Boolean) {
        this.isRenderDuringScale = isRenderDuringScale
    }

    fun setScrollHandle(scrollHandle: ScrollHandle?) {
        this.mScrollHandle = scrollHandle
    }

    fun setSpacing(spacingDp: Int) {
        mSpacingPx = PdfUtils.getDP(context, spacingDp)
    }

    fun setSwipeEnabled(enableSwipe: Boolean) {
        this.isEnableSwipe = enableSwipe
    }

    fun setSwipeVertical(swipeVertical: Boolean) {
        this.isSwipeVertical = swipeVertical
    }

    fun showPage(pageNumber: Int) {
        var mPageNumber: Int = pageNumber
        if (isRecycled) return
        // Check the page number and makes the difference between UserPages and DocumentPages
        mPageNumber = pdfFile!!.determineValidPageNumberFrom(mPageNumber)
        mCurrentPage = mPageNumber
        loadPages()
        if (mScrollHandle != null && !documentFitsView()) {
            mScrollHandle?.setPageNumber(mCurrentPage + 1)
        }
        callbacks.callOnPageChange(mCurrentPage, pdfFile!!.pagesCount)
    }

    /**
     * Get the offset to move to in order to snap to the page
     */
    fun snapOffsetForPage(pageIndex: Int, edge: SnapEdge): Float {
        var mOffset: Float = pdfFile!!.getPageOffset(pageIndex, mZoom)
        val mLength: Float = if (isSwipeVertical) height.toFloat() else width.toFloat()
        val mPageLength: Float = pdfFile!!.getPageLength(pageIndex, mZoom)
        if (edge == SnapEdge.CENTER) {
            mOffset = mOffset - mLength / 2f + mPageLength / 2f
        } else if (edge == SnapEdge.END) {
            mOffset = mOffset - mLength + mPageLength
        }
        return mOffset
    }

    fun stopFling() {
        mAnimationManager?.stopFling()
    }

    fun toCurrentScale(size: Float): Float {
        return size * mZoom
    }

    fun toRealScale(size: Float): Float {
        return size / mZoom
    }

    /**
     * @see .zoomCenteredTo
     */
    fun zoomCenteredRelativeTo(zoom: Float, pivot: PointF) {
        zoomCenteredTo(mZoom * zoom, pivot)
    }

    /**
     * Change the zoom level, relatively to a pivot point.
     * It will call moveTo() to make sure the given point stays
     * in the middle of the screen.
     * @param zoom  The zoom level.
     * @param pivot The point on the screen that should stays.
     */
    fun zoomCenteredTo(zoom: Float, pivot: PointF) {
        val mZoom: Float = zoom / this.mZoom
        zoomTo(zoom)
        var mBaseX: Float = mCurrentOffsetX * mZoom
        var mBaseY: Float = mCurrentOffsetY * mZoom
        mBaseX += pivot.x - pivot.x * mZoom
        mBaseY += pivot.y - pivot.y * mZoom
        moveTo(mBaseX, mBaseY)
    }

    /**
     * Change the zoom level
     */
    fun zoomTo(zoom: Float) {
        this.mZoom = zoom
    }

    fun zoomWithAnimation(centerX: Float, centerY: Float, scale: Float) {
        mAnimationManager?.startZoomAnimation(centerX, centerY, mZoom, scale)
    }

    fun zoomWithAnimation(scale: Float) {
        mAnimationManager?.startZoomAnimation(width / 2f, height / 2f, mZoom, scale)
    }

    override fun canScrollHorizontally(direction: Int): Boolean {
        if (pdfFile == null) return true
        return if (isSwipeVertical) {
            if (direction < 0 && mCurrentOffsetX < 0) {
                true
            } else direction > 0 && mCurrentOffsetX + toCurrentScale(pdfFile!!.maxPageWidth) > width
        } else {
            if (direction < 0 && mCurrentOffsetX < 0) {
                true
            } else direction > 0 && mCurrentOffsetX + pdfFile!!.getDocLen(mZoom) > width
        }
    }

    override fun canScrollVertically(direction: Int): Boolean {
        if (pdfFile == null) return true
        return if (isSwipeVertical) {
            if (direction < 0 && mCurrentOffsetY < 0) {
                true
            } else {
                direction > 0 && mCurrentOffsetY + pdfFile!!.getDocLen(mZoom) > height
            }
        } else {
            if (direction < 0 && mCurrentOffsetY < 0) {
                true
            } else {
                direction > 0 && mCurrentOffsetY + toCurrentScale(pdfFile!!.maxPageHeight) > height
            }
        }
    }

    /**
     * Handle fling animation
     */
    override fun computeScroll() {
        super.computeScroll()
        if (isInEditMode) return
        mAnimationManager?.computeFling()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (mRenderingHandlerThread == null) mRenderingHandlerThread = HandlerThread("PDF renderer")
    }

    override fun onDetachedFromWindow() {
        recycle()
        if (mRenderingHandlerThread != null) {
            mRenderingHandlerThread?.quitSafely()
            mRenderingHandlerThread = null
        }
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        if (isInEditMode) return
        // As I said in this class javadoc, we can think of this canvas as a huge strip on which
        // we draw all the images. We actually only draw the rendered parts, of course, but we
        // render them in the place they belong in this huge strip.
        // That's where Canvas.translate(x, y) becomes very helpful.
        // This is the situation :
        //  _______________________________________________
        // |   			 |					 			   |
        // | the actual  |					The big strip  |
        // |	canvas	 | 								   |
        // |_____________|								   |
        // |_______________________________________________|
        // If the rendered part is on the bottom right corner of the strip we can draw it but
        // we won't see it because the canvas is not big enough.
        // But if we call translate(-X, -Y) on the canvas just before drawing the object :
        //  _______________________________________________
        // |   			  					  _____________|
        // |   The big strip     			 |			   |
        // |		    					 |	the actual |
        // |								 |	canvas	   |
        // |_________________________________|_____________|
        // The object will be on the canvas.
        // This technique is massively used in this method, and allows abstraction of the
        // screen position when rendering the parts.

        // Draws background
        if (isEnableAntialiasing) canvas.drawFilter = mAntialiasFilter
        val mBackground = background
        if (mBackground == null) {
            canvas.drawColor(if (isNightMode) Color.BLACK else Color.WHITE)
        } else {
            mBackground.draw(canvas)
        }
        if (isRecycled || mState != State.SHOWN) return
        // Moves the canvas before drawing any element

        canvas.translate(mCurrentOffsetX, mCurrentOffsetY)
        // Draws thumbnails
        for (mPart in cacheManager!!.getThumbnails()) {
            drawPart(canvas, mPart)
        }
        // Draws parts
        for (mPart in cacheManager!!.pageParts) {
            drawPart(canvas, mPart)
            if (callbacks.onDrawAll != null && !onDrawPagesNumber.contains(mPart.page)) {
                onDrawPagesNumber.add(mPart.page)
            }
        }
        for (mPage in onDrawPagesNumber) drawWithListener(canvas, mPage, callbacks.onDrawAll)
        onDrawPagesNumber.clear()
        drawWithListener(canvas, mCurrentPage, callbacks.onDraw)
        // Restores the canvas position
        canvas.translate(-mCurrentOffsetX, -mCurrentOffsetY)
    }

    override fun onSizeChanged(w: Int, h: Int, oldW: Int, oldH: Int) {
        isHasSize = true
        if (mWaitingDocumentConfigurator != null) mWaitingDocumentConfigurator?.load()
        if (isInEditMode || mState != State.SHOWN) return
        // Calculates the position of the point which in the center of view relative to big strip
        val mCenterPointOffsetX: Float = -mCurrentOffsetX + oldW * 0.5f
        val mCenterPointOffsetY: Float = -mCurrentOffsetY + oldH * 0.5f
        val mRelativeCenterPointOffsetX: Float
        val mRelativeCenterPointOffsetY: Float
        if (isSwipeVertical) {
            mRelativeCenterPointOffsetX = mCenterPointOffsetX / pdfFile!!.maxPageWidth
            mRelativeCenterPointOffsetY = mCenterPointOffsetY / pdfFile!!.getDocLen(mZoom)
        } else {
            mRelativeCenterPointOffsetX = mCenterPointOffsetX / pdfFile!!.getDocLen(mZoom)
            mRelativeCenterPointOffsetY = mCenterPointOffsetY / pdfFile!!.maxPageHeight
        }
        mAnimationManager?.stopAll()
        pdfFile?.recalculatePageSizes(Size(w, h))
        if (isSwipeVertical) {
            mCurrentOffsetX = -mRelativeCenterPointOffsetX * pdfFile!!.maxPageWidth + w * 0.5f
            mCurrentOffsetY = -mRelativeCenterPointOffsetY * pdfFile!!.getDocLen(mZoom) + h * 0.5f
        } else {
            mCurrentOffsetX = -mRelativeCenterPointOffsetX * pdfFile!!.getDocLen(mZoom) + w * 0.5f
            mCurrentOffsetY = -mRelativeCenterPointOffsetY * pdfFile!!.maxPageHeight + h * 0.5f
        }
        moveTo(mCurrentOffsetX, mCurrentOffsetY)
        loadPageByOffset()
    }

    /**
     * Use an asset file as the pdf source
     */
    fun fromAsset(name: String?): Configurator {
        return Configurator(AssetSource(name!!))
    }

    /**
     * Use bytearray as the pdf source, documents is not saved
     */
    fun fromBytes(bytes: ByteArray?): Configurator {
        return Configurator(ByteArraySource(bytes!!))
    }

    /**
     * Use a file as the pdf source
     */
    fun fromFile(file: File?): Configurator {
        return Configurator(FileSource(file!!))
    }

    /**
     * Use custom source as pdf source
     */
    fun fromSource(documentSource: DocumentSource): Configurator {
        return Configurator(documentSource)
    }

    /**
     * Use stream as the pdf source. Stream will be written to bytearray,
     * because native code does not support Java Streams
     */
    fun fromStream(inputStream: InputStream?): Configurator {
        return Configurator(InputStreamSource(inputStream!!))
    }

    /**
     * Use URI as the pdf source, for use with content providers
     */
    fun fromUri(uri: Uri?): Configurator {
        return Configurator(UriSource(uri!!))
    }

    inner class Configurator(private val documentSource: DocumentSource) {
        private var defaultPage: Int = 0
        private var isAnnotation: Boolean = false
        private var isAntialiasing: Boolean = true
        private var isAutoSpacing: Boolean = false
        private var isEnableDoubleTap: Boolean = true
        private var isEnableSwipe: Boolean = true
        private var isFitEachPage: Boolean = false
        private var isNightMode: Boolean = false
        private var isPageFling: Boolean = false
        private var isPageSnap: Boolean = false
        private var isSwipeHorizontal: Boolean = false
        private var linkHandler: LinkHandler = DefaultLinkHandler(this@PDFView)
        private var onDrawAllListener: OnDrawListener? = null
        private var onDrawListener: OnDrawListener? = null
        private var onErrorListener: OnErrorListener? = null
        private var onLoadCompleteListener: OnLoadCompleteListener? = null
        private var onLongPressListener: OnLongPressListener? = null
        private var onPageChangeListener: OnPageChangeListener? = null
        private var onPageErrorListener: OnPageErrorListener? = null
        private var onPageScrollListener: OnPageScrollListener? = null
        private var onRenderListener: OnRenderListener? = null
        private var onTapListener: OnTapListener? = null
        private var pageFitPolicy: FitPolicy = FitPolicy.WIDTH
        private var pageNumbers: IntArray? = null
        private var password: String? = null
        private var scrollHandle: ScrollHandle? = null
        private var spacing: Int = 0

        fun pages(vararg pageNumbers: Int): Configurator {
            this.pageNumbers = pageNumbers
            return this
        }

        fun enableSwipe(enableSwipe: Boolean): Configurator {
            this.isEnableSwipe = enableSwipe
            return this
        }

        fun enableDoubleTap(enableDoubleTap: Boolean): Configurator {
            this.isEnableDoubleTap = enableDoubleTap
            return this
        }

        fun enableAnnotationRendering(annotationRendering: Boolean): Configurator {
            this.isAnnotation = annotationRendering
            return this
        }

        fun onDraw(onDrawListener: OnDrawListener?): Configurator {
            this.onDrawListener = onDrawListener
            return this
        }

        fun onDrawAll(onDrawAllListener: OnDrawListener?): Configurator {
            this.onDrawAllListener = onDrawAllListener
            return this
        }

        fun onLoad(onLoadCompleteListener: OnLoadCompleteListener?): Configurator {
            this.onLoadCompleteListener = onLoadCompleteListener
            return this
        }

        fun onPageScroll(onPageScrollListener: OnPageScrollListener?): Configurator {
            this.onPageScrollListener = onPageScrollListener
            return this
        }

        fun onError(onErrorListener: OnErrorListener?): Configurator {
            this.onErrorListener = onErrorListener
            return this
        }

        fun onPageError(onPageErrorListener: OnPageErrorListener?): Configurator {
            this.onPageErrorListener = onPageErrorListener
            return this
        }

        fun onPageChange(onPageChangeListener: OnPageChangeListener?): Configurator {
            this.onPageChangeListener = onPageChangeListener
            return this
        }

        fun onRender(onRenderListener: OnRenderListener?): Configurator {
            this.onRenderListener = onRenderListener
            return this
        }

        fun onTap(onTapListener: OnTapListener?): Configurator {
            this.onTapListener = onTapListener
            return this
        }

        fun onLongPress(onLongPressListener: OnLongPressListener?): Configurator {
            this.onLongPressListener = onLongPressListener
            return this
        }

        fun linkHandler(linkHandler: LinkHandler): Configurator {
            this.linkHandler = linkHandler
            return this
        }

        fun defaultPage(defaultPage: Int): Configurator {
            this.defaultPage = defaultPage
            return this
        }

        fun swipeHorizontal(swipeHorizontal: Boolean): Configurator {
            this.isSwipeHorizontal = swipeHorizontal
            return this
        }

        fun password(password: String?): Configurator {
            this.password = password
            return this
        }

        fun scrollHandle(scrollHandle: ScrollHandle?): Configurator {
            this.scrollHandle = scrollHandle
            return this
        }

        fun enableAntialiasing(antialiasing: Boolean): Configurator {
            this.isAntialiasing = antialiasing
            return this
        }

        fun spacing(spacing: Int): Configurator {
            this.spacing = spacing
            return this
        }

        fun autoSpacing(autoSpacing: Boolean): Configurator {
            this.isAutoSpacing = autoSpacing
            return this
        }

        fun pageFitPolicy(pageFitPolicy: FitPolicy): Configurator {
            this.pageFitPolicy = pageFitPolicy
            return this
        }

        fun fitEachPage(fitEachPage: Boolean): Configurator {
            this.isFitEachPage = fitEachPage
            return this
        }

        fun pageSnap(pageSnap: Boolean): Configurator {
            this.isPageSnap = pageSnap
            return this
        }

        fun pageFling(pageFling: Boolean): Configurator {
            this.isPageFling = pageFling
            return this
        }

        fun nightMode(nightMode: Boolean): Configurator {
            this.isNightMode = nightMode
            return this
        }

        fun disableLongPress(): Configurator {
            mDragPinchManager?.disableLongPress()
            return this
        }

        fun load() {
            if (!isHasSize) {
                mWaitingDocumentConfigurator = this
                return
            }
            this@PDFView.recycle()
            this@PDFView.callbacks.setOnLoadComplete(onLoadCompleteListener)
            this@PDFView.callbacks.onError = onErrorListener
            this@PDFView.callbacks.onDraw = onDrawListener
            this@PDFView.callbacks.onDrawAll = onDrawAllListener
            this@PDFView.callbacks.setOnPageChange(onPageChangeListener)
            this@PDFView.callbacks.setOnPageScroll(onPageScrollListener)
            this@PDFView.callbacks.setOnRender(onRenderListener)
            this@PDFView.callbacks.setOnTap(onTapListener)
            this@PDFView.callbacks.setOnLongPress(onLongPressListener)
            this@PDFView.callbacks.setOnPageError(onPageErrorListener)
            this@PDFView.callbacks.setLinkHandler(linkHandler)
            this@PDFView.setSwipeEnabled(isEnableSwipe)
            this@PDFView.setNightMode(isNightMode)
            this@PDFView.setDoubleTap(isEnableDoubleTap)
            this@PDFView.setDefaultPage(defaultPage)
            this@PDFView.setSwipeVertical(!isSwipeHorizontal)
            this@PDFView.setAnnotation(isAnnotation)
            this@PDFView.setScrollHandle(scrollHandle)
            this@PDFView.setAntialiasing(isAntialiasing)
            this@PDFView.setSpacing(spacing)
            this@PDFView.setAutoSpacing(isAutoSpacing)
            this@PDFView.setPageFitPolicy(pageFitPolicy)
            this@PDFView.setFitEachPage(isFitEachPage)
            this@PDFView.setPageSnap(isPageSnap)
            this@PDFView.setPageFling(isPageFling)
            if (pageNumbers != null) {
                this@PDFView.load(documentSource, password, pageNumbers)
            } else {
                this@PDFView.load(documentSource, password)
            }
        }
    }

    companion object {
        const val DEFAULT_MAX_SCALE = 3.0f
        const val DEFAULT_MID_SCALE = 1.75f
        const val DEFAULT_MIN_SCALE = 1.0f
    }

    private fun initPDFView() {
        if (isInEditMode) return
        pdfiumCore = PdfiumCore(context = context)
        cacheManager = CacheManager()
        mAnimationManager = AnimationManager(pdfView = this)
        mDragPinchManager = DragPinchManager(pdfView = this, mAnimationManager!!)
        mPagesLoader = PagesLoader(pdfView = this)
        mPaint = Paint()
        mDebugPaint = Paint()
        mDebugPaint?.style = Paint.Style.STROKE
        setWillNotDraw(false)
    }

    init {
        initPDFView()
    }
}