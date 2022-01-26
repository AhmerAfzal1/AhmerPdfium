package com.ahmer.pdfviewer

import android.content.Context
import android.graphics.*
import android.net.Uri
import android.os.HandlerThread
import android.util.AttributeSet
import android.util.Log
import android.widget.RelativeLayout
import androidx.annotation.ColorInt
import com.ahmer.pdfium.Bookmark
import com.ahmer.pdfium.Link
import com.ahmer.pdfium.Meta
import com.ahmer.pdfium.util.Size
import com.ahmer.pdfium.util.SizeF
import com.ahmer.pdfviewer.exception.PageRenderingException
import com.ahmer.pdfviewer.link.DefaultLinkHandler
import com.ahmer.pdfviewer.link.LinkHandler
import com.ahmer.pdfviewer.listener.*
import com.ahmer.pdfviewer.model.PagePart
import com.ahmer.pdfviewer.scroll.ScrollHandle
import com.ahmer.pdfviewer.source.AssetSource
import com.ahmer.pdfviewer.source.DocumentSource
import com.ahmer.pdfviewer.source.FileSource
import com.ahmer.pdfviewer.source.UriSource
import com.ahmer.pdfviewer.util.*
import java.io.File
import java.util.*

/**
 * It supports animations, zoom, cache, and swipe.
 *
 *
 * To fully understand this class you must know its principles :
 * - The PDF document is seen as if we always want to draw all the pages.
 * - The thing is that we only draw the visible parts.
 * - All parts are the same size, this is because we can't interrupt a native page rendering,
 * so we need these renderings to be as fast as possible, and be able to interrupt them
 * as soon as we can.
 * - The parts are loaded when the current offset or the current zoom level changes
 *
 *
 * Important :
 * - DocumentPage = A page of the PDF document.
 * - UserPage = A page as defined by the user.
 * By default, they're the same. But the user can change the pages order
 * using [.load]. In this
 * particular case, a userPage of 5 can refer to a documentPage of 17.
 */
