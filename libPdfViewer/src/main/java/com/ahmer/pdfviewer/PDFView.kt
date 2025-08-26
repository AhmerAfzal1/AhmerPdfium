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
import android.util.AttributeSet
import android.util.Log
import android.widget.RelativeLayout
import com.ahmer.pdfium.PdfDocument
import com.ahmer.pdfium.PdfTextPage
import com.ahmer.pdfium.PdfiumCore
import com.ahmer.pdfium.util.Size
import com.ahmer.pdfium.util.SizeF
import com.ahmer.pdfviewer.PDFView.Companion.FPDF_INCREMENTAL
import com.ahmer.pdfviewer.PDFView.Companion.FPDF_NO_INCREMENTAL
import com.ahmer.pdfviewer.PDFView.Companion.FPDF_REMOVE_SECURITY
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
import com.ahmer.pdfviewer.util.PdfConstants
import com.ahmer.pdfviewer.util.PdfUtils
import com.ahmer.pdfviewer.util.ScrollDir
import com.ahmer.pdfviewer.util.SnapEdge
import com.ahmer.pdfviewer.util.State
import java.io.File
import java.io.InputStream
import java.io.OutputStream

class PDFView(context: Context?, set: AttributeSet?) : RelativeLayout(context, set) {
    private val _antialiasFilter: PaintFlagsDrawFilter = PaintFlagsDrawFilter(
        0, Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG
    )
    private val _onDrawPagesNumber: MutableList<Int> = ArrayList(10)

    private var _animationManager: AnimationManager? = null
    private var _currentPage: Int = 0
    private var _currentXOffset: Float = 0f
    private var _currentYOffset: Float = 0f
    private var _debugPaint: Paint? = null
    private var _decodingTask: DecodingTask? = null
    private var _defaultPage: Int = 0
    private var _dragPinchManager: DragPinchManager? = null
    private var _isAnnotation: Boolean = false
    private var _isAutoSpacing: Boolean = false
    private var _isBestQuality: Boolean = false
    private var _isDoubleTapEnabled: Boolean = true
    private var _isEnableAntialiasing: Boolean = true
    private var _isEnableSwipe: Boolean = true
    private var _isFitEachPage: Boolean = false
    private var _isHasSize: Boolean = false
    private var _isNightMode: Boolean = false
    private var _isPageFling: Boolean = true
    private var _isPageSnap: Boolean = true
    private var _isRecycled: Boolean = true
    private var _isRenderDuringScale: Boolean = false
    private var _isScrollHandleInit: Boolean = false
    private var _isSwipeVertical: Boolean = true
    private var _pageFitPolicy: FitPolicy = FitPolicy.WIDTH
    private var _pagesLoader: PagesLoader? = null
    private var _paint: Paint? = null
    private var _scrollDir: ScrollDir = ScrollDir.NONE
    private var _scrollHandle: ScrollHandle? = null
    private var _spacingPx: Int = 0
    private var _state: State = State.DEFAULT
    private var _waitingDocumentConfigurator: Configurator? = null
    private var _zoom: Float = 1f
    private var _zoomMax: Float = DEFAULT_MAX_SCALE
    private var _zoomMid: Float = DEFAULT_MID_SCALE
    private var _zoomMin: Float = DEFAULT_MIN_SCALE

    var cacheManager: CacheManager? = null
    var callbacks: Callbacks = Callbacks()
    var pdfFile: PdfFile? = null
    var pdfiumCore: PdfiumCore? = null
    var renderingHandler: RenderingHandler? = null

    fun documentFitsView(): Boolean {
        val docLength: Float = pdfFile?.docLength(zoom = 1f) ?: return false
        return if (_isSwipeVertical) docLength < height else docLength < width
    }

