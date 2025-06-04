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

class PDFView(context: Context?, set: AttributeSet?) : RelativeLayout(context, set) {
    private val antialiasFilter: PaintFlagsDrawFilter = PaintFlagsDrawFilter(
        0, Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG
    )
    private val onDrawPagesNumber: MutableList<Int> = ArrayList(10)

    private var animationManager: AnimationManager? = null
    private var currentOffsetX: Float = 0f
    private var currentOffsetY: Float = 0f
    private var currentPage: Int = 0
    private var debugPaint: Paint? = null
    private var decodingTask: DecodingTask? = null
    private var defaultPage: Int = 0
    private var dragPinchManager: DragPinchManager? = null
    private var isAnnotation: Boolean = false
    private var isAutoSpacing: Boolean = false
    private var isBestQuality: Boolean = false
    private var isDoubleTapEnabled: Boolean = true
    private var isEnableAntialiasing: Boolean = true
    private var isEnableSwipe: Boolean = true
    private var isFitEachPage: Boolean = false
    private var isHasSize: Boolean = false
    private var isNightMode: Boolean = false
    private var isPageFling: Boolean = true
    private var isPageSnap: Boolean = true
    private var isRecycled: Boolean = true
    private var isRenderDuringScale: Boolean = false
    private var isScrollHandleInit: Boolean = false
    private var isSwipeVertical: Boolean = true
    private var zoom: Float = 1f
    private var maxZoom: Float = DEFAULT_MAX_SCALE
    private var midZoom: Float = DEFAULT_MID_SCALE
    private var minZoom: Float = DEFAULT_MIN_SCALE
    private var pageFitPolicy: FitPolicy = FitPolicy.WIDTH
    private var pagesLoader: PagesLoader? = null
    private var paint: Paint? = null
    private var renderingHandlerThread: HandlerThread? = null
    private var scrollDir = ScrollDir.NONE
    private var scrollHandle: ScrollHandle? = null
    private var spacingPx: Int = 0
    private var state: State = State.DEFAULT
    private var waitingDocumentConfigurator: Configurator? = null

    var cacheManager: CacheManager? = null
    var callbacks: Callbacks = Callbacks()
    var pdfFile: PdfFile? = null
    var pdfiumCore: PdfiumCore? = null
    var renderingHandler: RenderingHandler? = null

    fun documentFitsView(): Boolean {
        val docLength = pdfFile?.docLength(zoom = 1f) ?: return false
        return if (isSwipeVertical) docLength < height else docLength < width
    }

    private fun drawPart(canvas: Canvas, part: PagePart) {
        val bitmap: Bitmap = part.renderedBitmap ?: return
        if (bitmap.isRecycled) return

        val file: PdfFile = pdfFile ?: return
        val pageBounds: RectF = part.pageBounds
        val pageSize: SizeF = file.getPageSize(pageIndex = part.page)

        val (translationX, translationY) = if (isSwipeVertical) {
            toCurrentScale(size = file.maxPageWidth - pageSize.width) / 2 to
                    file.getPageOffset(pageIndex = part.page, zoom = zoom)
        } else {
            file.getPageOffset(pageIndex = part.page, zoom = zoom) to
                    toCurrentScale(size = file.maxPageHeight - pageSize.height) / 2
        }

        canvas.translate(translationX, translationY)

        val width: Float = toCurrentScale(size = pageBounds.width() * pageSize.width)
        val height: Float = toCurrentScale(size = pageBounds.height() * pageSize.height)
        val offsetX: Float = toCurrentScale(size = pageBounds.left * pageSize.width)
        val offsetY: Float = toCurrentScale(size = pageBounds.top * pageSize.height)
        val srcRect = Rect(0, 0, bitmap.width, bitmap.height)
        val dstRect = RectF(offsetX, offsetY, offsetX + width, offsetY + height)
        val totalX: Float = currentOffsetX + translationX
        val totalY: Float = currentOffsetY + translationY

        if (totalX + dstRect.left >= this.width
            || totalX + dstRect.right <= 0
            || totalY + dstRect.top >= this.height
            || totalY + dstRect.bottom <= 0
        ) {
            canvas.translate(-translationX, -translationY)
            return
        }

        canvas.drawBitmap(bitmap, srcRect, dstRect, paint)

        if (PdfConstants.DEBUG_MODE) {
            debugPaint?.color = if (part.page % 2 == 0) Color.RED else Color.BLUE
            debugPaint?.let { canvas.drawRect(dstRect, it) }
        }
        canvas.translate(-translationX, -translationY)
    }

