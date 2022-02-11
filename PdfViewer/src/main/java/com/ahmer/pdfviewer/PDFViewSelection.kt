package com.ahmer.pdfviewer

import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.ahmer.afzal.pdfviewer.R
import com.ahmer.pdfviewer.model.SearchRecord
import com.ahmer.pdfviewer.model.SearchRecordItem
import com.ahmer.pdfviewer.util.PdfUtils
import kotlin.math.sign

/**
 * A View to paint PDF selections, [magnifier] and search highlights
 */
class PDFViewSelection : View {
    private val vCursorPos = PointF()
    private val tmpPosRct = RectF()
    var supressRecalcInval = false
    var pdfView: PDFView? = null
    var drawableWidth = 60f
    var drawableHeight = 30f
    var drawableDeltaW = drawableWidth / 4
    var rectPaint: Paint? = null
    var rectFramePaint: Paint? = null
    var rectHighlightPaint: Paint? = null

    /**
     * Small Canvas for magnifier.
     * [ClipPath][Canvas.clipPath] fails if the canvas it too high. ( will never happen in this project. )
     * see [issuetracker](https://issuetracker.google.com/issues/132402784))
     */
    var cc: Canvas? = null
    var PageCache: Bitmap? = null
    var PageCacheDrawable: BitmapDrawable? = null
    var magClipper: Path? = null
    var magClipperR: RectF? = null
    var magFactor = 1.5f
    var magW = 560
    var magH = 280

    /**
     * output image
     */
    var frameDrawable: Drawable? = null
    var rectPoolSize = 0
    var rectPool = ArrayList<ArrayList<RectF>>()
    var magSelBucket = ArrayList<RectF>()
    private var framew = 0f

