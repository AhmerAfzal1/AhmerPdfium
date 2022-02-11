package com.ahmer.pdfviewer

import android.icu.text.BreakIterator
import android.os.Build

class BreakIteratorHelper {

    private var breakIteratorI: BreakIterator? = null
    private var breakIteratorJ: java.text.BreakIterator? = null

    fun setText(text: String?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            breakIteratorI!!.setText(text)
        } else {
            breakIteratorJ!!.setText(text)
        }
    }

    fun following(offset: Int): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            breakIteratorI!!.following(offset)
        } else {
            breakIteratorJ!!.following(offset)
        }
    }

    fun previous(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            breakIteratorI!!.previous()
        } else {
            breakIteratorJ!!.previous()
        }
    }

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            breakIteratorI = BreakIterator.getWordInstance()
        } else {
            breakIteratorJ = java.text.BreakIterator.getWordInstance()
        }
    }
}