    private fun drawWithListener(canvas: Canvas, page: Int, listener: OnDrawListener?) {
        listener ?: return
        val file: PdfFile = pdfFile ?: return

        val (translateX, translateY) = if (isSwipeVertical) {
            0f to file.getPageOffset(pageIndex = page, zoom = zoom)
        } else {
            file.getPageOffset(pageIndex = page, zoom = zoom) to 0f
        }
        canvas.translate(translateX, translateY)

        file.getPageSize(pageIndex = page).let { size ->
            listener.onLayerDrawn(
                canvas = canvas,
                pageWidth = toCurrentScale(size = size.width),
                pageHeight = toCurrentScale(size = size.height),
                currentPage = page
            )
        }
        canvas.translate(-translateX, -translateY)
    }

    fun findFocusPage(xOffset: Float, yOffset: Float): Int {
        val currentOffset: Float = if (isSwipeVertical) yOffset else xOffset
        val file: PdfFile = pdfFile ?: return 0
        val length: Float = if (isSwipeVertical) height.toFloat() else width.toFloat()

        return when {
            currentOffset > -1 -> 0
            currentOffset < -file.docLength(zoom = zoom) + length + 1 -> file.pagesCount - 1
            else -> {
                val center = currentOffset - length / 2f
                file.getPageAtOffset(offset = -center, zoom = zoom)
            }
        }
    }

    fun findSnapEdge(page: Int): SnapEdge {
        if (!isPageSnap || page < 0) return SnapEdge.NONE
        val file: PdfFile = pdfFile ?: return SnapEdge.NONE

        val currentOffset: Float = if (isSwipeVertical) currentOffsetY else currentOffsetX
        val length: Int = if (isSwipeVertical) height else width
        val offset: Float = -file.getPageOffset(pageIndex = page, zoom = zoom)
        val pageLength: Float = file.getPageLength(pageIndex = page, zoom = zoom)

        return when {
            length >= pageLength -> SnapEdge.CENTER
            currentOffset >= offset -> SnapEdge.START
            offset - pageLength > currentOffset - length -> SnapEdge.END
            else -> SnapEdge.NONE
        }
    }

    fun fitToWidth(page: Int) {
        if (state != State.SHOWN) {
            Log.v(PdfConstants.TAG, "Cannot fit, document not rendered yet")
            return
        }

        val file: PdfFile = pdfFile ?: run {
            Log.w(PdfConstants.TAG, "Cannot fit, PDF file is null")
            return
        }

        val pageSize: SizeF = file.getPageSize(pageIndex = page)
        zoomTo(zoom = width / pageSize.width)
        jumpTo(page = page, withAnimation = false)
    }

    fun getPageAtPositionOffset(positionOffset: Float): Int {
        val file: PdfFile = pdfFile ?: return 0
        return file.getPageAtOffset(offset = file.docLength(zoom = zoom) * positionOffset, zoom = zoom)
    }

    fun getPageRotation(pageIndex: Int): Int {
        val file: PdfFile = pdfFile ?: return 0
        return file.getPageRotation(pageIndex = pageIndex)
    }

    fun getPageSize(pageIndex: Int): SizeF {
        val file: PdfFile = pdfFile ?: return SizeF(width = 0f, height = 0f)
        return file.getPageSize(pageIndex = pageIndex)
    }

    fun getPositionOffset(): Float {
        val file: PdfFile = pdfFile ?: return 0f

        val docLength: Float = file.docLength(zoom = zoom)
        val offset: Float = if (isSwipeVertical) {
            -currentOffsetY / (docLength - height)
        } else {
            -currentOffsetX / (docLength - width)
        }
        return MathUtils.limit(number = offset, between = 0f, and = 1f)
    }

    @JvmOverloads
    fun jumpTo(page: Int, withAnimation: Boolean = false) {
        val file: PdfFile = pdfFile ?: return
        val pageNumber: Int = file.ensureValidPageNumber(userPage = page)

        var offset: Float = if (pageNumber == 0) 0f else -file.getPageOffset(pageIndex = pageNumber, zoom = zoom)
        offset += file.getPageSpacing(pageIndex = pageNumber, zoom = zoom) / 2f

        when {
            isSwipeVertical && withAnimation -> animationManager?.startYAnimation(yFrom = currentOffsetY, yTo = offset)
            isSwipeVertical -> moveTo(offsetX = currentOffsetX, offsetY = offset)
            withAnimation -> animationManager?.startXAnimation(xFrom = currentOffsetX, xTo = offset)
            else -> moveTo(offsetX = offset, offsetY = currentOffsetY)
        }
        showPage(page = pageNumber)
    }