class PDFView(context: Context?, set: AttributeSet?) : RelativeLayout(context, set) {
    private var searchQuery = ""
    private val antialiasFilter =
        PaintFlagsDrawFilter(0, Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    /**
     * Pages numbers used when calling onDrawAllListener
     */
    private val onDrawPagesNums: MutableList<Int> = ArrayList(10)
    var defaultOffset = 0f

    /**
     * Rendered parts go to the cache manager
     */
    var cacheManager: CacheManager? = null
    var pdfFile: PdfFile? = null

    /**
     * The thread [.renderingHandler] will run on
     */
    private var renderingHandlerThread: HandlerThread? = null
    private var decodingTask: DecodingTask? = null

    /**
     * Handler always waiting in the background and rendering tasks
     */
    var renderingHandler: RenderingHandler? = null
    var callbacks = Callbacks()
    private var minZoom = DEFAULT_MIN_SCALE
    private var midZoom = DEFAULT_MID_SCALE
    private var maxZoom = DEFAULT_MAX_SCALE
    private var scrollDir = ScrollDir.NONE

    /**
     * Animation manager manage all offset and zoom animation
     */
    private var animationManager: AnimationManager? = null

    /**
     * Drag manager manage all touch events
     */
    private var dragPinchManager: DragPinchManager? = null

    /**
     * The index of the current sequence
     */
    private var currentPage = 0

    /**
     * If you picture all the pages side by side in their optimal width,
     * and taking into account the zoom level, the current offset is the
     * position of the left border of the screen in this big picture
     */
    private var currentXOffset = 0f

    /**
     * If you picture all the pages side by side in their optimal width,
     * and taking into account the zoom level, the current offset is the
     * position of the left border of the screen in this big picture
     */
    private var currentYOffset = 0f

    /**
     * The zoom level, always >= 1
     */
    private var zoom = 1f

    /**
     * True if the PDFView has been recycled
     */
    private var isRecycled = true

    /**
     * Current state of the view
     */
    private var state = State.DEFAULT
    private var pagesLoader: PagesLoader? = null

    /**
     * Paint object for drawing
     */
    private var paint: Paint? = null

    /**
     * Paint object for drawing debug stuff
     */
    private var debugPaint: Paint? = null

    /**
     * Policy for fitting pages to screen
     */
    private var pageFitPolicy = FitPolicy.WIDTH
    private var isFitEachPage = false
    private var defaultPage = 0

    /**
     * True if should scroll through pages vertically instead of horizontally
     */
    private var isSwipeVertical = true
    private var enableSwipe = true
    private var isDoubleTapEnabled = true
    private var nightMode = false
    private var isPageSnap = true
    private var scrollHandle: ScrollHandle? = null
    private var isScrollHandleInit = false

    @ColorInt
    private var textHighlightColor = Color.WHITE

    /**
     * True if bitmap should use ARGB_8888 format and take more memory
     * False if bitmap should be compressed by using RGB_565 format and take less memory
     */
    private var bestQuality = false

    /**
     * True if annotations should be rendered
     * False otherwise
     */
    private var annotationRendering = false

    /**
     * True if the view should render during scaling
     * Can not be forced on older API versions (< Build.VERSION_CODES.KITKAT) as the GestureDetector does
     * not detect scrolling while scaling.
     * False otherwise
     */
    private var renderDuringScale = false

    /**
     * Antialiasing and bitmap filtering
     */
    private var enableAntialiasing = true

    /**
     * Spacing between pages, in px
     */
    private var spacingPx = 0

    /**
     * Add dynamic spacing to fit each page separately on the screen.
     */
    private var autoSpacing = false

    /**
     * Fling a single page at a time
     */
    private var pageFling = true

    /**
     * Holds info whether view has been added to layout and has width and height
     */
    private var hasSize = false

    /**
     * Holds last used Configurator that should be loaded when view has size
     */
    private var waitingDocumentConfigurator: Configurator? = null

    fun setTextHighlightColor(textHighlightColor: Int) {
        this.textHighlightColor = textHighlightColor
        loadPages()
    }

    fun getTextHighlightColor(): Int {
        return textHighlightColor
    }

    fun getScrollHandle(): ScrollHandle? {
        return scrollHandle
    }

    private fun setScrollHandle(scrollHandle: ScrollHandle?) {
        this.scrollHandle = scrollHandle
    }

    private fun load(docSource: DocumentSource, password: String?, userPages: IntArray? = null) {
        check(isRecycled) { "Don't call load on a PDF View without recycling it first." }
        isRecycled = false
        // Start decoding document
        decodingTask = DecodingTask(docSource, password, userPages, this)
        decodingTask!!.execute()
    }

    /**
     * Go to the given page.
     *
     * @param page Page index.
     */
    @JvmOverloads
    fun jumpTo(page: Int, withAnimation: Boolean = false) {
        var pageIndex = page
        if (pdfFile == null) {
            return
        }
        pageIndex = pdfFile!!.determineValidPageNumberFrom(pageIndex)
        var offset: Float = if (pageIndex == 0) 0f else -pdfFile!!.getPageOffset(pageIndex, zoom)
        offset += pdfFile!!.getPageSpacing(pageIndex, getZoom()) / 2f
        if (isSwipeVertical) {
            if (withAnimation) {
                animationManager!!.startYAnimation(currentYOffset, offset)
            } else {
                moveTo(currentXOffset, offset)
            }
        } else {
            if (withAnimation) {
                animationManager!!.startXAnimation(currentXOffset, offset)
            } else {
                moveTo(offset, currentYOffset)
            }
        }
        showPage(pageIndex)
    }

    fun showPage(pageNb: Int) {
        var pageNumber = pageNb
        if (isRecycled) {
            return
        }
        // Check the page number and makes the difference between UserPages and DocumentPages
        pageNumber = pdfFile!!.determineValidPageNumberFrom(pageNumber)
        currentPage = pageNumber
        loadPages()
        if (scrollHandle != null && documentFitsView()) {
            scrollHandle!!.setPageNum(currentPage + 1)
        }
        callbacks.callOnPageChange(currentPage, pdfFile!!.pagesCount)
    }

    /**
     * Get current position as ratio of document length to visible area.
     * 0 means that document start is visible, 1 that document end is visible
     *
     * @return offset between 0 and 1
     */
    fun getPositionOffset(): Float {
        val offset: Float = if (isSwipeVertical) {
            -currentYOffset / (pdfFile!!.getDocLen(zoom) - height)
        } else {
            -currentXOffset / (pdfFile!!.getDocLen(zoom) - width)
        }
        return MathUtils.limit(offset, 0f, 1f)
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
            moveTo(currentXOffset, (-pdfFile!!.getDocLen(zoom) + height) * progress, moveHandle)
        } else {
            moveTo((-pdfFile!!.getDocLen(zoom) + width) * progress, currentYOffset, moveHandle)
        }
        loadPageByOffset()
    }

    fun stopFling() {
        animationManager!!.stopFling()
    }

    fun getPageCount(): Int {
        return if (pdfFile == null) 0 else pdfFile!!.pagesCount
    }

    fun isNightMode(): Boolean {
        return nightMode
    }

    fun setNightMode(nightMode: Boolean) {
        this.nightMode = nightMode
    }

    fun enableDoubleTap(enableDoubleTap: Boolean) {
        isDoubleTapEnabled = enableDoubleTap
    }

    fun isDoubleTapEnabled(): Boolean {
        return isDoubleTapEnabled
    }

    fun onPageError(ex: PageRenderingException) {
        if (!callbacks.callOnPageError(ex.page, ex.cause)) {
            Log.e(TAG, "Cannot open page: " + ex.page + " due to: " + ex.cause)
        }
    }

    fun recycle() {
        waitingDocumentConfigurator = null
        animationManager?.stopAll()
        dragPinchManager?.disable()
        // Stop tasks
        if (renderingHandler != null) {
            renderingHandler?.stop()
            renderingHandler?.removeMessages(RenderingHandler.MSG_RENDER_PART_TASK)
        }
        decodingTask?.cancel()
        // Clear caches
        cacheManager!!.recycle()
        if (scrollHandle != null && isScrollHandleInit) {
            scrollHandle!!.destroyLayout()
        }
        if (pdfFile != null) {
            pdfFile!!.dispose()
            pdfFile = null
        }
        renderingHandler = null
        scrollHandle = null
        isScrollHandleInit = false
        currentYOffset = 0f
        currentXOffset = currentYOffset
        zoom = 1f
        isRecycled = true
        callbacks = Callbacks()
        state = State.DEFAULT
    }

