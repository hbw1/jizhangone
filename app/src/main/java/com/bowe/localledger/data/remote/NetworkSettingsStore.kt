package com.bowe.localledger.data.remote

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.networkDataStore by preferencesDataStore(name = "network_store")

class NetworkSettingsStore(
    private val context: Context,
) {
    val baseUrl: Flow<String> = context.networkDataStore.data
        .catch { error ->
            if (error is IOException) {
                emit(emptyPreferences())
            } else {
                throw error
            }
        }
        .map { prefs ->
            when (val saved = prefs[Keys.BASE_URL]) {
                null -> NetworkConfig.DEFAULT_BASE_URL
                NetworkConfig.LEGACY_TAILSCALE_BASE_URL -> NetworkConfig.DEFAULT_BASE_URL
                else -> normalizeBaseUrl(saved)
            }
        }

    suspend fun getBaseUrl(): String = baseUrl.first()

    suspend fun saveBaseUrl(url: String) {
        context.networkDataStore.edit { prefs ->
            prefs[Keys.BASE_URL] = normalizeBaseUrl(url)
        }
    }

    private fun normalizeBaseUrl(input: String): String {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return NetworkConfig.DEFAULT_BASE_URL
        if (trimmed == NetworkConfig.LEGACY_TAILSCALE_BASE_URL.removeSuffix("/")) {
            return NetworkConfig.DEFAULT_BASE_URL
        }
        return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
    }

    private object Keys {
        val BASE_URL = stringPreferencesKey("base_url")
    }
}