    private fun load(docSource: DocumentSource, password: String?, userPages: IntArray? = null) {
        check(value = isRecycled) { "Don't call load on a PDF View without recycling it first." }
        isRecycled = false
        pdfiumCore?.let {
            decodingTask = DecodingTask(
                docSource = docSource,
                password = password,
                userPages = userPages,
                pdfView = this@PDFView
            ).apply {
                execute()
            }
        }
    }

    fun loadComplete(pdfFile: PdfFile) {
        state = State.LOADED
        this.pdfFile = pdfFile

        renderingHandlerThread?.takeIf { !it.isAlive }?.start()

        renderingHandlerThread?.looper?.let { looper ->
            renderingHandler = RenderingHandler(looper = looper, pdfView = this@PDFView).apply {
                start()
            }
        }

        scrollHandle?.takeIf { !isScrollHandleInit }?.also { handle ->
            handle.setupLayout(pdfView = this@PDFView)
            isScrollHandleInit = true
        }

        dragPinchManager?.enable()
        callbacks.callOnLoadComplete(totalPages = pdfFile.pagesCount)
        jumpTo(page = defaultPage, withAnimation = false)
    }

    fun loadError(error: Throwable?) {
        state = State.ERROR
        callbacks.onError?.onError(t = error) ?: Log.e(PdfConstants.TAG, "Load PDF error: ", error)
        recycle()
        invalidate()
    }

    fun onPageError(ex: PageRenderingException) {
        if (!callbacks.callOnPageError(page = ex.page, error = ex.cause)) {
            Log.e(PdfConstants.TAG, "Cannot open page: ${ex.page}", ex.cause)
        }
    }

    fun loadPageByOffset() {
        val file: PdfFile = pdfFile ?: return
        if (file.pagesCount == 0) return

        val (offset, centerOfScreen) = if (isSwipeVertical) {
            currentOffsetY to height / 2f
        } else {
            currentOffsetX to width / 2f
        }

        val page: Int = file.getPageAtOffset(offset = -(offset - centerOfScreen), zoom = zoom)
        when {
            page in 0 until file.pagesCount && page != currentPage -> showPage(page = page)
            else -> loadPages()
        }
    }

    fun loadPages() {
        if (pdfFile == null || renderingHandler == null) return
        renderingHandler?.removeMessages(RenderingHandler.MSG_RENDER_PART_TASK)
        cacheManager?.makeNewSet()
        pagesLoader?.loadPages()
        reDraw()
    }

    fun moveRelativeTo(dx: Float, dy: Float) {
        moveTo(offsetX = currentOffsetX + dx, offsetY = currentOffsetY + dy)
    }

    @JvmOverloads
    fun moveTo(offsetX: Float, offsetY: Float, moveHandle: Boolean = true) {
        val file: PdfFile = pdfFile ?: return

        var newOffsetX: Float = offsetX
        var newOffsetY: Float = offsetY
        val positionOffset: Float = getPositionOffset()

        if (isSwipeVertical) {
            val scaledPageWidth: Float = toCurrentScale(size = file.maxPageWidth)
            newOffsetX = when {
                scaledPageWidth < width -> width / 2f - scaledPageWidth / 2
                newOffsetX > 0 -> 0f
                newOffsetX + scaledPageWidth < width -> width - scaledPageWidth
                else -> newOffsetX
            }

            val contentHeight: Float = file.docLength(zoom = zoom)
            newOffsetY = when {
                contentHeight < height -> (height - contentHeight) / 2
                newOffsetY > 0 -> 0f
                newOffsetY + contentHeight < height -> -contentHeight + height
                else -> newOffsetY
            }

            scrollDir = when {
                newOffsetY < currentOffsetY -> ScrollDir.END
                newOffsetY > currentOffsetY -> ScrollDir.START
                else -> ScrollDir.NONE
            }
        } else {
            val scaledPageHeight: Float = toCurrentScale(file.maxPageHeight)
            newOffsetY = when {
                scaledPageHeight < height -> height / 2f - scaledPageHeight / 2
                newOffsetY > 0 -> 0f
                newOffsetY + scaledPageHeight < height -> height - scaledPageHeight
                else -> newOffsetY
            }

            val contentWidth: Float = file.docLength(zoom)
            newOffsetX = when {
                contentWidth < width -> (width - contentWidth) / 2
                newOffsetX > 0 -> 0f
                newOffsetX + contentWidth < width -> -contentWidth + width
                else -> newOffsetX
            }

            scrollDir = when {
                newOffsetX < currentOffsetX -> ScrollDir.END
                newOffsetX > currentOffsetX -> ScrollDir.START
                else -> ScrollDir.NONE
            }
        }

        currentOffsetX = newOffsetX
        currentOffsetY = newOffsetY

        if (moveHandle && !documentFitsView()) {
            scrollHandle?.setScroll(position = positionOffset)
        }

        callbacks.callOnPageScroll(currentPage = currentPage, offset = positionOffset)
        reDraw()

        val focusedPage: Int = findFocusPage(xOffset = currentOffsetX, yOffset = currentOffsetY)
        if (focusedPage in 0 until file.pagesCount && focusedPage != currentPage) {
            showPage(page = focusedPage)
        }
    }

