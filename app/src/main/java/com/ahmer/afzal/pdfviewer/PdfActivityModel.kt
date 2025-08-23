package com.ahmer.afzal.pdfviewer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PdfActivityModel @Inject constructor(
    private val appDataStore: AppDataStore
) : ViewModel() {
    private val _uiState: MutableStateFlow<PdfUiState> = MutableStateFlow(value = PdfUiState())
    val pdfUiState: StateFlow<PdfUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                flow = appDataStore.isAutoSpacing,
                flow2 = appDataStore.isNightMode,
                flow3 = appDataStore.isPageSnap,
                flow4 = appDataStore.getSpacing,
                flow5 = appDataStore.isViewHorizontal
            ) { autoSpacing, nightMode, pageSnap, pageSpacing, viewHorizontal ->
                PdfUiState(
                    isAutoSpacing = autoSpacing,
                    isNightMode = nightMode,
                    isPageSnap = pageSnap,
                    spacing = pageSpacing,
                    isViewHorizontal = viewHorizontal
                )
            }.collect { newState ->
                _uiState.value = newState
            }
        }
    }

    fun updateAutoSpacing(isChecked: Boolean) {
        _uiState.value = _uiState.value.copy(isAutoSpacing = isChecked)
        viewModelScope.launch {
            appDataStore.updateAutoSpacing(value = isChecked)
        }
    }

    fun updateNightMode(isChecked: Boolean) {
        _uiState.value = _uiState.value.copy(isNightMode = isChecked)
        viewModelScope.launch {
            appDataStore.updateNightMode(value = isChecked)
        }
    }

    fun updatePageSnap(isChecked: Boolean) {
        _uiState.value = _uiState.value.copy(isPageSnap = isChecked)
        viewModelScope.launch {
            appDataStore.updatePageSnap(value = isChecked)
        }
    }

    fun updateSpacing(spacing: Int) {
        _uiState.value = _uiState.value.copy(spacing = spacing)
        viewModelScope.launch {
            appDataStore.updateSpacing(value = spacing)
        }
    }

    fun updateViewHorizontal(isChecked: Boolean) {
        _uiState.value = _uiState.value.copy(isViewHorizontal = isChecked)
        viewModelScope.launch {
            appDataStore.updateViewHorizontal(value = isChecked)
        }
    }

    fun loadLastPage(fileName: String): Flow<Int> {
        return appDataStore.getLastPage(fileName = fileName)
    }

    fun saveLastPage(fileName: String, page: Int) {
        viewModelScope.launch {
            appDataStore.saveLastPage(fileName = fileName, page = page)
        }
    }
}