    private fun drawPart(canvas: Canvas, part: PagePart) {
        val bitmap: Bitmap = part.renderedBitmap ?: return
        if (bitmap.isRecycled) return

        val file: PdfFile = pdfFile ?: return
        val pageBounds: RectF = part.pageBounds
        val pageSize: SizeF = file.getPageSize(pageIndex = part.page)

        val (translationX, translationY) = if (_isSwipeVertical) {
            toCurrentScale(size = file.maxPageWidth - pageSize.width) / 2 to
                    file.getPageOffset(pageIndex = part.page, zoom = _zoom)
        } else {
            file.getPageOffset(pageIndex = part.page, zoom = _zoom) to
                    toCurrentScale(size = file.maxPageHeight - pageSize.height) / 2
        }

        canvas.translate(translationX, translationY)

        val width: Float = toCurrentScale(size = pageBounds.width() * pageSize.width)
        val height: Float = toCurrentScale(size = pageBounds.height() * pageSize.height)
        val offsetX: Float = toCurrentScale(size = pageBounds.left * pageSize.width)
        val offsetY: Float = toCurrentScale(size = pageBounds.top * pageSize.height)
        val srcRect = Rect(0, 0, bitmap.width, bitmap.height)
        val dstRect = RectF(offsetX, offsetY, offsetX + width, offsetY + height)
        val totalX: Float = _currentXOffset + translationX
        val totalY: Float = _currentYOffset + translationY

        if (totalX + dstRect.left >= this.width
            || totalX + dstRect.right <= 0
            || totalY + dstRect.top >= this.height
            || totalY + dstRect.bottom <= 0
        ) {
            canvas.translate(-translationX, -translationY)
            return
        }

        canvas.drawBitmap(bitmap, srcRect, dstRect, _paint)

        if (PdfConstants.DEBUG_MODE) {
            _debugPaint?.color = if (part.page % 2 == 0) Color.RED else Color.BLUE
            _debugPaint?.let { canvas.drawRect(dstRect, it) }
        }
        canvas.translate(-translationX, -translationY)
    }

