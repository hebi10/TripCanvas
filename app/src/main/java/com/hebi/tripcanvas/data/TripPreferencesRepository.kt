package com.hebi.tripcanvas.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.hebi.tripcanvas.designsystem.theme.TripThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.tripCanvasDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "tripcanvas_preferences"
)

class TripPreferencesRepository(private val dataStore: DataStore<Preferences>) {
    private val onboardedKey = booleanPreferencesKey("onboarding_completed")
    private val themeKey = stringPreferencesKey("theme_mode")

    val preferences: Flow<TripUserPreferences> = dataStore.data.map { saved ->
        TripUserPreferences(
            onboardingCompleted = saved[onboardedKey] ?: false,
            themeMode = TripThemeMode.fromStoredValue(saved[themeKey])
        )
    }

    suspend fun completeOnboarding() {
        dataStore.edit { it[onboardedKey] = true }
    }

    suspend fun saveThemeMode(mode: TripThemeMode) {
        dataStore.edit { it[themeKey] = mode.name }
    }
}

fun tripPreferencesRepository(context: Context): TripPreferencesRepository =
    TripPreferencesRepository(context.applicationContext.tripCanvasDataStore)
