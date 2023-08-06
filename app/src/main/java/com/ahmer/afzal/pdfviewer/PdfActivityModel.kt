package com.ahmer.afzal.pdfviewer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PdfActivityModel @Inject constructor(private val pref: AppPreferencesManager) : ViewModel() {

    val searchDescription = MutableStateFlow("")

    val flow = pref.preferencesFlow

    fun updatePdfPageSnap(isChecked: Boolean) {
        viewModelScope.launch {
            pref.updatePageSnap(isChecked)
        }
    }

    fun updatePdfViewChange(isChecked: Boolean) {
        viewModelScope.launch {
            pref.updateViewHorizontal(isChecked)
        }
    }
}