    constructor(context: Context?) : super(context) {
        init()
    }

    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {
        init()
    }

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        init()
    }

    private fun init() {
        rectPaint = Paint()
        rectPaint!!.color = 0x66109afe
        rectHighlightPaint = Paint()
        rectHighlightPaint!!.color = resources.getColor(R.color.colorHighlight)
        rectPaint!!.xfermode = PorterDuffXfermode(PorterDuff.Mode.DARKEN)
        rectHighlightPaint!!.xfermode = PorterDuffXfermode(PorterDuff.Mode.DARKEN)
        rectFramePaint = Paint()
        rectFramePaint!!.color = -0x333854df
        rectFramePaint!!.style = Paint.Style.STROKE
        rectFramePaint!!.strokeWidth = 0.5f
    }

    private fun initMagnifier() {
        //setLayerType(LAYER_TYPE_NONE,null);
        cc = Canvas(Bitmap.createBitmap(magW, magH, Bitmap.Config.ARGB_8888).also {
            PageCache = it
        })
        PageCacheDrawable = BitmapDrawable(resources, PageCache)
        frameDrawable = ContextCompat.getDrawable(context, R.drawable.frame)
        framew = resources.getDimension(R.dimen.frame)
        magClipper = Path()
        magClipperR = RectF(PageCacheDrawable!!.bounds)
        magClipper!!.reset()
        magClipperR!![0f, 0f, magW.toFloat()] = magH.toFloat()
        magClipper!!.addRoundRect(magClipperR!!, framew + 5, framew + 5, Path.Direction.CW)
    }

    fun resetSel() {
        //Log.v(PdfConstants.TAG, "resetSel"+ pDocView.selPageSt +","+ pDocView.selPageEd+","+ pDocView.selStart+","+ pDocView.selEnd);
        if (pdfView != null && pdfView!!.pdfFile != null && pdfView!!.hasSelection) {
            val tid: Long? = pdfView!!.mDragPinchManager!!.loadText()
            if (pdfView!!.isNotCurrentPage(tid!!)) {
                return
            }
            val b1 = pdfView!!.selPageEd < pdfView!!.selPageSt
            if (b1) {
                pdfView!!.selPageEd = pdfView!!.selPageSt
                pdfView!!.selPageSt = pdfView!!.selPageEd
            } else {
                pdfView!!.selPageEd = pdfView!!.selPageEd
                pdfView!!.selPageSt = pdfView!!.selPageSt
            }
            if (b1 || pdfView!!.selPageEd == pdfView!!.selPageSt && pdfView!!.selEnd < pdfView!!.selStart) {
                pdfView!!.selStart = pdfView!!.selEnd
                pdfView!!.selEnd = pdfView!!.selStart
            } else {
                pdfView!!.selStart = pdfView!!.selStart
                pdfView!!.selEnd = pdfView!!.selEnd
            }
            val pageCount = pdfView!!.selPageEd - pdfView!!.selPageSt
            val sz = rectPool.size
            var rectPagePool: ArrayList<RectF>
            for (i in 0..pageCount) {
                if (i >= sz) {
                    rectPagePool = ArrayList()
                    rectPool.add(rectPagePool)
                } else {
                    rectPagePool = rectPool[i]
                }
                val selSt = if (i == 0) pdfView!!.selStart else 0
                val selEd = if (i == pageCount) pdfView!!.selEnd else -1
                // PDocument.PDocPage page = pDocView.pdfFile.mPDocPages[selPageSt + i];
                pdfView!!.mDragPinchManager!!.getSelRects(rectPagePool, selSt, selEd) //+10
            }
            recalcHandles()
            rectPoolSize = pageCount + 1
        } else {
            rectPoolSize = 0
        }
        if (!supressRecalcInval) {
            invalidate()
        }
    }

    fun recalcHandles() {
        var page = pdfView
        val tid: Long = page!!.mDragPinchManager!!.prepareText()
        if (pdfView!!.isNotCurrentPage(tid)) {
            return
        }
        val mappedX: Float = -pdfView!!.getCurrentXOffset() + pdfView!!.mDragPinchManager!!.lastX
        val mappedY: Float = -pdfView!!.getCurrentYOffset() + pdfView!!.mDragPinchManager!!.lastY
        val pageIndex = pdfView!!.pdfFile!!.getPageAtOffset(
            if (pdfView!!.isSwipeVertical()) mappedY else mappedX,
            pdfView!!.getZoom()
        )
        var st = pdfView!!.selStart
        var ed = pdfView!!.selEnd
        var dir = pdfView!!.selPageEd - pdfView!!.selPageSt
        dir = sign((if (dir == 0) ed - st else dir.toFloat()).toDouble()).toInt()
        if (dir != 0) {
            var atext: String? = page.mDragPinchManager!!.allText
            var len = atext!!.length
            if (st in 0 until len) {
                var c: Char
                while ((atext[st].also {
                        c = it
                    } == '\r' || c == '\n') && st + dir >= 0 && st + dir < len) {
                    st += dir
                }
            }
            page.getCharPos(pdfView!!.handleLeftPos, st)
            pdfView!!.lineHeightLeft = pdfView!!.handleLeftPos.height() / 2
            page.getCharLoosePos(pdfView!!.handleLeftPos, st)
            page = pdfView
            page!!.mDragPinchManager!!.prepareText()
            atext = page.mDragPinchManager!!.allText
            len = atext!!.length
            var delta = -1
            if (ed in 0 until len) {
                var c: Char
                dir *= -1
                while ((atext[ed].also {
                        c = it
                    } == '\r' || c == '\n') && ed + dir >= 0 && ed + dir < len) {
                    delta = 0
                    ed += dir
                }
            }
            //Log.v(PdfConstants.TAG, "getCharPos" + page.allText.substring(ed+delta, ed+delta+1));
            page.getCharPos(pdfView!!.handleRightPos, ed + delta)
            pdfView!!.lineHeightRight = pdfView!!.handleRightPos.height() / 2
            page.getCharLoosePos(pdfView!!.handleRightPos, ed + delta)
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (pdfView == null) {
            return
        }
        val vr = tmpPosRct
        val matrix = pdfView!!.matrix
        if (pdfView!!.isSearching) {
            // SearchRecord record =  pDocView.searchRecords.get(pDocView.getCurrentPage());
            val searchRecordList = getSearchRecords()
            for (record in searchRecordList) {
                if (record != null) {
                    pdfView!!.getAllMatchOnPage(record)
                    val page =
                        if (record.currentPage != -1) record.currentPage else pdfView!!.getCurrentPage()
                    val data = record.data as ArrayList<SearchRecordItem>?
                    var j = 0
                    val len = data!!.size
                    while (j < len) {
                        val rects = data[j].rects
                        for (rI in rects) {
                            pdfView!!.sourceToViewRectFFSearch(rI!!, vr, page)
                            matrix.reset()
                            val bmWidth = rI.width().toInt()
                            val bmHeight = rI.height().toInt()
                            pdfView!!.setMatrixArray(
                                pdfView!!.srcArray, 0f, 0f,
                                bmWidth.toFloat(), 0f, bmWidth.toFloat(),
                                bmHeight.toFloat(), 0f, bmHeight.toFloat()
                            )
                            pdfView!!.setMatrixArray(
                                pdfView!!.dstArray, vr.left, vr.top, vr.right, vr.top,
                                vr.right, vr.bottom, vr.left, vr.bottom
                            )
                            matrix.setPolyToPoly(
                                pdfView!!.srcArray, 0, pdfView!!.dstArray, 0, 4
                            )
                            matrix.postRotate(
                                0f, pdfView!!.getScreenWidth().toFloat(),
                                pdfView!!.getScreenHeight().toFloat()
                            )
                            canvas.save()
                            canvas.concat(matrix)
                            vr[0f, 0f, bmWidth.toFloat()] = bmHeight.toFloat()
                            canvas.drawRect(vr, rectHighlightPaint!!)
                            canvas.restore()
                        }
                        j++
                    }
                }
            }
        }
        if (pdfView!!.hasSelection) {
            pdfView!!.sourceToViewRectFF(pdfView!!.handleLeftPos, vr)
            var left = vr.left + drawableDeltaW
            pdfView!!.handleLeft!!.setBounds(
                (left - drawableWidth).toInt(),
                vr.bottom.toInt(), left.toInt(), (vr.bottom + drawableHeight).toInt()
            )
            pdfView!!.handleLeft!!.draw(canvas)
            //canvas.drawRect(pDocView.handleLeft.getBounds(), rectPaint);
            pdfView!!.sourceToViewRectFF(pdfView!!.handleRightPos, vr)
            left = vr.right - drawableDeltaW
            pdfView!!.handleRight!!.setBounds(
                left.toInt(),
                vr.bottom.toInt(),
                (left + drawableWidth).toInt(),
                (vr.bottom + drawableHeight).toInt()
            )
            pdfView!!.handleRight!!.draw(canvas)

            // canvas.drawRect(pDocView.handleRight.getBounds(), rectPaint);
            pdfView!!.sourceToViewCoord(pdfView!!.sCursorPos, vCursorPos)
            for (i in 0 until rectPoolSize) {
                val rectPage = rectPool[i]
                for (rI in rectPage) {
                    pdfView!!.sourceToViewRectFF(rI, vr)
                    matrix.reset()
                    val bmWidth = rI.width().toInt()
                    val bmHeight = rI.height().toInt()
                    pdfView!!.setMatrixArray(
                        pdfView!!.srcArray, 0f, 0f, bmWidth.toFloat(), 0f, 
                        bmWidth.toFloat(), bmHeight.toFloat(), 0f, bmHeight.toFloat()
                    )
                    pdfView!!.setMatrixArray(
                        pdfView!!.dstArray, vr.left, vr.top, vr.right, vr.top,
                        vr.right, vr.bottom, vr.left, vr.bottom
                    )
                    matrix.setPolyToPoly(pdfView!!.srcArray, 0, pdfView!!.dstArray, 0, 4)
                    matrix.postRotate(
                        0f, pdfView!!.getScreenWidth().toFloat(),
                        pdfView!!.getScreenHeight().toFloat()
                    )
                    canvas.save()
                    canvas.concat(matrix)
                    vr[0f, 0f, bmWidth.toFloat()] = bmHeight.toFloat()
                    canvas.drawRect(vr, rectPaint!!)
                    canvas.restore()
                }
            }
        }
    }

    /**
     * To draw search result after and before current page
     */
    private fun getSearchRecords(): ArrayList<SearchRecord?> {
        val list = ArrayList<SearchRecord?>()
        val currentPage = pdfView!!.getCurrentPage()
        if (PdfUtils.indexExists(pdfView!!.getPageCount(), currentPage - 1)) {
            val index = currentPage - 1
            if (pdfView!!.searchRecords!!.containsKey(index)) {
                val searchRecordPrev = pdfView!!.searchRecords!![index]
                if (searchRecordPrev != null) searchRecordPrev.currentPage = index
                list.add(searchRecordPrev)
            }
        }
        list.add(pdfView!!.searchRecords!![currentPage])
        if (PdfUtils.indexExists(pdfView!!.getPageCount(), currentPage + 1)) {
            val indexNext = currentPage + 1
            if (pdfView!!.searchRecords!!.containsKey(indexNext)) {
                val searchRecordNext = pdfView!!.searchRecords!![indexNext]
                if (searchRecordNext != null) searchRecordNext.currentPage = indexNext
                list.add(pdfView!!.searchRecords!![indexNext])
            }
        }
        return list
    }
}