    fun onBitmapRendered(part: PagePart) {
        val file: PdfFile = pdfFile ?: return

        if (state == State.LOADED) {
            state = State.SHOWN
            callbacks.callOnRender(totalPages = file.pagesCount)
        }

        if (part.isThumbnail) {
            cacheManager?.cacheThumbnail(part = part)
        } else {
            cacheManager?.cachePart(part = part)
        }
        reDraw()
    }

    fun pageFillsScreen(): Boolean {
        val file: PdfFile = pdfFile ?: return false
        val start: Float = -file.getPageOffset(pageIndex = currentPage, zoom = zoom)
        val end: Float = start - file.getPageLength(pageIndex = currentPage, zoom = zoom)

        return if (isSwipeVertical()) {
            start > currentOffsetY && end < currentOffsetY - height
        } else {
            start > currentOffsetX && end < currentOffsetX - width
        }
    }

    fun performPageSnap() {
        if (!isPageSnap) return
        val file: PdfFile = pdfFile ?: return
        if (file.pagesCount == 0) return

        val centerPage: Int = findFocusPage(xOffset = currentOffsetX, yOffset = currentOffsetY)
        val edge: SnapEdge = findSnapEdge(page = centerPage)
        if (edge == SnapEdge.NONE) return
        val offset: Float = snapOffsetForPage(pageIndex = centerPage, edge = edge)
        if (isSwipeVertical) {
            animationManager?.startYAnimation(yFrom = currentOffsetY, yTo = -offset)
        } else {
            animationManager?.startXAnimation(xFrom = currentOffsetX, xTo = -offset)
        }
    }

    fun recycle() {
        waitingDocumentConfigurator = null

        animationManager?.stopAll()
        dragPinchManager?.disable()

        renderingHandler?.let { handler ->
            handler.stop()
            handler.removeMessages(RenderingHandler.MSG_RENDER_PART_TASK)
        }

        decodingTask?.cancel()
        cacheManager?.clearAll()

        scrollHandle?.takeIf { isScrollHandleInit }?.destroyLayout()

        pdfFile?.let { file ->
            file.dispose()
            pdfFile = null
        }

        renderingHandler = null
        scrollHandle = null
        isScrollHandleInit = false
        currentOffsetY = 0f
        currentOffsetX = currentOffsetY
        zoom = 1f
        isRecycled = true
        callbacks = Callbacks()
        state = State.DEFAULT
    }

    fun setPositionOffset(progress: Float, moveHandle: Boolean) {
        val file: PdfFile = pdfFile ?: return
        val docLength: Float = file.docLength(zoom = zoom)

        if (isSwipeVertical) {
            moveTo(
                offsetX = currentOffsetX,
                offsetY = (-docLength + height) * progress,
                moveHandle = moveHandle
            )
        } else {
            moveTo(
                offsetX = (-docLength + width) * progress,
                offsetY = currentOffsetY,
                moveHandle = moveHandle
            )
        }
        loadPageByOffset()
    }

    private fun showPage(page: Int) {
        if (isRecycled) return
        val file: PdfFile = pdfFile ?: return

        val pageNumber: Int = file.ensureValidPageNumber(userPage = page)
        currentPage = pageNumber
        loadPages()

        if (!documentFitsView()) {
            scrollHandle?.setPageNumber(pageNumber = currentPage + 1)
        }

        callbacks.callOnPageChange(page = currentPage, totalPages = file.pagesCount)
    }

