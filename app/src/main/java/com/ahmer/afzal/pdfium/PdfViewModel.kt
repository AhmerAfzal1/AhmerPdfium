package com.ahmer.afzal.pdfium

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PdfViewModel @Inject constructor(private val pref: AppPreferencesManager) : ViewModel() {

    val searchDescription = MutableStateFlow("")

    val flow = pref.preferencesFlow

    fun updatePdfPageSnap(isChecked: Boolean) {
        viewModelScope.launch {
            pref.updatePdfPageSnap(isChecked)
        }
    }

    fun updatePdfViewChange(isChecked: Boolean) {
        viewModelScope.launch {
            pref.updatePdfViewChange(isChecked)
        }
    }
}