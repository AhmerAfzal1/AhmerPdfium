package com.ahmer.afzal.pdfviewer

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
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
        val AUTO_SPACING = booleanPreferencesKey("AutoSpacingKey")
        val PAGE_SNAP = booleanPreferencesKey("SnapKey")
        val SPACING = intPreferencesKey("SpacingKey")
        val VIEW_HORIZONTAL = booleanPreferencesKey("ViewKey")
    }

    data class FilterPreferences(
        val isAutoSpacing: Boolean,
        val isPageSnap: Boolean,
        val isViewHorizontal: Boolean,
        val spacing: Int
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
            val isAutoSpacing = preferences[PreferencesKeys.AUTO_SPACING] ?: true
            val isPageSnap = preferences[PreferencesKeys.PAGE_SNAP] ?: true
            val isViewHorizontal = preferences[PreferencesKeys.VIEW_HORIZONTAL] ?: false
            val setSpacing = preferences[PreferencesKeys.SPACING] ?: 5
            FilterPreferences(isAutoSpacing, isPageSnap, isViewHorizontal, setSpacing)
        }

    suspend fun updateAutoSpacing(isChecked: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.AUTO_SPACING] = isChecked
        }
    }

    suspend fun updatePageSnap(isChecked: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.PAGE_SNAP] = isChecked
        }
    }

    suspend fun updateViewHorizontal(isChecked: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.VIEW_HORIZONTAL] = isChecked
        }
    }

    suspend fun updateSpacing(px: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SPACING] = px
        }
    }
}