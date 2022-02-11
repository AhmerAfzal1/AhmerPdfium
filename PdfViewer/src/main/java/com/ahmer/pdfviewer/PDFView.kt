package com.ahmer.pdfviewer

import android.content.Context
import android.content.res.Resources
import android.graphics.*
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.HandlerThread
import android.util.AttributeSet
import android.util.DisplayMetrics
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.RelativeLayout
import androidx.annotation.NonNull
import androidx.annotation.Nullable
import androidx.core.content.ContextCompat
import com.ahmer.afzal.pdfviewer.R
import com.ahmer.pdfium.PdfDocument
import com.ahmer.pdfium.PdfiumCore
import com.ahmer.pdfium.util.Size
import com.ahmer.pdfium.util.SizeF
import com.ahmer.pdfviewer.exception.PageRenderingException
import com.ahmer.pdfviewer.link.DefaultLinkHandler
import com.ahmer.pdfviewer.link.LinkHandler
import com.ahmer.pdfviewer.listener.*
import com.ahmer.pdfviewer.model.PagePart
import com.ahmer.pdfviewer.model.SearchRecord
import com.ahmer.pdfviewer.model.SearchRecordItem
import com.ahmer.pdfviewer.scroll.ScrollHandle
import com.ahmer.pdfviewer.source.AssetSource
import com.ahmer.pdfviewer.source.DocumentSource
import com.ahmer.pdfviewer.source.FileSource
import com.ahmer.pdfviewer.source.UriSource
import com.ahmer.pdfviewer.util.*
import com.ahmer.pdfviewer.util.PdfUtils.getDP
import java.io.File
import kotlin.math.abs


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
    private var mCurrentPage: Int = 0

    /**
     * Paint object for drawing debug stuff
     */
    private var mDebugPaint: Paint? = null
    private var mDecodingTask: DecodingTask? = null
    private var mDefaultPage: Int = 0

    /**
     * Drag manager manage all touch events
     */
    var mDragPinchManager: DragPinchManager? = null
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

    ///////////////////////////////////////////////////
    val searchRecords: HashMap<Int?, SearchRecord?>? = HashMap()
    val srcArray = FloatArray(8)
    val dstArray = FloatArray(8)
    val handleLeftPos = RectF()
    val handleRightPos = RectF()
    val mMatrix = Matrix()
    var isSearching = false
    var startInDrag = false
    var dm: DisplayMetrics? = null
    var selectionPaintView: PDFViewSelection? = null

    /**
     * Pdfium core for loading and rendering PDFs
     */
    var pdfiumCore: PdfiumCore? = null
    var mResource: Resources? = null
    var sCursorPos = PointF()
    var lineHeightLeft = 0f
    var lineHeightRight = 0f
    var hasSelection = false
    var selPageSt = -1
    var selPageEd = 0
    var selStart = 0
    var selEnd = 0
    var hideView: View? = null
    var handleLeft: Drawable? = null
    var handleRight: Drawable? = null
    var draggingHandle: Drawable? = null
    var drawableScale = 1f
    var onSelection: OnSelection? = null
    private var task: SearchTask? = null
    private var spacingTopPx = 0
    private var spacingBottomPx = 0
    private var initialRender = true

    fun setIsSearching(isSearching: Boolean) {
        this.isSearching = isSearching
        redrawSel()
    }

    fun setOnSelections(onSelection: OnSelection?) {
        this.onSelection = onSelection
    }

    fun setMatrixArray(
        array: FloatArray, f0: Float, f1: Float, f2: Float, f3: Float,
        f4: Float, f5: Float, f6: Float, f7: Float
    ) {
        array[0] = f0
        array[1] = f1
        array[2] = f2
        array[3] = f3
        array[4] = f4
        array[5] = f5
        array[6] = f6
        array[7] = f7
    }

    fun notifyItemAdded(
        searchTask: SearchTask?, arr: ArrayList<SearchRecord>, schRecord: SearchRecord?, i: Int
    ) {
        searchRecords!![i] = schRecord
    }

    fun setSelectionPaintViews(sv: PDFViewSelection) {
        selectionPaintView = sv
        sv.pdfView = this
        sv.resetSel()
        sv.drawableWidth = getDP(context, sv.drawableWidth.toInt()) * drawableScale
        sv.drawableHeight = getDP(context, sv.drawableHeight.toInt()) * drawableScale
        sv.drawableDeltaW = sv.drawableWidth / 4
    }

    fun isNotCurrentPage(tid: Long): Boolean {
        return mDragPinchManager?.currentTextPtr != 0L && tid != mDragPinchManager?.currentTextPtr
    }

    fun redrawSel() {
        if (selectionPaintView != null) {
            selectionPaintView!!.invalidate()
        }
    }

    fun clearSelection() {
        if (onSelection != null) {
            onSelection!!.onSelection(false)
        }
        mDragPinchManager?.currentTextPtr = 0
        hasSelection = false
        redrawSel()
    }

    fun getScreenWidth(): Int {
        var ret = 0
        if (ret == 0 && dm != null) ret = dm!!.widthPixels
        return ret
    }

    fun getScreenHeight(): Int {
        var ret = 0
        if (ret == 0 && dm != null) ret = dm!!.heightPixels
        return ret
    }

    fun setSearchResults(arr: ArrayList<SearchRecord?>?, key: String?, flag: Int) {
        //  adaptermy.setSearchResults(arr, key, flag);
        //  currentViewer.selectionPaintView.searchCtx = adaptermy.getSearchProvider();
    }

    fun closeTask() {
        task?.abort()
        task = null
    }

    fun search(text: String?) {
        searchRecords!!.clear()
        setIsSearching(true)
        if (task != null) {
            closeTask()
        }
        task = SearchTask(this, text!!)
        task!!.start()
    }

    fun startSearch(arr: ArrayList<SearchRecord>, key: String?, flag: Int) {}

    fun endSearch(arr: ArrayList<SearchRecord>) {
        selectionPaintView!!.invalidate()
        // searchHandler.endSearch(arr);
    }

    fun setSelectionAtPage(pageIdx: Int, st: Int, ed: Int) {
        selPageSt = pageIdx
        selPageEd = pageIdx
        selStart = st
        selEnd = ed
        hasSelection = true
        selectionPaintView!!.resetSel()
    }


    @Nullable
    fun sourceToViewCoord(sxy: PointF, @NonNull vTarget: PointF): PointF {
        return sourceToViewCoord(sxy.x, sxy.y, vTarget)
    }


    fun sourceToViewCoord(sx: Float, sy: Float, @NonNull vTarget: PointF): PointF {
        val xPreRotate = sourceToViewX(sx)
        val yPreRotate = sourceToViewY(sy)
        vTarget[xPreRotate] = yPreRotate
        return vTarget
    }

    private fun sourceToViewX(sx: Float): Float {
        return sx * getZoom() //+getCurrentXOffset(); // + vTranslate.x;
    }

    private fun sourceToViewY(sy: Float): Float {
        return sy * getZoom() //+getCurrentYOffset()) ;//+ vTranslate.y;
    }

    fun getCharPos(pos: RectF?, index: Int) {
        val mappedX: Float = -getCurrentXOffset() + mDragPinchManager?.lastX!!
        val mappedY: Float = -getCurrentYOffset() + mDragPinchManager?.lastY!!
        val page = pdfFile!!.getPageAtOffset(if (isSwipeVertical()) mappedY else mappedX, getZoom())
        val pagePtr: Long? = pdfFile!!.pdfDocument.mNativePagesPtr[page]
        val size = pdfFile!!.getPageSize(page)
        //  SizeF size = pdfFile.SizeF size = pdfView.pdfFile.getPageSize(page);(page, getZoom());
        mDragPinchManager?.loadText()?.let {
            pdfiumCore!!.nativeGetCharPos(
                pagePtr!!, 0, 0, size.width.toInt(), size.height.toInt(),
                pos, it, index, true
            )
        }
    }

    fun getCharLoosePos(pos: RectF?, index: Int) {
        val mappedX: Float = -getCurrentXOffset() + mDragPinchManager?.lastX!!
        val mappedY: Float = -getCurrentYOffset() + mDragPinchManager?.lastY!!
        val page = pdfFile!!.getPageAtOffset(if (isSwipeVertical()) mappedY else mappedX, getZoom())
        val pagePtr: Long? = pdfFile!!.pdfDocument.mNativePagesPtr.get(page)
        val size = pdfFile!!.getPageSize(page)
        //   SizeF size = pdfFile.getScaledPageSize(page, getZoom());
        mDragPinchManager?.loadText()?.let {
            pdfiumCore!!.nativeGetMixedLooseCharPos(
                pagePtr!!, 0, getLateralOffset(), size.width.toInt(), size.height.toInt(),
                pos, it, index, true
            )
        }
    }

    fun getCharLoose(pos: RectF?, index: Int) {
        val page: Int = mCurrentPage
        val pagePtr: Long? = pdfFile!!.pdfDocument.mNativePagesPtr.get(page)
        val size = pdfFile!!.getPageSize(page)
        //   SizeF size = pdfFile.getScaledPageSize(page, getZoom());
        mDragPinchManager?.loadText()?.let {
            pdfiumCore!!.nativeGetMixedLooseCharPos(
                pagePtr!!, 0, getLateralOffset(), size.width.toInt(), size.height.toInt(),
                pos, it, index, true
            )
        }
    }

    fun getAllMatchOnPage(record: SearchRecord) {
        val page = if (record.currentPage != -1) record.currentPage else mCurrentPage
        val tid: Long? = mDragPinchManager?.prepareText(page)
        if (record.data == null && tid != -1L) {
            //CMN.rt();
            val data: ArrayList<SearchRecordItem> = ArrayList()
            record.data = data
            val keyStr = task!!.keyStr
            if (keyStr != 0L) {
                val searchHandle =
                    pdfiumCore!!.nativeFindTextPageStart(tid!!, keyStr, task!!.flag, record.findStart)
                if (searchHandle != 0L) {
                    while (pdfiumCore!!.nativeFindTextPageNext(searchHandle)) {
                        val st = pdfiumCore!!.nativeGetFindIdx(searchHandle)
                        val ed = pdfiumCore!!.nativeGetFindLength(searchHandle)
                        getRectsForRecordItem(data, st, ed, page)
                    }
                    pdfiumCore!!.nativeFindTextPageEnd(searchHandle)
                }
            }
        }
    }

    fun mergeLineRects(selRects: List<RectF>, box: RectF?): ArrayList<RectF> {
        val tmp = RectF()
        val selLineRects: ArrayList<RectF> = ArrayList(selRects.size)
        var currentLineRect: RectF? = null
        for (rI in selRects) {
            //CMN.Log("RectF rI:selRects", rI);
            if (currentLineRect != null && abs(currentLineRect.top + currentLineRect.bottom - (rI.top + rI.bottom)) < currentLineRect.bottom - currentLineRect.top) {
                currentLineRect.left = currentLineRect.left.coerceAtMost(rI.left)
                currentLineRect.right = currentLineRect.right.coerceAtLeast(rI.right)
                currentLineRect.top = currentLineRect.top.coerceAtMost(rI.top)
                currentLineRect.bottom = currentLineRect.bottom.coerceAtLeast(rI.bottom)
            } else {
                currentLineRect = RectF()
                currentLineRect.set(rI)
                selLineRects.add(currentLineRect)
                val cid: Int? =
                    mDragPinchManager?.getCharIdxAt(rI.left + 1, rI.top + rI.height() / 2, 10)
                if (cid != null) {
                    if (cid > 0) {
                        getCharLoose(tmp, cid)
                        currentLineRect.left = currentLineRect.left.coerceAtMost(tmp.left)
                        currentLineRect.right = currentLineRect.right.coerceAtLeast(tmp.right)
                        currentLineRect.top = currentLineRect.top.coerceAtMost(tmp.top)
                        currentLineRect.bottom = currentLineRect.bottom.coerceAtLeast(tmp.bottom)
                    }
                }
            }
            if (box != null) {
                box.left = box.left.coerceAtMost(currentLineRect.left)
                box.right = box.right.coerceAtLeast(currentLineRect.right)
                box.top = box.top.coerceAtMost(currentLineRect.top)
                box.bottom = box.bottom.coerceAtLeast(currentLineRect.bottom)
            }
        }
        return selLineRects
    }

    private fun getRectsForRecordItem(
        data: ArrayList<SearchRecordItem>, st: Int, ed: Int, page: Int
    ) {

        // int page = currentPage;//pdfFile.getPageAtOffset(isSwipeVertical() ? mappedY : mappedX, getZoom());
        val tid: Long? = pdfFile!!.pdfDocument.mNativeTextPtr[page]
        val pid: Long? = pdfFile!!.pdfDocument.mNativePagesPtr[page]
        val size = pdfFile!!.getPageSize(page)
        if (st >= 0 && ed > 0) {
            val rectCount = pdfiumCore!!.nativeCountRects(tid!!, st, ed)
            if (rectCount > 0) {
                var rects = arrayOfNulls<RectF>(rectCount)
                for (i in 0 until rectCount) {
                    val rI = RectF()
                    pdfiumCore!!.nativeGetRect(
                        pid!!, 0, 0, size.width.toInt(), size.height.toInt(), tid, rI, i
                    )
                    rects[i] = rI
                }
                rects = listOf(*rects).toTypedArray()
                data.add(SearchRecordItem(st, ed, rects))
            }
        }
    }

    fun getLateralOffset(): Int {
        return 0
    }

    fun initSelection() {
        handleLeft = ContextCompat.getDrawable(context, R.drawable.abc_text_select_handle_left_mtrl_dark)
        handleRight = ContextCompat.getDrawable(context, R.drawable.abc_text_select_handle_right_mtrl_dark)
        val colorFilter: ColorFilter = PorterDuffColorFilter(-0x55cf6502, PorterDuff.Mode.SRC_IN)
        handleLeft?.colorFilter = colorFilter
        handleRight?.colorFilter = colorFilter
        handleLeft?.alpha = 255
        handleRight?.alpha = 255
        val moveSlop = 1.6f
    }

    fun sourceToViewRectFFSearch(sRect: RectF, vTarget: RectF, currentPage: Int) {
        val pageX = pdfFile!!.getSecondaryPageOffset(currentPage, getZoom()).toInt()
        val pageY = pdfFile!!.getPageOffset(currentPage, getZoom()).toInt()
        vTarget[sRect.left * getZoom() + pageX + mCurrentOffsetX, sRect.top * getZoom() + pageY + mCurrentOffsetY, sRect.right * getZoom() + pageX + mCurrentOffsetX] =
            sRect.bottom * getZoom() + pageY + mCurrentOffsetY
    }

    fun sourceToViewRectFF(sRect: RectF, vTarget: RectF) {
        val mappedX: Float = -getCurrentXOffset() + mDragPinchManager?.lastX!!
        val mappedY: Float = -getCurrentYOffset() + mDragPinchManager?.lastY!!
        // Log.e("mDragPinchManager?",mDragPinchManager?.lastX+""+mDragPinchManager?.lastY);
        // Log.e("getCurrentYOffset",(-getCurrentYOffset())+""+(-getCurrentXOffset()));
        var page = -1
        if (pdfFile?.pdfDocument != null && pdfFile!!.pdfDocument.mNativeTextPtr.containsValue(
                mDragPinchManager?.currentTextPtr
            )
        ) {
            for ((key, value) in pdfFile!!.pdfDocument.mNativeTextPtr.entries) {
                if (value == mDragPinchManager?.currentTextPtr) {
                    page = key
                }
            }
        }
        val curPage =
            pdfFile!!.getPageAtOffset(if (isSwipeVertical()) mappedY else mappedX, getZoom())
        if (page == -1) page = curPage
        Log.e("page", page.toString() + "")
        val pageX = pdfFile!!.getSecondaryPageOffset(page, getZoom()).toInt()
        val pageY = pdfFile!!.getPageOffset(page, getZoom()).toInt()
        vTarget[sRect.left * getZoom() + pageX + mCurrentOffsetX, sRect.top * getZoom() + pageY + mCurrentOffsetY, sRect.right * getZoom() + pageX + mCurrentOffsetX] =
            sRect.bottom * getZoom() + pageY + mCurrentOffsetY
    }

    fun findPageCached(key: String?, pageIdx: Int, flag: Int): SearchRecord? {
        val tid: Long? = mDragPinchManager?.loadText(pageIdx)
        if (tid == -1L) {
            return null
        }
        val foundIdx = pdfiumCore!!.nativeFindTextPage(tid!!, key, flag)
        return if (foundIdx == -1) null else SearchRecord(pageIdx, foundIdx)
    }

    @Throws(Exception::class)
    fun getSelection(): String? {
        if (selectionPaintView != null) {
            try {
                if (hasSelection) {
                    val pageStart = selPageSt
                    val pageCount = selPageEd - pageStart
                    if (pageCount == 0) {
                        mDragPinchManager?.prepareText()
                        return mDragPinchManager?.allText!!.substring(selStart, selEnd)
                    }
                    val sb = StringBuilder()
                    var selCount = 0
                    for (i in 0..pageCount) {
                        mDragPinchManager?.prepareText()
                        val len: Int = mDragPinchManager?.allText!!.length
                        selCount += if (i == 0) len - selStart else if (i == pageCount) selEnd else len
                    }
                    sb.ensureCapacity(selCount + 64)
                    for (i in 0..pageCount) {
                        sb.append(
                            mDragPinchManager?.allText!!.substring(
                                if (i == 0) selStart else 0,
                                if (i == pageCount) selEnd else mDragPinchManager?.allText!!.length
                            )
                        )
                    }
                    return sb.toString()
                }
            } catch (e: Exception) {
                Log.e("get Selection Exception", "Exception", e)
                throw e
            }
        }
        return null
    }

    fun hasSelection(): Boolean {
        return hasSelection
    }

    fun getSpacingTopPx(): Int {
        return spacingTopPx
    }

    fun getSpacingBottomPx(): Int {
        return spacingBottomPx
    }

    private fun setOnScrollHideView(hideView: View) {
        this.hideView = hideView
    }

    private fun setSpacingTop(spacingTopDp: Int) {
        spacingTopPx = getDP(context, spacingTopDp)
    }

    private fun setSpacingBottom(spacingBottomDp: Int) {
        spacingBottomPx = getDP(context, spacingBottomDp)
    }





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
            Log.e(TAG, "Cannot fit, document not rendered yet")
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
    fun getDocumentMeta(): PdfDocument.Meta? {
        return pdfFile?.getMetaData()
    }

    /**
     * Will be empty until document is loaded
     */
    fun getLinks(page: Int): List<PdfDocument.Link> {
        return pdfFile?.getPageLinks(page) ?: emptyList()
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
    fun getTableOfContents(): List<PdfDocument.Bookmark> {
        return pdfFile?.getBookmarks() ?: emptyList()
    }

    fun getTotalPagesCount(): Int {
        return pdfFile?.pagesCount ?: 0
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
        return isEnableSwipe && !startInDrag
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
        if (mPage == 0 && initialRender) {
            initialRender = false
            mOffset += spacingTopPx
        }
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
        check(isRecycled) { "Don't call load on a PDF View without recycling it first." }
        isRecycled = false
        // Start decoding document
        mDecodingTask = DecodingTask(docSource, pdfiumCore!!, password, userPages, this)
        mDecodingTask?.execute()
    }

    /**
     * Called when the PDF is loaded
     */
    fun loadComplete(pdfFile: PdfFile) {
        mState = State.LOADED
        this.pdfFile = pdfFile
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
        onErrorListener?.onError(t) ?: Log.e(TAG, "Load PDF error: ", t)
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
            Log.e(TAG, "Cannot open page: " + ex.page + " due to: " + ex.cause)
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
        val mCurrentOffsetX = mCurrentOffsetX
        val mCurrentOffsetY = mCurrentOffsetY
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
     * Use custom source as pdf source
     */
    fun fromSource(docSource: DocumentSource): Configurator {
        return Configurator(docSource)
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
        private var spacingTop = 0
        private var spacingBottom = 0
        private var hideView: View? = null

        fun onScrollingHideView(view: View?): Configurator {
            this.hideView = view
            return this
        }

        fun spacingTop(spacingTop: Int): Configurator {
            this.spacingTop = spacingTop
            return this
        }

        fun spacingBottom(spacingBottom: Int): Configurator {
            this.spacingBottom = spacingBottom
            return this
        }

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
            this@PDFView.setSpacingTop(spacingTop)
            this@PDFView.setSpacingBottom(spacingBottom)
            hideView?.let { this@PDFView.setOnScrollHideView(it) }
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
        mRenderingHandlerThread = HandlerThread("PDF renderer")
        pdfiumCore = PdfiumCore(context)
        if (isInEditMode) return
        cacheManager = CacheManager()
        mAnimationManager = AnimationManager(this)
        mDragPinchManager = DragPinchManager(this, mAnimationManager!!)
        mPagesLoader = PagesLoader(this)
        mPaint = Paint()
        mDebugPaint = Paint()
        mDebugPaint?.style = Paint.Style.STROKE

        val display =
            (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay
        mResource = resources
        dm = resources.displayMetrics
        display.getMetrics(dm)

        initSelection()
        setWillNotDraw(false)
    }

    init {
        initPDFView()
    }
}