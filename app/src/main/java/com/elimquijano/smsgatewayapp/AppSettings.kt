package com.elimquijano.smsgatewayapp

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class AppSettings(private val context: Context) {

    companion object {
        val KEY_SERVER_URL = stringPreferencesKey("server_url")
        // NUEVO: Clave para guardar los logs
        val KEY_LOGS = stringPreferencesKey("app_logs")
    }

    // --- URL ---
    val getServerUrl: Flow<String?> = context.dataStore.data.map { it[KEY_SERVER_URL] }
    suspend fun saveServerUrl(url: String) {
        context.dataStore.edit { it[KEY_SERVER_URL] = url }
    }

    // --- Logs ---
    val getLogs: Flow<String> = context.dataStore.data.map { it[KEY_LOGS] ?: "" }

    suspend fun appendLog(logMessage: String) {
        context.dataStore.edit { settings ->
            val currentLogs = settings[KEY_LOGS] ?: ""
            val lines = currentLogs.lines()
            // Mantenemos solo las últimas 200 líneas para no crear un archivo enorme
            val last200Lines = if (lines.size > 200) lines.takeLast(200).joinToString("\n") else currentLogs
            settings[KEY_LOGS] = if (last200Lines.isEmpty()) logMessage else "$last200Lines\n$logMessage"
        }
    }

    suspend fun clearLogs() {
        context.dataStore.edit { it[KEY_LOGS] = "" }
    }
}