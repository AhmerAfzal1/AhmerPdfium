package com.ahmer.afzal.pdfviewer

import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PdfFragmentModel @Inject constructor(
    private val appDataStore: AppDataStore
) : ViewModel(), LifecycleObserver {
    private val stopTime: Long = 5000L

    val search: MutableStateFlow<String> = MutableStateFlow("")

    val isAutoSpacing: StateFlow<Boolean> = appDataStore.isAutoSpacing.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = stopTime),
        initialValue = true
    )

    val isPageSnap: StateFlow<Boolean> = appDataStore.isPageSnap.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = stopTime),
        initialValue = true
    )

    val getSpacing: StateFlow<Int> = appDataStore.getSpacing.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = stopTime),
        initialValue = 5
    )

    val isViewHorizontal: StateFlow<Boolean> = appDataStore.isViewHorizontal.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = stopTime),
        initialValue = false
    )

    fun updateAutoSpacing(isChecked: Boolean) {
        viewModelScope.launch {
            appDataStore.updateAutoSpacing(isChecked = isChecked)
        }
    }

    fun updatePageSnap(isChecked: Boolean) {
        viewModelScope.launch {
            appDataStore.updatePageSnap(isChecked = isChecked)
        }
    }

    fun updateSpacing(spacing: Int) {
        viewModelScope.launch {
            appDataStore.updateSpacing(px = spacing)
        }
    }

    fun updateViewChange(isChecked: Boolean) {
        viewModelScope.launch {
            appDataStore.updateViewHorizontal(isChecked = isChecked)
        }
    }
}