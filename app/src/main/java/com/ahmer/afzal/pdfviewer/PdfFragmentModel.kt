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
class PdfFragmentModel @Inject constructor(
    private val appDataStore: AppDataStore
) : ViewModel() {
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _uiState = MutableStateFlow(PdfUiState())
    val pdfUiState: StateFlow<PdfUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                appDataStore.isAutoSpacing,
                appDataStore.isNightMode,
                appDataStore.isPageSnap,
                appDataStore.getSpacing,
                appDataStore.isViewHorizontal
            ) { mAutoSpacing, mNight, mPageSnap, mSpacing, mViewHorizontal ->
                PdfUiState(
                    isAutoSpacing = mAutoSpacing,
                    isNightMode = mNight,
                    isPageSnap = mPageSnap,
                    spacing = mSpacing,
                    isViewHorizontal = mViewHorizontal
                )
            }.collect { newState ->
                _uiState.value = newState
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun updateAutoSpacing(isChecked: Boolean) {
        _uiState.value = _uiState.value.copy(isAutoSpacing = isChecked)
        viewModelScope.launch {
            appDataStore.updateAutoSpacing(isChecked)
        }
    }

    fun updateNightMode(isChecked: Boolean) {
        _uiState.value = _uiState.value.copy(isNightMode = isChecked)
        viewModelScope.launch {
            appDataStore.updateNightMode(isChecked)
        }
    }

    fun updatePageSnap(isChecked: Boolean) {
        _uiState.value = _uiState.value.copy(isPageSnap = isChecked)
        viewModelScope.launch {
            appDataStore.updatePageSnap(isChecked)
        }
    }

    fun updateSpacing(spacing: Int) {
        _uiState.value = _uiState.value.copy(spacing = spacing)
        viewModelScope.launch {
            appDataStore.updateSpacing(spacing)
        }
    }

    fun updateViewHorizontal(isChecked: Boolean) {
        _uiState.value = _uiState.value.copy(isViewHorizontal = isChecked)
        viewModelScope.launch {
            appDataStore.updateViewHorizontal(isChecked)
        }
    }

    fun loadLastPage(fileName: String): Flow<Int> {
        return appDataStore.getLastPage(fileName)
    }

    fun saveLastPage(fileName: String, page: Int) {
        viewModelScope.launch {
            appDataStore.saveLastPage(fileName, page)
        }
    }
}