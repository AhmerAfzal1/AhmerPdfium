package com.ahmer.pdfviewer.model

import android.graphics.RectF

/**
 * Stores the highlight rects and start-end index of one matching item on a page
 */
class SearchRecordItem(val st: Int, val ed: Int, val rects: Array<RectF?>)