package com.hebi.tripcanvas.data

import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.hebi.tripcanvas.designsystem.theme.TripThemeMode
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TripPreferencesRepositoryTest {
    private lateinit var directory: File
    private lateinit var scope: CoroutineScope
    private lateinit var store: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>
    private lateinit var repository: TripPreferencesRepository

    @Before
    fun setUp() {
        directory = Files.createTempDirectory("tripcanvas-preferences-test").toFile()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        store = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { File(directory, "preferences.preferences_pb") }
        )
        repository = TripPreferencesRepository(store)
    }

    @After
    fun tearDown() {
        scope.cancel()
        directory.deleteRecursively()
    }

    @Test
    fun defaultsToWelcomeAndSystemTheme() = runBlocking {
        val preferences = repository.preferences.first()
        assertFalse(preferences.onboardingCompleted)
        assertEquals(TripThemeMode.System, preferences.themeMode)
    }

    @Test
    fun completedWelcomeAndThemeRemainReadable() = runBlocking {
        repository.completeOnboarding()
        repository.saveThemeMode(TripThemeMode.Dark)

        val refreshed = TripPreferencesRepository(store).preferences.first()
        assertTrue(refreshed.onboardingCompleted)
        assertEquals(TripThemeMode.Dark, refreshed.themeMode)

        repository.saveThemeMode(TripThemeMode.Light)
        assertEquals(TripThemeMode.Light, repository.preferences.first().themeMode)
    }

    @Test
    fun unrecognizedThemeValueFallsBackToSystem() = runBlocking {
        store.edit { saved ->
            saved[stringPreferencesKey("theme_mode")] = "unrecognized-theme"
        }
        assertEquals(TripThemeMode.System, repository.preferences.first().themeMode)
    }
}