    fun snapOffsetForPage(pageIndex: Int, edge: SnapEdge): Float {
        val file: PdfFile = pdfFile ?: return 0f
        val length: Float = if (isSwipeVertical) height.toFloat() else width.toFloat()

        val offset: Float = file.getPageOffset(pageIndex = pageIndex, zoom = zoom)
        val pageLength: Float = file.getPageLength(pageIndex = pageIndex, zoom = zoom)

        return when (edge) {
            SnapEdge.CENTER -> offset - length / 2f + pageLength / 2f
            SnapEdge.END -> offset - length + pageLength
            else -> offset
        }
    }

    fun zoomCenteredRelativeTo(zoom: Float, pivot: PointF) {
        zoomCenteredTo(zoom = this.zoom * zoom, pivot = pivot)
    }

    fun zoomCenteredTo(zoom: Float, pivot: PointF) {
        val zoomRatio: Float = zoom / this.zoom
        zoomTo(zoom = zoom)

        var baseX: Float = currentOffsetX * zoomRatio
        var baseY: Float = currentOffsetY * zoomRatio

        baseX += pivot.x - pivot.x * zoomRatio
        baseY += pivot.y - pivot.y * zoomRatio

        moveTo(offsetX = baseX, offsetY = baseY)
    }

    fun zoomTo(zoom: Float) {
        this.zoom = zoom
    }

    fun zoomWithAnimation(centerX: Float, centerY: Float, scale: Float) {
        animationManager?.startZoomAnimation(
            centerX = centerX,
            centerY = centerY,
            zoomFrom = zoom,
            zoomTo = scale
        )
    }

    fun zoomWithAnimation(scale: Float) {
        animationManager?.startZoomAnimation(
            centerX = width / 2f,
            centerY = height / 2f,
            zoomFrom = zoom,
            zoomTo = scale
        )
    }

    //Setter
    fun setAnnotation(enabled: Boolean) {
        isAnnotation = enabled
    }

    fun setAntialiasing(enabled: Boolean) {
        isEnableAntialiasing = enabled
    }

    fun setAutoSpacing(enabled: Boolean) {
        isAutoSpacing = enabled
    }

    fun setBestQuality(enabled: Boolean) {
        isBestQuality = enabled
    }

    fun setDefaultPage(page: Int) {
        defaultPage = page
    }

    fun setDoubleTap(enabled: Boolean) {
        isDoubleTapEnabled = enabled
    }

    fun setFitEachPage(enabled: Boolean) {
        isFitEachPage = enabled
    }

    fun setMaxZoom(zoom: Float) {
        maxZoom = zoom
    }

    fun setMidZoom(zoom: Float) {
        midZoom = zoom
    }

    fun setMinZoom(zoom: Float) {
        minZoom = zoom
    }

    fun setNightMode(enabled: Boolean) {
        isNightMode = enabled
    }

    fun setPageFitPolicy(policy: FitPolicy) {
        pageFitPolicy = policy
    }

    fun setPageFling(enabled: Boolean) {
        isPageFling = enabled
    }

    fun setPageSnap(enabled: Boolean) {
        isPageSnap = enabled
    }

    fun setRenderDuringScale(enabled: Boolean) {
        isRenderDuringScale = enabled
    }

    fun setScrollHandle(handle: ScrollHandle?) {
        scrollHandle = handle
    }

    fun setSpacing(spacingDp: Int) {
        spacingPx = PdfUtils.getDP(context = context, dp = spacingDp)
    }

    fun setSwipeEnabled(enabled: Boolean) {
        isEnableSwipe = enabled
    }

    fun setSwipeVertical(enabled: Boolean) {
        isSwipeVertical = enabled
    }

