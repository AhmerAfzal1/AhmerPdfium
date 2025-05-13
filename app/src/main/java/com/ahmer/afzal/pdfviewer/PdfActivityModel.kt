package com.ahmer.afzal.pdfviewer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PdfActivityModel @Inject constructor(
    private val appDataStore: AppDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(PdfUiState())
    val uiState: StateFlow<PdfUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    init {
        viewModelScope.launch {
            appDataStore.isAutoSpacing.collect { autoSpacing ->
                _uiState.update { it.copy(isAutoSpacing = autoSpacing) }
            }
        }

        viewModelScope.launch {
            appDataStore.isPageSnap.collect { pageSnap ->
                _uiState.update { it.copy(isPageSnap = pageSnap) }
            }
        }

        viewModelScope.launch {
            appDataStore.getSpacing.collect { spacing ->
                _uiState.update { it.copy(spacing = spacing) }
            }
        }

        viewModelScope.launch {
            appDataStore.isViewHorizontal.collect { viewHorizontal ->
                _uiState.update { it.copy(isViewHorizontal = viewHorizontal) }
            }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleNightMode() {
        _uiState.update { it.copy(isNightMode = !it.isNightMode) }
    }

    fun togglePageSnap() {
        val newState = !_uiState.value.isPageSnap
        _uiState.update {
            it.copy(
                isPageSnap = newState,
                isAutoSpacing = newState,
                spacing = if (newState) 5 else 10
            )
        }

        viewModelScope.launch {
            appDataStore.updatePageSnap(newState)
            appDataStore.updateAutoSpacing(newState)
            appDataStore.updateSpacing(if (newState) 5 else 10)
        }
    }

    fun toggleViewOrientation() {
        val newState = !_uiState.value.isViewHorizontal
        _uiState.update { it.copy(isViewHorizontal = newState) }
        viewModelScope.launch {
            appDataStore.updateViewHorizontal(newState)
        }
    }

    fun setPdfLoaded(isLoaded: Boolean) {
        _uiState.update { it.copy(isPdfLoaded = isLoaded) }
    }
}