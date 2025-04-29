package com.ahmer.afzal.pdfviewer

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppDataStore @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private object DataStoreKeys {
        val autoSpacing = booleanPreferencesKey(name = "AutoSpacingKey")
        val pageSnap = booleanPreferencesKey(name = "SnapKey")
        val spacing = intPreferencesKey(name = "SpacingKey")
        val viewHorizontal = booleanPreferencesKey(name = "ViewKey")
    }
    suspend fun saveLastPage(fileName: String, page: Int) = dataStore.edit { preferences ->
        val pageKey = intPreferencesKey("last_page_${fileName.hashCode()}")
        preferences[pageKey] = page
    }

    suspend fun updateAutoSpacing(isChecked: Boolean) = dataStore.edit { preferences ->
        preferences[DataStoreKeys.autoSpacing] = isChecked
    }

    suspend fun updatePageSnap(isChecked: Boolean) = dataStore.edit { preferences ->
        preferences[DataStoreKeys.pageSnap] = isChecked
    }

    suspend fun updateSpacing(px: Int) = dataStore.edit { preferences ->
        preferences[DataStoreKeys.spacing] = px
    }

    suspend fun updateViewHorizontal(isChecked: Boolean) = dataStore.edit { preferences ->
        preferences[DataStoreKeys.viewHorizontal] = isChecked
    }

    val isAutoSpacing: Flow<Boolean> = dataStore.data.map { preference ->
        preference[DataStoreKeys.autoSpacing] ?: true
    }

    val isPageSnap: Flow<Boolean> = dataStore.data.map { preference ->
        preference[DataStoreKeys.pageSnap] ?: true
    }

    val getSpacing: Flow<Int> = dataStore.data.map { preference ->
        preference[DataStoreKeys.spacing] ?: 5
    }

    val isViewHorizontal: Flow<Boolean> = dataStore.data.map { preference ->
        preference[DataStoreKeys.viewHorizontal] ?: false
    }

    fun getLastPage(fileName: String): Flow<Int> = dataStore.data.map { preferences ->
        val pageKey = intPreferencesKey("last_page_${fileName.hashCode()}")
        preferences[pageKey] ?: 0
    }
}