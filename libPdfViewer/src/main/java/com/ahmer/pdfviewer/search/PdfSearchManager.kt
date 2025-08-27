package com.ahmer.pdfviewer.search

import android.content.Context
import android.graphics.RectF
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import com.ahmer.pdfium.FindFlags
import com.ahmer.pdfium.FindResult
import com.ahmer.pdfium.PdfTextPage
import com.ahmer.pdfviewer.PDFView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PdfSearchManager(
    private val pdfView: PDFView,
    private val searchContainer: View,
    private val searchQuery: EditText,
    private val matchCase: CheckBox,
    private val wholeWord: CheckBox,
    private val searchPrevBtn: ImageButton,
    private val searchNextBtn: ImageButton,
    private val closeSearchBtn: ImageButton,
    private val searchCounter: TextView
) {
    private val coroutineScope: CoroutineScope = CoroutineScope(context = Dispatchers.Main + SupervisorJob())
    private var activeSearchJob: Job? = null
    private var currentMatchPosition: Int = -1
    private var lastText: String = ""
    private var searchMatches: List<SearchResult> = emptyList()

    init {
        setupListeners()
    }

    private fun triggerSearch(query: String) {
        activeSearchJob?.cancel()
        activeSearchJob = coroutineScope.launch {
            if (query.isNotEmpty()) {
                delay(timeMillis = 300L)
                performSearch(query = query)
            } else {
                clearSearch()
            }
        }
    }

    private fun setupListeners() {
        closeSearchBtn.setOnClickListener { hideSearch() }
        searchPrevBtn.setOnClickListener { showPrevMatch() }
        searchNextBtn.setOnClickListener { showNextMatch() }
        matchCase.setOnCheckedChangeListener { _, _ -> triggerSearch(query = searchQuery.text.toString()) }
        wholeWord.setOnCheckedChangeListener { _, _ -> triggerSearch(query = searchQuery.text.toString()) }

        searchQuery.addTextChangedListener(object : TextWatcher {

            override fun afterTextChanged(s: Editable?) {
                val query: String = s?.toString() ?: ""
                if (query == lastText) return
                lastText = query

                triggerSearch(query = lastText)
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun processSearchResults(
        pdfTextPage: PdfTextPage,
        findResult: FindResult,
        pageIndex: Int,
        results: MutableList<SearchResult>
    ) {
        while (findResult.findNext() && coroutineScope.isActive) {
            val charIndex: Int = findResult.getSchResultIndex()
            val charCount: Int = findResult.getSchCount()
            val rects: List<RectF> = pdfTextPage.getTextRangeRects(wordRanges = intArrayOf(charIndex, charCount))
                ?.map { it.rect } ?: emptyList()

            //Log.v("PdfSearch", "$query: found at charIndex: $charIndex")
            //Log.v("PdfSearch", "$query: found search char length: $countSchResult")
            //Log.v("PdfSearch", "$query: textPage charCount: ${textPage.charCount}")
            //Log.v("PdfSearch", "$query: rects textRageReact: $rects")
            //Log.v("PdfSearch", "$query: rects getBox: $getBox")

            results.add(SearchResult(pageIndex = pageIndex, startIndex = charIndex, length = charCount, rect = rects))
        }
    }

    private fun updateSearchResults(results: List<SearchResult>) {
        searchMatches = results
        if (searchMatches.isNotEmpty()) {
            currentMatchPosition = 0
            jumpToCurrentMatch()
        }
        updateSearchCounterText()
    }

    private suspend fun performSearch(query: String) {
        clearSearch()

        withContext(context = Dispatchers.IO) {
            val flags: Set<FindFlags> = buildSearchOptions()
            val results: MutableList<SearchResult> = mutableListOf()
            val totalPages: Int = pdfView.pagesCount

            for (pageIndex in 0 until totalPages) {
                if (!isActive) return@withContext

                try {
                    pdfView.openPage(pageIndex = pageIndex)
                    pdfView.openTextPage(pageIndex = pageIndex)?.use { textPage ->
                        textPage.startTextSearch(query = query, flags = flags, startIndex = 0)?.use { findResult ->
                            processSearchResults(
                                pdfTextPage = textPage,
                                findResult = findResult,
                                pageIndex = pageIndex,
                                results = results
                            )
                        }
                    } ?: run {
                        Log.w("PdfSearch", "Could not open text page for index $pageIndex")
                    }
                } catch (e: Exception) {
                    Log.e("PdfSearch", "Search failed on page $pageIndex", e)
                }
            }
            withContext(context = Dispatchers.Main) {
                if (isActive) updateSearchResults(results = results)
            }
        }
    }

    private fun buildSearchOptions(): Set<FindFlags> {
        return mutableSetOf<FindFlags>().apply {
            add(FindFlags.NONE)
            if (matchCase.isChecked) add(FindFlags.MATCH_CASE)
            if (wholeWord.isChecked) add(FindFlags.MATCH_WHOLE_WORD)
        }
    }

    private fun jumpToCurrentMatch() {
        if (currentMatchPosition !in searchMatches.indices) return
        val searchResult: SearchResult = searchMatches[currentMatchPosition]
        pdfView.jumpTo(page = searchResult.pageIndex, withAnimation = true)
    }

    private fun showNextMatch() {
        if (searchMatches.isEmpty()) return
        currentMatchPosition = (currentMatchPosition + 1) % searchMatches.size
        jumpToCurrentMatch()
        updateSearchCounterText()
    }

    private fun showPrevMatch() {
        if (searchMatches.isEmpty()) return
        currentMatchPosition = (currentMatchPosition - 1).mod(searchMatches.size)
        jumpToCurrentMatch()
        updateSearchCounterText()
    }

    private fun updateSearchCounterText() {
        searchCounter.text = if (searchMatches.isNotEmpty()) {
            "${currentMatchPosition + 1}/${searchMatches.size}"
        } else {
            "0/0"
        }
    }

    private fun showKeyboard() {
        val imm = pdfView.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(searchQuery, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideKeyboard() {
        val imm = pdfView.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(searchQuery.windowToken, 0)
    }

    private fun clearSearch() {
        lastText = ""
        searchMatches = emptyList()
        currentMatchPosition = -1
        updateSearchCounterText()
    }

    fun showSearch() {
        searchContainer.visibility = View.VISIBLE
        searchCounter.visibility = View.VISIBLE
        searchQuery.requestFocus()
        showKeyboard()
    }

    private fun hideSearch() {
        searchContainer.visibility = View.GONE
        searchCounter.visibility = View.GONE
        hideKeyboard()
        clearSearch()
        activeSearchJob?.cancel()
        searchMatches = emptyList()
    }

    fun destroy() {
        coroutineScope.cancel()
        hideSearch()
    }

    data class SearchResult(
        val pageIndex: Int,
        val startIndex: Int,
        val length: Int,
        val rect: List<RectF>
    ) {
        init {
            require(value = startIndex >= 0) { "Invalid startIndex: $startIndex" }
            require(value = length > 0) { "Invalid length: $length" }
        }
    }
}