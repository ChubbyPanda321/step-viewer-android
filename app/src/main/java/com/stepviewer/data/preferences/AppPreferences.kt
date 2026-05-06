package com.stepviewer.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.stepviewer.data.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

class AppPreferences(private val context: Context) {

    companion object {
        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        private val KEY_LAST_MATERIAL = stringPreferencesKey("last_material")
        private val KEY_MEASUREMENT_ENABLED = booleanPreferencesKey("measurement_enabled")

        private const val DEFAULT_THEME = "LIGHT"
        private const val DEFAULT_MATERIAL = "MC 17-4PH Stainless Steel"
    }

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        try {
            ThemeMode.valueOf(prefs[KEY_THEME_MODE] ?: DEFAULT_THEME)
        } catch (_: IllegalArgumentException) {
            ThemeMode.LIGHT
        }
    }

    val lastMaterialName: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_LAST_MATERIAL] ?: DEFAULT_MATERIAL
    }

    val measurementEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_MEASUREMENT_ENABLED] ?: false
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { prefs ->
            prefs[KEY_THEME_MODE] = mode.name
        }
    }

    suspend fun setLastMaterial(name: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LAST_MATERIAL] = name
        }
    }

    suspend fun setMeasurementEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_MEASUREMENT_ENABLED] = enabled
        }
    }
}
