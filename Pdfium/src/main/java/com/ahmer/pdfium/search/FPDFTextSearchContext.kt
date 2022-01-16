package com.ahmer.pdfium.search

abstract class FPDFTextSearchContext protected constructor(
    override val pageIndex: Int,
    override val query: String,
    override val isMatchCase: Boolean,
    override val isMatchWholeWord: Boolean
) :
    TextSearchContext {
    protected var mHasNext = true
    protected var mHasPrev = false
    override fun hasNext(): Boolean {
        return countResult() > 0 || mHasNext
    }

    override fun hasPrev(): Boolean {
        return countResult() > 0 || mHasPrev
    }

    override fun startSearch() {
        searchNext()
    }

    override fun stopSearch() {}

    init {
        prepareSearch()
    }
}