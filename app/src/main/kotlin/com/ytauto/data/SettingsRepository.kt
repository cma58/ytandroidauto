package com.ytauto.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "ytauto_settings")

object SettingsRepository {
    private val KEY_SPONSORBLOCK = booleanPreferencesKey("sponsorblock_enabled")
    private val KEY_AUTO_SYNC = booleanPreferencesKey("auto_sync_enabled")

    fun sponsorBlockEnabled(context: Context): Flow<Boolean> =
        context.dataStore.data.map { it[KEY_SPONSORBLOCK] ?: true }

    fun autoSyncEnabled(context: Context): Flow<Boolean> =
        context.dataStore.data.map { it[KEY_AUTO_SYNC] ?: true }

    suspend fun setSponsorBlock(context: Context, enabled: Boolean) {
        context.dataStore.edit { it[KEY_SPONSORBLOCK] = enabled }
    }

    suspend fun setAutoSync(context: Context, enabled: Boolean) {
        context.dataStore.edit { it[KEY_AUTO_SYNC] = enabled }
    }
}