    private fun drawWithListener(canvas: Canvas, page: Int, listener: OnDrawListener?) {
        listener ?: return
        val file: PdfFile = pdfFile ?: return

        val (translateX, translateY) = if (_isSwipeVertical) {
            0f to file.getPageOffset(pageIndex = page, zoom = _zoom)
        } else {
            file.getPageOffset(pageIndex = page, zoom = _zoom) to 0f
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
        val currentOffset: Float = if (_isSwipeVertical) yOffset else xOffset
        val file: PdfFile = pdfFile ?: return 0
        val length: Float = if (_isSwipeVertical) height.toFloat() else width.toFloat()

        return when {
            currentOffset > -1 -> 0
            currentOffset < -file.docLength(zoom = _zoom) + length + 1 -> file.pagesCount - 1
            else -> {
                val center: Float = currentOffset - length / 2f
                file.getPageAtOffset(offset = -center, zoom = _zoom)
            }
        }
    }

    fun findSnapEdge(page: Int): SnapEdge {
        if (!_isPageSnap || page < 0) return SnapEdge.NONE
        val file: PdfFile = pdfFile ?: return SnapEdge.NONE

        val currentOffset: Float = if (_isSwipeVertical) _currentYOffset else _currentXOffset
        val length: Int = if (_isSwipeVertical) height else width
        val offset: Float = -file.getPageOffset(pageIndex = page, zoom = _zoom)
        val pageLength: Float = file.getPageLength(pageIndex = page, zoom = _zoom)

        return when {
            length >= pageLength -> SnapEdge.CENTER
            currentOffset >= offset -> SnapEdge.START
            offset - pageLength > currentOffset - length -> SnapEdge.END
            else -> SnapEdge.NONE
        }
    }

    fun fitToWidth(page: Int) {
        if (_state != State.SHOWN) {
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
        return file.getPageAtOffset(offset = file.docLength(zoom = _zoom) * positionOffset, zoom = _zoom)
    }

    fun pageRotation(pageIndex: Int): Int {
        val file: PdfFile = pdfFile ?: return 0
        return file.pageRotation(pageIndex = pageIndex)
    }

    fun openPage(pageIndex: Int): Long {
        val file: PdfFile = pdfFile ?: return 0L
        return file.openDocPage(pageIndex = pageIndex)
    }

    fun openTextPage(pageIndex: Int): PdfTextPage? {
        return pdfFile?.openTextPage(pageIndex = pageIndex)
    }

    fun deletePage(pageIndex: Int) {
        val file: PdfFile = pdfFile ?: return
        return file.deletePage(pageIndex = pageIndex)
    }

    fun getPageSize(pageIndex: Int): SizeF {
        val file: PdfFile = pdfFile ?: return SizeF(width = 0f, height = 0f)
        return file.getPageSize(pageIndex = pageIndex)
    }

    fun getPositionOffset(): Float {
        val file: PdfFile = pdfFile ?: return 0f

        val docLength: Float = file.docLength(zoom = _zoom)
        val offset: Float = if (_isSwipeVertical) {
            -_currentYOffset / (docLength - height)
        } else {
            -_currentXOffset / (docLength - width)
        }
        return offset.coerceIn(minimumValue = 0f, maximumValue = 1f)
    }

    @JvmOverloads
    fun jumpTo(page: Int, withAnimation: Boolean = false) {
        val file: PdfFile = pdfFile ?: return
        val pageNumber: Int = file.ensureValidPageNumber(userPage = page)

        var offset: Float = if (pageNumber == 0) 0f else -file.getPageOffset(pageIndex = pageNumber, zoom = _zoom)
        offset += file.getPageSpacing(pageIndex = pageNumber, zoom = _zoom) / 2f

        when {
            _isSwipeVertical && withAnimation -> _animationManager?.startYAnimation(
                yFrom = _currentYOffset,
                yTo = offset
            )

            _isSwipeVertical -> moveTo(offsetX = _currentXOffset, offsetY = offset)
            withAnimation -> _animationManager?.startXAnimation(xFrom = _currentXOffset, xTo = offset)
            else -> moveTo(offsetX = offset, offsetY = _currentYOffset)
        }
        showPage(page = pageNumber)
    }

    private fun load(docSource: DocumentSource, password: String?, userPages: IntArray? = null) {
        check(value = _isRecycled) { "Don't call load on a PDF View without recycling it first." }
        _isRecycled = false
        pdfiumCore?.let {
            _decodingTask = DecodingTask(
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
        _state = State.LOADED
        this.pdfFile = pdfFile

        renderingHandler = RenderingHandler(pdfView = this@PDFView).apply { start() }

        _scrollHandle?.takeIf { !_isScrollHandleInit }?.also { handle ->
            handle.setupLayout(pdfView = this@PDFView)
            _isScrollHandleInit = true
        }

        _dragPinchManager?.enable()
        callbacks.callOnLoadComplete(totalPages = pdfFile.pagesCount)
        jumpTo(page = _defaultPage, withAnimation = false)
    }

    fun loadError(error: Throwable?) {
        _state = State.ERROR
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

        val (offset, center) = if (_isSwipeVertical) _currentYOffset to height / 2f else _currentXOffset to width / 2f
        val page: Int = file.getPageAtOffset(offset = -(offset - center), zoom = _zoom)
        when {
            page in 0 until file.pagesCount && page != _currentPage -> showPage(page = page)
            else -> loadPages()
        }
    }

    fun loadPages() {
        if (pdfFile == null || renderingHandler == null) return
        renderingHandler?.removeTasks()
        cacheManager?.makeNewSet()
        _pagesLoader?.loadPages()
        invalidate()
    }

    fun moveRelativeTo(dx: Float, dy: Float) {
        moveTo(offsetX = _currentXOffset + dx, offsetY = _currentYOffset + dy)
    }

    @JvmOverloads
    fun moveTo(offsetX: Float, offsetY: Float, moveHandle: Boolean = true) {
        val file: PdfFile = pdfFile ?: return

        var newOffsetX: Float = offsetX
        var newOffsetY: Float = offsetY
        val positionOffset: Float = getPositionOffset()

        if (_isSwipeVertical) {
            val scaledPageWidth: Float = toCurrentScale(size = file.maxPageWidth)
            newOffsetX = when {
                scaledPageWidth < width -> width / 2f - scaledPageWidth / 2
                newOffsetX > 0 -> 0f
                newOffsetX + scaledPageWidth < width -> width - scaledPageWidth
                else -> newOffsetX
            }

            val contentHeight: Float = file.docLength(zoom = _zoom)
            newOffsetY = when {
                contentHeight < height -> (height - contentHeight) / 2
                newOffsetY > 0 -> 0f
                newOffsetY + contentHeight < height -> -contentHeight + height
                else -> newOffsetY
            }

            _scrollDir = when {
                newOffsetY < _currentYOffset -> ScrollDir.END
                newOffsetY > _currentYOffset -> ScrollDir.START
                else -> ScrollDir.NONE
            }
        } else {
            val scaledPageHeight: Float = toCurrentScale(size = file.maxPageHeight)
            newOffsetY = when {
                scaledPageHeight < height -> height / 2f - scaledPageHeight / 2
                newOffsetY > 0 -> 0f
                newOffsetY + scaledPageHeight < height -> height - scaledPageHeight
                else -> newOffsetY
            }

            val contentWidth: Float = file.docLength(zoom = _zoom)
            newOffsetX = when {
                contentWidth < width -> (width - contentWidth) / 2
                newOffsetX > 0 -> 0f
                newOffsetX + contentWidth < width -> -contentWidth + width
                else -> newOffsetX
            }

            _scrollDir = when {
                newOffsetX < _currentXOffset -> ScrollDir.END
                newOffsetX > _currentXOffset -> ScrollDir.START
                else -> ScrollDir.NONE
            }
        }

        _currentXOffset = newOffsetX
        _currentYOffset = newOffsetY

        if (moveHandle && !documentFitsView()) _scrollHandle?.setScroll(position = positionOffset)

        callbacks.callOnPageScroll(currentPage = _currentPage, offset = positionOffset)
        invalidate()

        val focusedPage: Int = findFocusPage(xOffset = _currentXOffset, yOffset = _currentYOffset)
        if (focusedPage in 0 until file.pagesCount && focusedPage != _currentPage) showPage(page = focusedPage)
    }

    fun onBitmapRendered(part: PagePart) {
        val file: PdfFile = pdfFile ?: return

        if (_state == State.LOADED) {
            _state = State.SHOWN
            callbacks.callOnRender(totalPages = file.pagesCount)
        }

        if (part.isThumbnail) cacheManager?.cacheThumbnail(part = part) else cacheManager?.cachePart(part = part)
        invalidate()
    }

    fun pageFillsScreen(): Boolean {
        val file: PdfFile = pdfFile ?: return false
        val start: Float = -file.getPageOffset(pageIndex = _currentPage, zoom = _zoom)
        val end: Float = start - file.getPageLength(pageIndex = _currentPage, zoom = _zoom)

        return if (_isSwipeVertical) {
            start > _currentYOffset && end < _currentYOffset - height
        } else {
            start > _currentXOffset && end < _currentXOffset - width
        }
    }

    fun performPageSnap() {
        if (!_isPageSnap) return
        val file: PdfFile = pdfFile ?: return
        if (file.pagesCount == 0) return

        val centerPage: Int = findFocusPage(xOffset = _currentXOffset, yOffset = _currentYOffset)
        val edge: SnapEdge = findSnapEdge(page = centerPage)
        if (edge == SnapEdge.NONE) return
        val offset: Float = snapOffsetForPage(pageIndex = centerPage, edge = edge)
        if (_isSwipeVertical) {
            _animationManager?.startYAnimation(yFrom = _currentYOffset, yTo = -offset)
        } else {
            _animationManager?.startXAnimation(xFrom = _currentXOffset, xTo = -offset)
        }
    }

    fun recycle() {
        _waitingDocumentConfigurator = null

        _animationManager?.stopAll()
        _dragPinchManager?.disable()

        renderingHandler?.stop()
        renderingHandler?.removeTasks()

        _decodingTask?.cancel()
        cacheManager?.clearAll()

        _scrollHandle?.takeIf { _isScrollHandleInit }?.destroyLayout()

        pdfFile?.let { file ->
            file.dispose()
            pdfFile = null
        }

        renderingHandler = null
        _scrollHandle = null
        _isScrollHandleInit = false
        _currentYOffset = 0f
        _currentXOffset = _currentYOffset
        _zoom = 1f
        _isRecycled = true
        callbacks = Callbacks()
        _state = State.DEFAULT
    }

    fun setPositionOffset(progress: Float, moveHandle: Boolean) {
        val file: PdfFile = pdfFile ?: return
        val docLength: Float = file.docLength(zoom = _zoom)

        if (_isSwipeVertical) {
            moveTo(offsetX = _currentXOffset, offsetY = (-docLength + height) * progress, moveHandle = moveHandle)
        } else {
            moveTo(offsetX = (-docLength + width) * progress, offsetY = _currentYOffset, moveHandle = moveHandle)
        }
        loadPageByOffset()
    }

    private fun showPage(page: Int) {
        if (_isRecycled) return
        val file: PdfFile = pdfFile ?: return

        val pageNumber: Int = file.ensureValidPageNumber(userPage = page)
        _currentPage = pageNumber
        loadPages()

        if (!documentFitsView()) {
            _scrollHandle?.setPageNumber(pageNumber = _currentPage + 1)
        }

        callbacks.callOnPageChange(page = _currentPage, totalPages = file.pagesCount)
    }

    fun snapOffsetForPage(pageIndex: Int, edge: SnapEdge): Float {
        val file: PdfFile = pdfFile ?: return 0f
        val length: Float = if (_isSwipeVertical) height.toFloat() else width.toFloat()

        val offset: Float = file.getPageOffset(pageIndex = pageIndex, zoom = _zoom)
        val pageLength: Float = file.getPageLength(pageIndex = pageIndex, zoom = _zoom)

        return when (edge) {
            SnapEdge.CENTER -> offset - length / 2f + pageLength / 2f
            SnapEdge.END -> offset - length + pageLength
            else -> offset
        }
    }

    fun zoomCenteredRelativeTo(zoom: Float, pivot: PointF) {
        zoomCenteredTo(zoom = _zoom * zoom, pivot = pivot)
    }

    fun zoomCenteredTo(zoom: Float, pivot: PointF) {
        val zoomRatio: Float = zoom / _zoom
        zoomTo(zoom = zoom)

        var baseX: Float = _currentXOffset * zoomRatio
        var baseY: Float = _currentYOffset * zoomRatio

        baseX += pivot.x - pivot.x * zoomRatio
        baseY += pivot.y - pivot.y * zoomRatio

        moveTo(offsetX = baseX, offsetY = baseY)
    }

    fun zoomTo(zoom: Float) {
        _zoom = zoom
    }

    fun zoomWithAnimation(centerX: Float, centerY: Float, scale: Float) {
        _animationManager?.startZoomAnimation(centerX = centerX, centerY = centerY, zoomFrom = _zoom, zoomTo = scale)
    }

    fun zoomWithAnimation(scale: Float) {
        _animationManager?.startZoomAnimation(
            centerX = width / 2f,
            centerY = height / 2f,
            zoomFrom = _zoom,
            zoomTo = scale
        )
    }

    //Setter
    fun setAnnotation(enabled: Boolean) {
        _isAnnotation = enabled
    }

    fun setAntialiasing(enabled: Boolean) {
        _isEnableAntialiasing = enabled
    }

    fun setAutoSpacing(enabled: Boolean) {
        _isAutoSpacing = enabled
    }

    fun setBestQuality(enabled: Boolean) {
        _isBestQuality = enabled
    }

    fun setDefaultPage(page: Int) {
        _defaultPage = page
    }

    fun setDoubleTap(enabled: Boolean) {
        _isDoubleTapEnabled = enabled
    }

    fun setFitEachPage(enabled: Boolean) {
        _isFitEachPage = enabled
    }

    fun setMaxZoom(zoom: Float) {
        _zoomMax = zoom
    }

    fun setMidZoom(zoom: Float) {
        _zoomMid = zoom
    }

    fun setMinZoom(zoom: Float) {
        _zoomMin = zoom
    }

    fun setNightMode(enabled: Boolean) {
        _isNightMode = enabled
    }

    fun setPageFitPolicy(policy: FitPolicy) {
        _pageFitPolicy = policy
    }

    fun setPageFling(enabled: Boolean) {
        _isPageFling = enabled
    }

    fun setPageSnap(enabled: Boolean) {
        _isPageSnap = enabled
    }

    fun setRenderDuringScale(enabled: Boolean) {
        _isRenderDuringScale = enabled
    }

    fun setScrollHandle(handle: ScrollHandle?) {
        _scrollHandle = handle
    }

    fun setSpacing(spacingDp: Int) {
        _spacingPx = PdfUtils.getDP(context = context, dp = spacingDp)
    }

    fun setSwipeEnabled(enabled: Boolean) {
        _isEnableSwipe = enabled
    }

    fun setSwipeVertical(enabled: Boolean) {
        _isSwipeVertical = enabled
    }

    //Getter
    val bookmarks: List<PdfDocument.Bookmark> get() = pdfFile?.bookmarks ?: emptyList()
    val documentMeta: PdfDocument.Meta? get() = pdfFile?.metaData
    val currentPage: Int get() = _currentPage
    val currentXOffset: Float get() = _currentXOffset
    val currentYOffset: Float get() = _currentYOffset
    val maxZoom: Float get() = _zoomMax
    val midZoom: Float get() = _zoomMid
    val minZoom: Float get() = _zoomMin
    val pagesCount: Int get() = pdfFile?.pagesCount ?: pdfFile!!.totalPages
    val pageFitPolicy: FitPolicy get() = _pageFitPolicy
    val scrollHandle: ScrollHandle? get() = _scrollHandle
    val spacingPx: Int get() = _spacingPx
    val zoom: Float get() = _zoom
    val isAnnotationRendering: Boolean get() = _isAnnotation
    val isAntialiasing: Boolean get() = _isEnableAntialiasing
    val isAutoSpacingEnabled: Boolean get() = _isAutoSpacing
    val isBestQuality: Boolean get() = _isBestQuality
    val isDoubleTapEnabled: Boolean get() = _isDoubleTapEnabled
    val isFitEachPage: Boolean get() = _isFitEachPage
    val isNightMode: Boolean get() = _isNightMode
    val isPageFlingEnabled: Boolean get() = _isPageFling
    val isPageSnap: Boolean get() = _isPageSnap
    val isRecycled: Boolean get() = _isRecycled
    val isRenderDuringScale: Boolean get() = _isRenderDuringScale
    val isSwipeEnabled: Boolean get() = _isEnableSwipe
    val isSwipeVertical: Boolean get() = _isSwipeVertical
    val isZooming: Boolean get() = _zoom != _zoomMin

    //Others
    fun resetZoom(): Unit = zoomTo(zoom = _zoomMin)
    fun resetZoomWithAnimation(): Unit = zoomWithAnimation(scale = _zoomMin)
    fun stopFling(): Unit? = _animationManager?.stopFling()
    fun toCurrentScale(size: Float): Float = size * _zoom
    fun toRealScale(size: Float): Float = size / _zoom

    override fun canScrollHorizontally(direction: Int): Boolean {
        val file: PdfFile = pdfFile ?: return true

        return if (_isSwipeVertical) {
            when {
                direction < 0 && _currentXOffset < 0 -> true
                direction > 0 -> _currentXOffset + toCurrentScale(size = file.maxPageWidth) > width
                else -> false
            }
        } else {
            when {
                direction < 0 && _currentXOffset < 0 -> true
                direction > 0 -> _currentXOffset + file.docLength(zoom = _zoom) > width
                else -> false
            }
        }
    }


    override fun canScrollVertically(direction: Int): Boolean {
        val file: PdfFile = pdfFile ?: return true

        return if (_isSwipeVertical) {
            when {
                direction < 0 && _currentYOffset < 0 -> true
                direction > 0 -> _currentYOffset + file.docLength(zoom = _zoom) > height
                else -> false
            }
        } else {
            when {
                direction < 0 && _currentYOffset < 0 -> true
                direction > 0 -> _currentYOffset + toCurrentScale(size = file.maxPageHeight) > height
                else -> false
            }
        }
    }

    override fun computeScroll() {
        super.computeScroll()
        if (isInEditMode) return
        _animationManager?.computeFling()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
    }

    override fun onDetachedFromWindow() {
        recycle()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        if (isInEditMode) return

        if (_isEnableAntialiasing) {
            canvas.drawFilter = _antialiasFilter
        }

        background?.draw(canvas) ?: canvas.drawColor(if (_isNightMode) Color.BLACK else Color.WHITE)

        if (_isRecycled || _state != State.SHOWN) return

        canvas.translate(_currentXOffset, _currentYOffset)

        cacheManager?.let { manager ->
            manager.allThumbnails.forEach { part ->
                drawPart(canvas = canvas, part = part)
            }
            manager.pageParts.forEach { part ->
                drawPart(canvas = canvas, part = part)
                callbacks.onDrawAll?.takeIf { !_onDrawPagesNumber.contains(part.page) }?.let {
                    _onDrawPagesNumber.add(part.page)
                }
            }
        }

        canvas.translate(-_currentXOffset, -_currentYOffset)

        _onDrawPagesNumber.forEach { page ->
            drawWithListener(canvas = canvas, page = page, listener = callbacks.onDrawAll)
        }
        _onDrawPagesNumber.clear()
        drawWithListener(canvas = canvas, page = _currentPage, listener = callbacks.onDraw)
    }

    override fun onSizeChanged(w: Int, h: Int, oldW: Int, oldH: Int) {
        _isHasSize = true
        _waitingDocumentConfigurator?.load()

        if (isInEditMode || _state != State.SHOWN) return
        val file: PdfFile = pdfFile ?: return

        val centerPointOffsetX: Float = -_currentXOffset + oldW * 0.5f
        val centerPointOffsetY: Float = -_currentYOffset + oldH * 0.5f

        val (relativeX, relativeY) = if (_isSwipeVertical) {
            centerPointOffsetX / file.maxPageWidth to centerPointOffsetY / file.docLength(zoom = _zoom)
        } else {
            centerPointOffsetX / file.docLength(zoom = _zoom) to centerPointOffsetY / file.maxPageHeight
        }

        _animationManager?.stopAll()
        file.recalculatePageSizes(viewSize = Size(width = w, height = h))

        val (newOffsetX, newOffsetY) = if (_isSwipeVertical) {
            -relativeX * file.maxPageWidth + w * 0.5f to -relativeY * file.docLength(zoom = _zoom) + h * 0.5f
        } else {
            -relativeX * file.docLength(zoom = _zoom) + w * 0.5f to -relativeY * file.maxPageHeight + h * 0.5f
        }

        _currentXOffset = newOffsetX
        _currentYOffset = newOffsetY
        moveTo(offsetX = _currentXOffset, offsetY = _currentYOffset)
        loadPageByOffset()
    }

    /**
     * Writes a copy of the currently loaded PDF document to the provided output stream,
     * allowing control over incremental updates and security settings.
     *
     * @param out The output stream to which the PDF will be written. Caller is responsible for closing it.
     * @param flags A flag indicating the save behavior. Valid options:
     *   - [FPDF_INCREMENTAL]: Preserves previous revisions, appending changes incrementally.
     *   - [FPDF_NO_INCREMENTAL]: Rewrites the entire PDF as a new file, optimizing size.
     *   - [FPDF_REMOVE_SECURITY]: Generates an unprotected copy if the source PDF is restricted.
     *
     * @return `true` if the PDF was successfully saved, `false` if no document was loaded or on failure.
     */
    fun saveAsCopy(out: OutputStream, flags: Int): Boolean {
        return pdfFile?.saveAsCopy(out = out, flags = flags) ?: false
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
        fun disableLongPress() = apply { _dragPinchManager?.disableLongPress() }
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
            if (!_isHasSize) {
                _waitingDocumentConfigurator = this@Configurator
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

        const val FPDF_INCREMENTAL: Int = 1
        const val FPDF_NO_INCREMENTAL: Int = 2
        const val FPDF_REMOVE_SECURITY: Int = 3
    }

    private fun initPDFView() {
        if (isInEditMode) return
        pdfiumCore = PdfiumCore(context = context)
        cacheManager = CacheManager()
        _animationManager = AnimationManager(pdfView = this@PDFView).also {
            _dragPinchManager = DragPinchManager(pdfView = this@PDFView, animationManager = it)
        }
        _pagesLoader = PagesLoader(pdfView = this@PDFView)
        _paint = Paint()
        _debugPaint = Paint().apply {
            style = Paint.Style.STROKE
        }
        setWillNotDraw(false)
    }

    init {
        initPDFView()
    }
}