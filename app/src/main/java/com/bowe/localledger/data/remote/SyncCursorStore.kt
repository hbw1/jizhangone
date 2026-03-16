package com.bowe.localledger.data.remote

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.syncDataStore by preferencesDataStore(name = "sync_store")

class SyncCursorStore(
    private val context: Context,
) {
    suspend fun getCursor(remoteBookId: String): String? {
        val prefs = context.syncDataStore.data.first()
        return prefs[cursorKey(remoteBookId)]
    }

    suspend fun saveCursor(remoteBookId: String, cursor: String) {
        context.syncDataStore.edit { prefs ->
            prefs[cursorKey(remoteBookId)] = cursor
        }
    }

    private fun cursorKey(remoteBookId: String) = stringPreferencesKey("cursor_$remoteBookId")
}