    //Getter
    fun bookmarks(): List<PdfDocument.Bookmark> = pdfFile?.bookmarks() ?: emptyList()
    fun documentMeta(): PdfDocument.Meta? = pdfFile?.metaData()
    fun getCurrentPage(): Int = currentPage
    fun getCurrentXOffset(): Float = currentOffsetX
    fun getCurrentYOffset(): Float = currentOffsetY
    fun getMaxZoom(): Float = maxZoom
    fun getMidZoom(): Float = midZoom
    fun getMinZoom(): Float = minZoom
    fun getPageCount(): Int = pdfFile?.pagesCount ?: 0
    fun getPageFitPolicy(): FitPolicy = pageFitPolicy
    fun getScrollHandle(): ScrollHandle? = scrollHandle
    fun getSpacingPx(): Int = spacingPx
    fun getZoom(): Float = zoom
    fun isAnnotationRendering(): Boolean = isAnnotation
    fun isAntialiasing(): Boolean = isEnableAntialiasing
    fun isAutoSpacingEnabled(): Boolean = isAutoSpacing
    fun isBestQuality(): Boolean = isBestQuality
    fun isDoubleTapEnabled(): Boolean = isDoubleTapEnabled
    fun isFitEachPage(): Boolean = isFitEachPage
    fun isNightMode(): Boolean = isNightMode
    fun isPageFlingEnabled(): Boolean = isPageFling
    fun isPageSnap(): Boolean = isPageSnap
    fun isRecycled(): Boolean = isRecycled
    fun isRenderDuringScale(): Boolean = isRenderDuringScale
    fun isSwipeEnabled(): Boolean = isEnableSwipe
    fun isSwipeVertical(): Boolean = isSwipeVertical
    fun isZooming(): Boolean = zoom != minZoom
    fun totalPages(): Int = pdfFile?.totalPages() ?: 0

    //Others
    fun resetZoom(): Unit = zoomTo(zoom = minZoom)
    fun resetZoomWithAnimation(): Unit = zoomWithAnimation(scale = minZoom)
    fun stopFling(): Unit? = animationManager?.stopFling()
    fun toCurrentScale(size: Float): Float = size * zoom
    fun toRealScale(size: Float): Float = size / zoom
    private fun reDraw(): Unit = invalidate()

    override fun canScrollHorizontally(direction: Int): Boolean {
        val file: PdfFile = pdfFile ?: return true

        return if (isSwipeVertical) {
            when {
                direction < 0 && currentOffsetX < 0 -> true
                direction > 0 -> currentOffsetX + toCurrentScale(size = file.maxPageWidth) > width
                else -> false
            }
        } else {
            when {
                direction < 0 && currentOffsetX < 0 -> true
                direction > 0 -> currentOffsetX + file.docLength(zoom = zoom) > width
                else -> false
            }
        }
    }


    override fun canScrollVertically(direction: Int): Boolean {
        val file: PdfFile = pdfFile ?: return true

        return if (isSwipeVertical) {
            when {
                direction < 0 && currentOffsetY < 0 -> true
                direction > 0 -> currentOffsetY + file.docLength(zoom = zoom) > height
                else -> false
            }
        } else {
            when {
                direction < 0 && currentOffsetY < 0 -> true
                direction > 0 -> currentOffsetY + toCurrentScale(size = file.maxPageHeight) > height
                else -> false
            }
        }
    }

    override fun computeScroll() {
        super.computeScroll()
        if (isInEditMode) return
        animationManager?.computeFling()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (renderingHandlerThread == null) {
            renderingHandlerThread = HandlerThread("PDF renderer")
        }
    }

    override fun onDetachedFromWindow() {
        recycle()
        renderingHandlerThread?.quitSafely()
        renderingHandlerThread = null
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        if (isInEditMode) return

        if (isEnableAntialiasing) {
            canvas.drawFilter = antialiasFilter
        }

        background?.draw(canvas) ?: canvas.drawColor(if (isNightMode) Color.BLACK else Color.WHITE)

        if (isRecycled || state != State.SHOWN) return

        canvas.translate(currentOffsetX, currentOffsetY)

        cacheManager?.let { manager ->
            manager.allThumbnails.forEach { part ->
                drawPart(canvas = canvas, part = part)
            }
            manager.pageParts.forEach { part ->
                drawPart(canvas = canvas, part = part)
                callbacks.onDrawAll?.takeIf { !onDrawPagesNumber.contains(part.page) }?.let {
                    onDrawPagesNumber.add(part.page)
                }
            }
        }

        canvas.translate(-currentOffsetX, -currentOffsetY)

        onDrawPagesNumber.forEach { page ->
            drawWithListener(canvas = canvas, page = page, listener = callbacks.onDrawAll)
        }
        onDrawPagesNumber.clear()
        drawWithListener(canvas = canvas, page = currentPage, listener = callbacks.onDraw)
    }

