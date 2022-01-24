package com.ahmer.afzal.pdfium

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppPreferencesManager @Inject constructor(@ApplicationContext private val context: Context) {

    private object PreferencesKeys {
        val PDF_PAGE_SNAP = booleanPreferencesKey("PageSnapKey")
        val PDF_VIEW_CHANGE = booleanPreferencesKey("ViewChangeKey")
    }

    data class FilterPreferences(
        val pdfPageSnap: Boolean,
        val pdfViewChange: Boolean
    )

    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "RosePdfiumPrefs")

    val preferencesFlow = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                Log.e(Constants.LOG_TAG, "PreferencesException: ${exception.message}", exception)
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val pdfPageSnap = preferences[PreferencesKeys.PDF_PAGE_SNAP] ?: true
            val pdfSwapHandle = preferences[PreferencesKeys.PDF_VIEW_CHANGE] ?: false
            FilterPreferences(pdfPageSnap, pdfSwapHandle)
        }

    suspend fun updatePdfPageSnap(isChecked: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.PDF_PAGE_SNAP] = isChecked
        }
    }

    suspend fun updatePdfViewChange(isChecked: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.PDF_VIEW_CHANGE] = isChecked
        }
    }
}