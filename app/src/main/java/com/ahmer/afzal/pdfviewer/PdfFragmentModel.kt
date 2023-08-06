package com.ahmer.afzal.pdfviewer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PdfFragmentModel @Inject constructor(private val pref: AppPreferencesManager) : ViewModel() {

    val searchDescription = MutableStateFlow("")

    val flow = pref.preferencesFlow

    fun updateAutoSpacing(isChecked: Boolean) {
        viewModelScope.launch {
            pref.updateAutoSpacing(isChecked)
        }
    }

    fun updatePageSnap(isChecked: Boolean) {
        viewModelScope.launch {
            pref.updatePageSnap(isChecked)
        }
    }

    fun updateViewHorizontal(isChecked: Boolean) {
        viewModelScope.launch {
            pref.updateViewHorizontal(isChecked)
        }
    }

    fun updateSpacing(int: Int) {
        viewModelScope.launch {
            pref.updateSpacing(int)
        }
    }
}