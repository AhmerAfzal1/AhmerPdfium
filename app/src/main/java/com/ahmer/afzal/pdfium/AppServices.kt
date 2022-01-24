package com.ahmer.afzal.pdfium

import android.inputmethodservice.InputMethodService
import android.os.Build
import android.view.inputmethod.InputMethodManager

object AppServices : InputMethodService() {

    @JvmStatic
    fun showKeyboard() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            requestShowSelf(InputMethodManager.SHOW_IMPLICIT)
        }
    }

    @JvmStatic
    fun hideKeyboard() {
        requestHideSelf(InputMethodManager.HIDE_NOT_ALWAYS)
    }
}