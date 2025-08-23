package com.ahmer.afzal.pdfviewer

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppDataStore @Inject constructor(context: Context) {
    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = Constants.DATA_STORE_NAME)
    private val dataStore: DataStore<Preferences> = context.dataStore
    private val autoSpacing: Preferences.Key<Boolean> = booleanPreferencesKey(name = "isAutoSpacing")
    private val nightMode: Preferences.Key<Boolean> = booleanPreferencesKey(name = "isNightMode")
    private val pageSnap: Preferences.Key<Boolean> = booleanPreferencesKey(name = "isPageSnap")
    private val viewHorizontal: Preferences.Key<Boolean> = booleanPreferencesKey(name = "isViewHorizontal")
    private val spacing: Preferences.Key<Int> = intPreferencesKey(name = "spacing")

    suspend fun saveLastPage(fileName: String, page: Int) = dataStore.edit { preferences ->
        val pageKey: Preferences.Key<Int> = intPreferencesKey(name = "last_page_${fileName.hashCode()}")
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
            val pageKey: Preferences.Key<Int> = intPreferencesKey(name = "last_page_${fileName.hashCode()}")
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