    fun isRecycled(): Boolean {
        return isRecycled
    }

    /**
     * Handle fling animation
     */
    override fun computeScroll() {
        super.computeScroll()
        if (isInEditMode) {
            return
        }
        animationManager!!.computeFling()
    }

    override fun onDetachedFromWindow() {
        recycle()
        if (renderingHandlerThread != null) {
            renderingHandlerThread?.quitSafely()
            renderingHandlerThread = null
        }
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldW: Int, oldH: Int) {
        hasSize = true
        if (waitingDocumentConfigurator != null) {
            waitingDocumentConfigurator!!.load()
        }
        if (isInEditMode || state != State.SHOWN) {
            return
        }

        // Calculates the position of the point which in the center of view relative to big strip
        val centerPointInStripXOffset = -currentXOffset + oldW * 0.5f
        val centerPointInStripYOffset = -currentYOffset + oldH * 0.5f
        val relativeCenterPointInStripXOffset: Float
        val relativeCenterPointInStripYOffset: Float
        if (isSwipeVertical) {
            relativeCenterPointInStripXOffset = centerPointInStripXOffset / pdfFile!!.maxPageWidth
            relativeCenterPointInStripYOffset =
                centerPointInStripYOffset / pdfFile!!.getDocLen(zoom)
        } else {
            relativeCenterPointInStripXOffset =
                centerPointInStripXOffset / pdfFile!!.getDocLen(zoom)
            relativeCenterPointInStripYOffset =
                centerPointInStripYOffset / pdfFile!!.getPageSize(currentPage).height
        }
        animationManager!!.stopAll()
        pdfFile!!.recalculatePageSizes(Size(w, h))
        if (isSwipeVertical) {
            currentXOffset = -relativeCenterPointInStripXOffset * pdfFile!!.maxPageWidth + w * 0.5f
            currentYOffset =
                -relativeCenterPointInStripYOffset * pdfFile!!.getDocLen(zoom) + h * 0.5f
        } else {
            currentXOffset =
                -relativeCenterPointInStripXOffset * pdfFile!!.getDocLen(zoom) + w * 0.5f
            currentYOffset =
                -relativeCenterPointInStripYOffset * pdfFile!!.getPageSize(currentPage).height + h * 0.5f
        }
        moveTo(currentXOffset, currentYOffset)
        loadPageByOffset()
    }

    override fun canScrollHorizontally(direction: Int): Boolean {
        if (pdfFile == null) {
            return true
        }
        return if (isSwipeVertical) {
            if (direction < 0 && currentXOffset < 0) {
                true
            } else direction > 0 && currentXOffset + toCurrentScale(pdfFile!!.maxPageWidth) > width
        } else {
            if (direction < 0 && currentXOffset < 0) {
                true
            } else direction > 0 && currentXOffset + pdfFile!!.getDocLen(zoom) > width
        }
    }