    override fun onSizeChanged(w: Int, h: Int, oldW: Int, oldH: Int) {
        isHasSize = true
        waitingDocumentConfigurator?.load()

        if (isInEditMode || state != State.SHOWN) return
        val file: PdfFile = pdfFile ?: return

        val centerPointOffsetX: Float = -currentOffsetX + oldW * 0.5f
        val centerPointOffsetY: Float = -currentOffsetY + oldH * 0.5f

        val (relativeX, relativeY) = if (isSwipeVertical) {
            centerPointOffsetX / file.maxPageWidth to centerPointOffsetY / file.docLength(zoom = zoom)
        } else {
            centerPointOffsetX / file.docLength(zoom = zoom) to centerPointOffsetY / file.maxPageHeight
        }

        animationManager?.stopAll()
        file.recalculatePageSizes(viewSize = Size(width = w, height = h))

        val (newOffsetX, newOffsetY) = if (isSwipeVertical) {
            -relativeX * file.maxPageWidth + w * 0.5f to -relativeY * file.docLength(zoom = zoom) + h * 0.5f
        } else {
            -relativeX * file.docLength(zoom = zoom) + w * 0.5f to -relativeY * file.maxPageHeight + h * 0.5f
        }

        currentOffsetX = newOffsetX
        currentOffsetY = newOffsetY
        moveTo(currentOffsetX, currentOffsetY)
        loadPageByOffset()
    }

    fun fromAsset(name: String?): Configurator {
        requireNotNull(value = name) { "Asset name must not be null" }
        return Configurator(documentSource = AssetSource(name = name))
    }

    fun fromBytes(bytes: ByteArray?): Configurator {
        requireNotNull(value = bytes) { "Byte array must not be null" }
        return Configurator(documentSource = ByteArraySource(data = bytes))
    }

    fun fromFile(file: File?): Configurator {
        requireNotNull(value = file) { "File must not be null" }
        return Configurator(documentSource = FileSource(file = file))
    }

    fun fromSource(documentSource: DocumentSource): Configurator {
        return Configurator(documentSource = documentSource)
    }

    fun fromStream(inputStream: InputStream?): Configurator {
        requireNotNull(value = inputStream) { "InputStream must not be null" }
        return Configurator(documentSource = InputStreamSource(inputStream = inputStream))
    }

    fun fromUri(uri: Uri?): Configurator {
        requireNotNull(value = uri) { "Uri must not be null" }
        return Configurator(documentSource = UriSource(uri = uri))
    }

