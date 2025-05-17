package com.ahmer.afzal.pdfviewer

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppDataStore @Inject constructor(context: Context) {
    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = Constants.DATA_STORE_NAME)
    private val dataStore = context.dataStore

    private val autoSpacing = booleanPreferencesKey(name = "isAutoSpacing")
    private val nightMode = booleanPreferencesKey(name = "isNightMode")
    private val pageSnap = booleanPreferencesKey(name = "isPageSnap")
    private val viewHorizontal = booleanPreferencesKey(name = "isViewHorizontal")
    private val spacing = intPreferencesKey(name = "spacing")

    suspend fun saveLastPage(fileName: String, page: Int) = dataStore.edit { preferences ->
        val pageKey = intPreferencesKey("last_page_${fileName.hashCode()}")
        preferences[pageKey] = page
    }

    suspend fun updateAutoSpacing(value: Boolean) = dataStore.edit { preferences ->
        preferences[autoSpacing] = value
    }

    suspend fun updateNightMode(value: Boolean) = dataStore.edit { preferences ->
        preferences[nightMode] = value
    }

    suspend fun updatePageSnap(value: Boolean) = dataStore.edit { preferences ->
        preferences[pageSnap] = value
    }

    suspend fun updateSpacing(value: Int) = dataStore.edit { preferences ->
        preferences[spacing] = value
    }

    suspend fun updateViewHorizontal(value: Boolean) = dataStore.edit { preferences ->
        preferences[viewHorizontal] = value
    }

    fun getLastPage(fileName: String): Flow<Int> = dataStore.data
        .map { preferences ->
            val pageKey = intPreferencesKey("last_page_${fileName.hashCode()}")
            preferences[pageKey] ?: 0
        }
        .distinctUntilChanged()

    val isAutoSpacing: Flow<Boolean> = dataStore.data
        .map { preference -> preference[autoSpacing] ?: true }
        .distinctUntilChanged()

    val isNightMode: Flow<Boolean> = dataStore.data
        .map { preference -> preference[nightMode] ?: false }
        .distinctUntilChanged()

    val isPageSnap: Flow<Boolean> = dataStore.data
        .map { preference -> preference[pageSnap] ?: true }
        .distinctUntilChanged()

    val getSpacing: Flow<Int> = dataStore.data
        .map { preference -> preference[spacing] ?: 5 }
        .distinctUntilChanged()

    val isViewHorizontal: Flow<Boolean> = dataStore.data
        .map { preference -> preference[viewHorizontal] ?: false }
        .distinctUntilChanged()
}