    override fun canScrollVertically(direction: Int): Boolean {
        if (pdfFile == null) {
            return true
        }
        return if (isSwipeVertical) {
            if (direction < 0 && currentYOffset < 0) {
                true
            } else {
                direction > 0 && currentYOffset + pdfFile!!.getDocLen(zoom) > height
            }
        } else {
            if (direction < 0 && currentYOffset < 0) {
                true
            } else {
                direction > 0 && currentYOffset + toCurrentScale(pdfFile!!.maxPageHeight) > height
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (isInEditMode) {
            return
        }
        // As I said in this class javadoc, we can think of this canvas as a huge
        // strip on which we draw all the images. We actually only draw the rendered
        // parts, of course, but we render them in the place they belong in this huge
        // strip.

        // That's where Canvas.translate(x, y) becomes very helpful.
        // This is the situation :
        //  _______________________________________________
        // |   			 |					 			   |
        // | the actual  |					The big strip  |
        // |	canvas	 | 								   |
        // |_____________|								   |
        // |_______________________________________________|
        //
        // If the rendered part is on the bottom right corner of the strip
        // we can draw it but we won't see it because the canvas is not big enough.

        // But if we call translate(-X, -Y) on the canvas just before drawing the object :
        //  _______________________________________________
        // |   			  					  _____________|
        // |   The big strip     			 |			   |
        // |		    					 |	the actual |
        // |								 |	canvas	   |
        // |_________________________________|_____________|
        //
        // The object will be on the canvas.
        // This technique is massively used in this method, and allows
        // abstraction of the screen position when rendering the parts.

        // Draws background
        if (enableAntialiasing) {
            canvas.drawFilter = antialiasFilter
        }
        val bg = background
        if (bg == null) {
            canvas.drawColor(if (nightMode) Color.BLACK else Color.WHITE)
        } else {
            bg.draw(canvas)
        }
        if (isRecycled) {
            return
        }
        if (state != State.SHOWN) {
            return
        }

        // Moves the canvas before drawing any element
        val currentXOffset = currentXOffset
        val currentYOffset = currentYOffset
        canvas.translate(currentXOffset, currentYOffset)

        // Draws thumbnails
        for (part in cacheManager!!.getThumbnails()) {
            drawPart(canvas, part)
        }

        // Draws parts
        for (part in cacheManager!!.pageParts) {
            drawPart(canvas, part)
            if (callbacks.onDrawAll != null && !onDrawPagesNums.contains(part.page)) {
                onDrawPagesNums.add(part.page)
            }
        }
        for (page in onDrawPagesNums) {
            drawWithListener(canvas, page, callbacks.onDrawAll)
        }
        onDrawPagesNums.clear()
        drawWithListener(canvas, currentPage, callbacks.onDraw)

        // Restores the canvas position
        canvas.translate(-currentXOffset, -currentYOffset)
    }

    private fun drawWithListener(canvas: Canvas, page: Int, listener: OnDrawListener?) {
        if (listener != null) {
            val translateX: Float
            val translateY: Float
            if (isSwipeVertical) {
                translateX = 0f
                translateY = pdfFile!!.getPageOffset(page, zoom)
            } else {
                translateY = 0f
                translateX = pdfFile!!.getPageOffset(page, zoom)
            }
            canvas.translate(translateX, translateY)
            val size = pdfFile!!.getPageSize(page)
            listener.onLayerDrawn(
                canvas, toCurrentScale(size.width), toCurrentScale(size.height), page
            )
            canvas.translate(-translateX, -translateY)
        }
    }

    /**
     * Draw a given PagePart on the canvas
     */
    private fun drawPart(canvas: Canvas, part: PagePart) {
        // Can seem strange, but avoid lot of calls
        val pageRelativeBounds = part.pageRelativeBounds
        val renderedBitmap = part.renderedBitmap
        if (renderedBitmap != null && renderedBitmap.isRecycled) {
            return
        }

        // Move to the target page
        val localTranslationX: Float
        val localTranslationY: Float
        val size = pdfFile!!.getPageSize(part.page)
        if (isSwipeVertical) {
            localTranslationY = pdfFile!!.getPageOffset(part.page, zoom)
            val maxWidth = pdfFile!!.maxPageWidth
            localTranslationX = toCurrentScale(maxWidth - size.width) / 2
        } else {
            localTranslationX = pdfFile!!.getPageOffset(part.page, zoom)
            val maxHeight = pdfFile!!.maxPageHeight
            localTranslationY = toCurrentScale(maxHeight - size.height) / 2
        }
        canvas.translate(localTranslationX, localTranslationY)
        val srcRect = renderedBitmap?.let { Rect(0, 0, it.width, it.height) }
        val offsetX = toCurrentScale(pageRelativeBounds.left * size.width)
        val offsetY = toCurrentScale(pageRelativeBounds.top * size.height)
        val width = toCurrentScale(pageRelativeBounds.width() * size.width)
        val height = toCurrentScale(pageRelativeBounds.height() * size.height)

        // If we use float values for this rectangle, there will be a possible gap between page
        // parts, especially when the zoom level is high.
        val dstRect = RectF(offsetX, offsetY, (offsetX + width), (offsetY + height))
        // Check if bitmap is in the screen
        val translationX = currentXOffset + localTranslationX
        val translationY = currentYOffset + localTranslationY
        if (translationX + dstRect.left >= getWidth() || translationX + dstRect.right <= 0 || translationY + dstRect.top >= getHeight() || translationY + dstRect.bottom <= 0) {
            canvas.translate(-localTranslationX, -localTranslationY)
            return
        }
        canvas.drawBitmap(renderedBitmap!!, srcRect, dstRect, paint)
        if (PdfConstants.DEBUG_MODE) {
            debugPaint!!.color = if (part.page % 2 == 0) Color.RED else Color.BLUE
            canvas.drawRect(dstRect, debugPaint!!)
        }

        // Restore the canvas position
        canvas.translate(-localTranslationX, -localTranslationY)
    }

    fun getSearchQuery(): String {
        return searchQuery
    }

    fun setSearchQuery(searchQuery: String) {
        this.searchQuery = searchQuery
        loadPages()
    }

    /**
     * Load all the parts around the center of the screen,
     * taking into account X and Y offsets, zoom level, and
     * the current page displayed
     */
    fun loadPages() {
        if (pdfFile == null || renderingHandler == null) {
            return
        }

        // Cancel all current tasks
        renderingHandler?.removeMessages(RenderingHandler.MSG_RENDER_PART_TASK)
        cacheManager!!.makeANewSet()
        pagesLoader!!.loadPages(searchQuery)
        reDraw()
    }

    interface OnTextParseListener {
        fun onTextParseSuccess(pageIndex: Int, text: String)
        fun onTextParseError(pageIndex: Int)
    }

    private var parseListener: OnTextParseListener? = null
    fun parseText(listener: OnTextParseListener) {
        if (pdfFile == null) {
            return
        }
        if (renderingHandler == null) {
            return
        }
        parseListener = listener
        reDraw()
    }

    /**
     * Called when the PDF is loaded
     */
    fun loadComplete(pdfFile: PdfFile) {
        state = State.LOADED
        this.pdfFile = pdfFile
        if (renderingHandlerThread?.isAlive != true) {
            renderingHandlerThread?.start()
        }
        renderingHandler = renderingHandlerThread?.looper?.let {
            RenderingHandler(it, this)
        }
        renderingHandler?.start()
        if (scrollHandle != null) {
            scrollHandle!!.setupLayout(this)
            isScrollHandleInit = true
        }
        dragPinchManager!!.enable()
        callbacks.callOnLoadComplete(pdfFile.pagesCount)
        jumpTo(defaultPage, false)
    }

    fun loadError(t: Throwable?) {
        state = State.ERROR
        // Store reference, because callbacks will be cleared in recycle() method
        val onErrorListener = callbacks.onError
        recycle()
        invalidate()
        onErrorListener?.onError(t) ?: Log.e(TAG, "Load PDF error: ", t)
    }

    private fun reDraw() {
        invalidate()
    }

    /**
     * Called when a rendering task is over and
     * a PagePart has been freshly created.
     *
     * @param part The created PagePart.
     */
    fun onBitmapRendered(part: PagePart) {
        // When it is first rendered part
        if (state == State.LOADED) {
            state = State.SHOWN
            callbacks.callOnRender(pdfFile!!.pagesCount)
        }
        if (part.isThumbnail) {
            cacheManager!!.cacheThumbnail(part)
        } else {
            cacheManager!!.cachePart(part)
        }
        reDraw()
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
        var offX = offsetX
        var offY = offsetY
        if (isSwipeVertical) {
            // Check X offset
            val scaledPageWidth = toCurrentScale(pdfFile!!.maxPageWidth)
            if (scaledPageWidth < width) {
                offX = width / 2f - scaledPageWidth / 2
            } else {
                if (offX > 0) {
                    offX = 0f
                } else if (offX + scaledPageWidth < width) {
                    offX = width - scaledPageWidth
                }
            }

            // Check Y offset
            val contentHeight = pdfFile!!.getDocLen(zoom)
            if (contentHeight < height) { // whole document height visible on screen
                offY = (height - contentHeight) / 2
            } else {
                if (offY > 0) { // top visible
                    offY = 0f
                } else if (offY + contentHeight < height) { // bottom visible
                    offY = -contentHeight + height
                }
            }
            scrollDir = when {
                offY < currentYOffset -> {
                    ScrollDir.END
                }
                offY > currentYOffset -> {
                    ScrollDir.START
                }
                else -> {
                    ScrollDir.NONE
                }
            }
        } else {
            // Check Y offset
            val scaledPageHeight = toCurrentScale(pdfFile!!.maxPageHeight)
            if (scaledPageHeight < height && defaultOffset == 0.0f) {
                offY = height / 2f - scaledPageHeight / 2
                if (defaultOffset == 0.0f && currentYOffset == 0.0f) {
                    defaultOffset = offY
                }
            } else {
                if (offY - toCurrentScale(defaultOffset) > 0) {
                    offY = toCurrentScale(defaultOffset)
                } else if (offY + (scaledPageHeight + toCurrentScale(defaultOffset)) < height) {
                    offY = height - scaledPageHeight - toCurrentScale(defaultOffset)
                }
            }

            // Check X offset
            val contentWidth = pdfFile!!.getDocLen(zoom)
            if (contentWidth < width) { // Whole document width visible on screen
                offX = (width - contentWidth) / 2
            } else {
                if (offX > 0) { // left visible
                    offX = 0f
                } else if (offX + contentWidth < width) { // right visible
                    offX = -contentWidth + width
                }
            }
            scrollDir = when {
                offX < currentXOffset -> {
                    ScrollDir.END
                }
                offX > currentXOffset -> {
                    ScrollDir.START
                }
                else -> {
                    ScrollDir.NONE
                }
            }
        }
        currentXOffset = offX
        currentYOffset = offY
        val positionOffset = getPositionOffset()
        if (moveHandle && scrollHandle != null && documentFitsView()) {
            scrollHandle!!.setScroll(positionOffset)
        }
        callbacks.callOnPageScroll(getCurrentPage(), positionOffset)
        reDraw()
        val pageNumber = findFocusPage(currentXOffset, currentYOffset)
        if (pageNumber >= 0 && pageNumber < pdfFile!!.pagesCount && pageNumber != getCurrentPage()) {
            showPage(pageNumber)
        }
    }

    fun loadPageByOffset() {
        if (pdfFile == null) {
            return
        }
        if (0 == pdfFile!!.pagesCount) {
            return
        }
        val offset: Float
        val screenCenter: Float
        if (isSwipeVertical) {
            offset = currentYOffset
            screenCenter = height.toFloat() / 2
        } else {
            offset = currentXOffset
            screenCenter = width.toFloat() / 2
        }
        val page = pdfFile!!.getPageAtOffset(-(offset - screenCenter), zoom)
        if (page >= 0 && page <= pdfFile!!.pagesCount - 1 && page != getCurrentPage()) {
            showPage(page)
        } else {
            loadPages()
        }
    }

    /**
     * Get the page rotation
     *
     * @param pageIndex the page index
     * @return the rotation
     */
    fun getPageRotation(pageIndex: Int): Int {
        return pdfFile?.getPageRotation(pageIndex)!!
    }

    /**
     * Animate to the nearest snapping position for the current SnapPolicy
     */
    fun performPageSnap() {
        if (!isPageSnap || pdfFile == null || pdfFile!!.pagesCount == 0) {
            return
        }
        val centerPage = findFocusPage(currentXOffset, currentYOffset)
        val edge = findSnapEdge(centerPage)
        if (edge == SnapEdge.NONE) {
            return
        }
        val offset = snapOffsetForPage(centerPage, edge)
        if (isSwipeVertical) {
            animationManager?.startYAnimation(currentYOffset, -offset)
        } else {
            animationManager?.startXAnimation(currentXOffset, -offset)
        }
    }

    /**
     * Find the edge to snap to when showing the specified page
     */
    fun findSnapEdge(page: Int): SnapEdge {
        if (!isPageSnap || page < 0) {
            return SnapEdge.NONE
        }
        val currentOffset = if (isSwipeVertical) currentYOffset else currentXOffset
        val offset = -pdfFile!!.getPageOffset(page, zoom)
        val length = if (isSwipeVertical) height else width
        val pageLength = pdfFile!!.getPageLength(page, zoom)
        return when {
            length >= pageLength -> {
                SnapEdge.CENTER
            }
            currentOffset >= offset -> {
                SnapEdge.START
            }
            offset - pageLength > currentOffset - length -> {
                SnapEdge.END
            }
            else -> {
                SnapEdge.NONE
            }
        }
    }

    /**
     * Get the offset to move to in order to snap to the page
     */
    fun snapOffsetForPage(pageIndex: Int, edge: SnapEdge): Float {
        var offset = pdfFile!!.getPageOffset(pageIndex, zoom)
        val length = if (isSwipeVertical) height.toFloat() else width.toFloat()
        val pageLength = pdfFile!!.getPageLength(pageIndex, zoom)
        if (edge == SnapEdge.CENTER) {
            offset = offset - length / 2f + pageLength / 2f
        } else if (edge == SnapEdge.END) {
            offset = offset - length + pageLength
        }
        return offset
    }

    fun findFocusPage(xOffset: Float, yOffset: Float): Int {
        val currOffset = if (isSwipeVertical) yOffset else xOffset
        val length = if (isSwipeVertical) height.toFloat() else width.toFloat()
        // Make sure first and last page can be found
        if (currOffset > -1) {
            return 0
        } else if (currOffset < -pdfFile!!.getDocLen(zoom) + length + 1) {
            return pdfFile!!.pagesCount - 1
        }
        // Else find page in center
        val center = currOffset - length / 2f
        return pdfFile!!.getPageAtOffset(-center, zoom)
    }

    /**
     * @return true if single page fills the entire screen in the scrolling direction
     */
    fun pageFillsScreen(): Boolean {
        val start = -pdfFile!!.getPageOffset(currentPage, zoom)
        val end = start - pdfFile!!.getPageLength(currentPage, zoom)
        return if (isSwipeVertical()) {
            start > currentYOffset && end < currentYOffset - height
        } else {
            start > currentXOffset && end < currentXOffset - width
        }
    }

    /**
     * Move relatively to the current position.
     *
     * @param dx The X difference you want to apply.
     * @param dy The Y difference you want to apply.
     * @see .moveTo
     */
    fun moveRelativeTo(dx: Float, dy: Float) {
        moveTo(currentXOffset + dx, currentYOffset + dy)
    }

    /**
     * Change the zoom level
     */
    fun zoomTo(zoom: Float) {
        this.zoom = zoom
    }

    /**
     * Change the zoom level, relatively to a pivot point.
     * It will call moveTo() to make sure the given point stays
     * in the middle of the screen.
     *
     * @param zoom  The zoom level.
     * @param pivot The point on the screen that should stays.
     */
    fun zoomCenteredTo(zoom: Float, pivot: PointF) {
        val dzoom = zoom / this.zoom
        zoomTo(zoom)
        var baseX = currentXOffset * dzoom
        var baseY = currentYOffset * dzoom
        baseX += pivot.x - pivot.x * dzoom
        baseY += pivot.y - pivot.y * dzoom
        moveTo(baseX, baseY)
    }

    /**
     * @see .zoomCenteredTo
     */
    fun zoomCenteredRelativeTo(dzoom: Float, pivot: PointF) {
        zoomCenteredTo(zoom * dzoom, pivot)
    }

    /**
     * Checks if whole document can be displayed on screen, doesn't include zoom
     *
     * @return true if whole document can displayed at once, false otherwise
     */
    fun documentFitsView(): Boolean {
        val len = pdfFile!!.getDocLen(1f)
        return if (isSwipeVertical) {
            len >= height
        } else {
            len >= width
        }
    }

    fun fitToWidth(page: Int) {
        if (state != State.SHOWN) {
            Log.e(TAG, "Cannot fit, document not rendered yet")
            return
        }
        zoomTo(width / pdfFile!!.getPageSize(page).width)
        jumpTo(page)
    }

    fun getPageSize(pageIndex: Int): SizeF {
        return if (pdfFile == null) SizeF(0f, 0f) else pdfFile!!.getPageSize(pageIndex)
    }

    fun getCurrentPage(): Int {
        return currentPage
    }

    fun getCurrentXOffset(): Float {
        return currentXOffset
    }

    fun getCurrentYOffset(): Float {
        return currentYOffset
    }

    fun toRealScale(size: Float): Float {
        return size / zoom
    }

    fun toCurrentScale(size: Float): Float {
        return size * zoom
    }

    fun getZoom(): Float {
        return zoom
    }

    fun isZooming(): Boolean {
        return zoom != minZoom
    }

    private fun setDefaultPage(defaultPage: Int) {
        this.defaultPage = defaultPage
    }

    fun resetZoom() {
        zoomTo(minZoom)
    }

    fun resetZoomWithAnimation() {
        zoomWithAnimation(minZoom)
    }

    fun zoomWithAnimation(centerX: Float, centerY: Float, scale: Float) {
        animationManager!!.startZoomAnimation(centerX, centerY, zoom, scale)
    }

    fun zoomWithAnimation(scale: Float) {
        animationManager!!.startZoomAnimation(width / 2f, height / 2f, zoom, scale)
    }

    /**
     * Get page number at given offset
     *
     * @param positionOffset scroll offset between 0 and 1
     * @return page number at given offset, starting from 0
     */
    fun getPageAtPositionOffset(positionOffset: Float): Int {
        return pdfFile!!.getPageAtOffset(pdfFile!!.getDocLen(zoom) * positionOffset, zoom)
    }

    fun getTotalPagesCount(): Int {
        return pdfFile?.getTotalPagesCount() ?: 0
    }

    fun getMinZoom(): Float {
        return minZoom
    }

    fun setMinZoom(minZoom: Float) {
        this.minZoom = minZoom
    }

    fun getMidZoom(): Float {
        return midZoom
    }

    fun setMidZoom(midZoom: Float) {
        this.midZoom = midZoom
    }

    fun getMaxZoom(): Float {
        return maxZoom
    }

    fun setMaxZoom(maxZoom: Float) {
        this.maxZoom = maxZoom
    }

    fun useBestQuality(bestQuality: Boolean) {
        this.bestQuality = bestQuality
    }

    fun isBestQuality(): Boolean {
        return bestQuality
    }

    fun isSwipeVertical(): Boolean {
        return isSwipeVertical
    }

    private fun setSwipeVertical(swipeVertical: Boolean) {
        this.isSwipeVertical = swipeVertical
    }

    fun isSwipeEnabled(): Boolean {
        return enableSwipe
    }

    fun setSwipeEnabled(enableSwipe: Boolean) {
        this.enableSwipe = enableSwipe
    }

    fun enableAnnotationRendering(annotationRendering: Boolean) {
        this.annotationRendering = annotationRendering
    }

    fun isAnnotationRendering(): Boolean {
        return annotationRendering
    }

    fun enableRenderDuringScale(renderDuringScale: Boolean) {
        this.renderDuringScale = renderDuringScale
    }

    fun isAntialiasing(): Boolean {
        return enableAntialiasing
    }

    fun enableAntialiasing(enableAntialiasing: Boolean) {
        this.enableAntialiasing = enableAntialiasing
    }

    fun getSpacingPx(): Int {
        return spacingPx
    }

    fun isAutoSpacingEnabled(): Boolean {
        return autoSpacing
    }

    fun setPageFling(pageFling: Boolean) {
        this.pageFling = pageFling
    }

    fun isPageFlingEnabled(): Boolean {
        return pageFling
    }

    private fun setSpacing(spacingDp: Int) {
        spacingPx = PdfUtils.getDP(context, spacingDp)
    }

    private fun setAutoSpacing(autoSpacing: Boolean) {
        this.autoSpacing = autoSpacing
    }

    fun getPageFitPolicy(): FitPolicy {
        return pageFitPolicy
    }

    private fun setPageFitPolicy(pageFitPolicy: FitPolicy) {
        this.pageFitPolicy = pageFitPolicy
    }

    fun isFitEachPage(): Boolean {
        return isFitEachPage
    }

    private fun setFitEachPage(fitEachPage: Boolean) {
        this.isFitEachPage = fitEachPage
    }

    fun isPageSnap(): Boolean {
        return isPageSnap
    }

    fun setPageSnap(pageSnap: Boolean) {
        this.isPageSnap = pageSnap
    }

    fun doRenderDuringScale(): Boolean {
        return renderDuringScale
    }

    /**
     * Invoke on changing view height to calculate new default offset
     */
    fun calculateDefaultOffset() {
        val scaledPageHeight = toCurrentScale(pdfFile!!.maxPageHeight)
        if (scaledPageHeight < height) {
            defaultOffset = height / 2.0f - scaledPageHeight / 2
        }
    }

    /**
     * Returns null if document is not loaded
     */
    fun getDocumentMeta(): Meta? {
        return pdfFile?.getMetaData()
    }

    /**
     * Will be empty until document is loaded
     */
    fun getTableOfContents(): List<Bookmark> {
        return pdfFile?.getBookmarks() ?: emptyList()
    }

    /**
     * Will be empty until document is loaded
     */
    fun getLinks(page: Int): List<Link> {
        return pdfFile?.getPageLinks(page) ?: emptyList()
    }

    /**
     * Use an asset file as the pdf source
     */
    fun fromAsset(assetName: String?): Configurator {
        return Configurator(AssetSource(assetName!!))
    }

    /**
     * Use a file as the pdf source
     */
    fun fromFile(file: File?): Configurator {
        return Configurator(FileSource(file!!))
    }

    /**
     * Use URI as the pdf source, for use with content providers
     */
    fun fromUri(uri: Uri?): Configurator {
        return Configurator(UriSource(uri!!))
    }

    /**
     * Use custom source as pdf source
     */
    fun fromSource(docSource: DocumentSource): Configurator {
        return Configurator(docSource)
    }

    /**
     * START - scrolling in first page direction
     * END - scrolling in last page direction
     * NONE - not scrolling
     */
    internal enum class ScrollDir {
        NONE, START, END
    }

    private enum class State {
        DEFAULT, LOADED, SHOWN, ERROR
    }

    inner class Configurator(private val documentSource: DocumentSource) {
        private var pageNumbers: IntArray? = null
        private var enableSwipe = true
        private var enableDoubleTap = true
        private var onDrawListener: OnDrawListener? = null
        private var onDrawAllListener: OnDrawListener? = null
        private var onLoadCompleteListener: OnLoadCompleteListener? = null
        private var onErrorListener: OnErrorListener? = null
        private var onPageChangeListener: OnPageChangeListener? = null
        private var onPageScrollListener: OnPageScrollListener? = null
        private var onRenderListener: OnRenderListener? = null
        private var onTapListener: OnTapListener? = null
        private var onLongPressListener: OnLongPressListener? = null
        private var onPageErrorListener: OnPageErrorListener? = null
        private var linkHandler: LinkHandler = DefaultLinkHandler(this@PDFView)
        private var defaultPage = 0
        private var swipeHorizontal = false
        private var annotationRendering = false
        private var password: String? = null
        private var scrollHandle: ScrollHandle? = null
        private var antialiasing = true
        private var spacing = 0
        private var autoSpacing = false
        private var pageFitPolicy = FitPolicy.WIDTH
        private var fitEachPage = false
        private var pageFling = false
        private var pageSnap = false
        private var nightMode = false

        fun pages(vararg pageNumbers: Int): Configurator {
            this.pageNumbers = pageNumbers
            return this
        }

        fun enableSwipe(enableSwipe: Boolean): Configurator {
            this.enableSwipe = enableSwipe
            return this
        }

        fun enableDoubleTap(enableDoubleTap: Boolean): Configurator {
            this.enableDoubleTap = enableDoubleTap
            return this
        }

        fun enableAnnotationRendering(annotationRendering: Boolean): Configurator {
            this.annotationRendering = annotationRendering
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
            this.swipeHorizontal = swipeHorizontal
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
            this.antialiasing = antialiasing
            return this
        }

        fun spacing(spacing: Int): Configurator {
            this.spacing = spacing
            return this
        }

        fun autoSpacing(autoSpacing: Boolean): Configurator {
            this.autoSpacing = autoSpacing
            return this
        }

        fun pageFitPolicy(pageFitPolicy: FitPolicy): Configurator {
            this.pageFitPolicy = pageFitPolicy
            return this
        }

        fun fitEachPage(fitEachPage: Boolean): Configurator {
            this.fitEachPage = fitEachPage
            return this
        }

        fun pageSnap(pageSnap: Boolean): Configurator {
            this.pageSnap = pageSnap
            return this
        }

        fun pageFling(pageFling: Boolean): Configurator {
            this.pageFling = pageFling
            return this
        }

        fun nightMode(nightMode: Boolean): Configurator {
            this.nightMode = nightMode
            return this
        }

        fun disableLongPress(): Configurator {
            dragPinchManager!!.disableLongPress()
            return this
        }

        fun load() {
            if (!hasSize) {
                waitingDocumentConfigurator = this
                return
            }
            this@PDFView.recycle();
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
            this@PDFView.setSwipeEnabled(enableSwipe)
            this@PDFView.setNightMode(nightMode)
            this@PDFView.enableDoubleTap(enableDoubleTap)
            this@PDFView.setDefaultPage(defaultPage)
            this@PDFView.setSwipeVertical(!swipeHorizontal)
            this@PDFView.enableAnnotationRendering(annotationRendering)
            this@PDFView.setScrollHandle(scrollHandle)
            this@PDFView.enableAntialiasing(antialiasing)
            this@PDFView.setSpacing(spacing)
            this@PDFView.setAutoSpacing(autoSpacing)
            this@PDFView.setPageFitPolicy(pageFitPolicy)
            this@PDFView.setFitEachPage(fitEachPage)
            this@PDFView.setPageSnap(pageSnap)
            this@PDFView.setPageFling(pageFling)
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
        private val TAG = PDFView::class.java.simpleName
    }

    private fun initPDFView() {
        renderingHandlerThread = HandlerThread("PDF renderer")
        if (isInEditMode) {
            return
        }
        cacheManager = CacheManager()
        animationManager = AnimationManager(this)
        dragPinchManager = DragPinchManager(this, animationManager!!)
        pagesLoader = PagesLoader(this)
        paint = Paint()
        debugPaint = Paint()
        debugPaint!!.style = Paint.Style.STROKE
        setWillNotDraw(false)
    }

    init {
        initPDFView()
    }
}