    inner class Configurator(private val documentSource: DocumentSource) {
        private var defaultPage: Int = 0
        private var isAnnotation: Boolean = false
        private var isAntialiasing: Boolean = true
        private var isAutoSpacing: Boolean = false
        private var isDoubleTapEnabled: Boolean = true
        private var isFitEachPage: Boolean = false
        private var isNightMode: Boolean = false
        private var isPageFling: Boolean = false
        private var isPageSnap: Boolean = false
        private var isSwipeEnabled: Boolean = true
        private var isSwipeHorizontal: Boolean = false
        private var linkHandler: LinkHandler = DefaultLinkHandler(pdfView = this@PDFView)
        private var pageFitPolicy: FitPolicy = FitPolicy.WIDTH
        private var pageNumbers: IntArray? = null
        private var password: String? = null
        private var scrollHandle: ScrollHandle? = null
        private var spacing: Int = 0

        // Listeners
        private var onDrawListener: OnDrawListener? = null
        private var onDrawAllListener: OnDrawListener? = null
        private var onErrorListener: OnErrorListener? = null
        private var onLoadCompleteListener: OnLoadCompleteListener? = null
        private var onLongPressListener: OnLongPressListener? = null
        private var onPageChangeListener: OnPageChangeListener? = null
        private var onPageErrorListener: OnPageErrorListener? = null
        private var onPageScrollListener: OnPageScrollListener? = null
        private var onRenderListener: OnRenderListener? = null
        private var onTapListener: OnTapListener? = null

        fun autoSpacing(enable: Boolean) = apply { isAutoSpacing = enable }
        fun defaultPage(page: Int) = apply { defaultPage = page }
        fun disableLongPress() = apply { dragPinchManager?.disableLongPress() }
        fun enableAnnotationRendering(enable: Boolean) = apply { isAnnotation = enable }
        fun enableAntialiasing(enable: Boolean) = apply { isAntialiasing = enable }
        fun enableDoubleTap(enable: Boolean) = apply { isDoubleTapEnabled = enable }
        fun enableSwipe(enable: Boolean) = apply { isSwipeEnabled = enable }
        fun fitEachPage(enable: Boolean) = apply { isFitEachPage = enable }
        fun linkHandler(handler: LinkHandler) = apply { linkHandler = handler }
        fun nightMode(enable: Boolean) = apply { isNightMode = enable }
        fun onDraw(listener: OnDrawListener?) = apply { onDrawListener = listener }
        fun onDrawAll(listener: OnDrawListener?) = apply { onDrawAllListener = listener }
        fun onError(listener: OnErrorListener?) = apply { onErrorListener = listener }
        fun onLoad(listener: OnLoadCompleteListener?) = apply { onLoadCompleteListener = listener }
        fun onLongPress(listener: OnLongPressListener?) = apply { onLongPressListener = listener }
        fun onPageChange(listener: OnPageChangeListener?) = apply { onPageChangeListener = listener }
        fun onPageError(listener: OnPageErrorListener?) = apply { onPageErrorListener = listener }
        fun onPageScroll(listener: OnPageScrollListener?) = apply { onPageScrollListener = listener }
        fun onRender(listener: OnRenderListener?) = apply { onRenderListener = listener }
        fun onTap(listener: OnTapListener?) = apply { onTapListener = listener }
        fun pageFitPolicy(policy: FitPolicy) = apply { pageFitPolicy = policy }
        fun pageFling(enable: Boolean) = apply { isPageFling = enable }
        fun pages(vararg pageNumbers: Int) = apply { this.pageNumbers = pageNumbers }
        fun pageSnap(enable: Boolean) = apply { isPageSnap = enable }
        fun password(password: String?) = apply { this.password = password }
        fun scrollHandle(handle: ScrollHandle?) = apply { scrollHandle = handle }
        fun spacing(spacing: Int) = apply { this.spacing = spacing }
        fun swipeHorizontal(horizontal: Boolean) = apply { isSwipeHorizontal = horizontal }

        fun load() {
            if (!isHasSize) {
                waitingDocumentConfigurator = this@Configurator
                return
            }
            recycle()
            callbacks.apply {
                onDraw = onDrawListener
                onDrawAll = onDrawAllListener
                onError = onErrorListener
                setLinkHandler(linkHandler = linkHandler)
                setOnLoadComplete(listener = onLoadCompleteListener)
                setOnLongPress(listener = onLongPressListener)
                setOnPageChange(listener = onPageChangeListener)
                setOnPageError(listener = onPageErrorListener)
                setOnPageScroll(listener = onPageScrollListener)
                setOnRender(listener = onRenderListener)
                setOnTap(listener = onTapListener)
            }
            setAnnotation(enabled = isAnnotation)
            setAntialiasing(enabled = isAntialiasing)
            setAutoSpacing(enabled = isAutoSpacing)
            setDefaultPage(page = defaultPage)
            setDoubleTap(enabled = isDoubleTapEnabled)
            setFitEachPage(enabled = isFitEachPage)
            setNightMode(enabled = isNightMode)
            setPageFitPolicy(policy = pageFitPolicy)
            setPageFling(enabled = isPageFling)
            setPageSnap(enabled = isPageSnap)
            setScrollHandle(handle = scrollHandle)
            setSpacing(spacingDp = spacing)
            setSwipeEnabled(enabled = isSwipeEnabled)
            setSwipeVertical(enabled = !isSwipeHorizontal)
            if (pageNumbers != null) {
                load(docSource = documentSource, password = password, userPages = pageNumbers)
            } else {
                load(docSource = documentSource, password = password)
            }
        }
    }

    companion object {
        const val DEFAULT_MAX_SCALE: Float = 3.0f
        const val DEFAULT_MID_SCALE: Float = 1.75f
        const val DEFAULT_MIN_SCALE: Float = 1.0f
    }

    private fun initPDFView() {
        if (isInEditMode) return
        pdfiumCore = PdfiumCore(context = context)
        cacheManager = CacheManager()
        animationManager = AnimationManager(pdfView = this@PDFView).also {
            dragPinchManager = DragPinchManager(pdfView = this@PDFView, animationManager = it)
        }
        pagesLoader = PagesLoader(pdfView = this@PDFView)
        paint = Paint()
        debugPaint = Paint().apply {
            style = Paint.Style.STROKE
        }
        setWillNotDraw(false)
    }

    init {
        initPDFView()
    }
}