package com.hebi.tripcanvas

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.hebi.tripcanvas.data.TripUserPreferences
import com.hebi.tripcanvas.data.tripPreferencesRepository
import com.hebi.tripcanvas.designsystem.components.TripErrorState
import com.hebi.tripcanvas.designsystem.components.TripLoadingState
import com.hebi.tripcanvas.designsystem.theme.TripCanvasTheme
import com.hebi.tripcanvas.designsystem.theme.TripDesign
import com.hebi.tripcanvas.designsystem.theme.TripThemeMode
import com.hebi.tripcanvas.designsystem.theme.isTripDarkMode
import com.hebi.tripcanvas.navigation.TripDestinations
import com.hebi.tripcanvas.screens.HomeScreen
import com.hebi.tripcanvas.screens.SettingsScreen
import com.hebi.tripcanvas.screens.TripWorkspaceScreen
import com.hebi.tripcanvas.screens.WelcomeScreen
import com.hebi.tripcanvas.showcase.DesignSystemScreen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private sealed interface SettingsLoadState {
    data object Loading : SettingsLoadState
    data object Failed : SettingsLoadState
    data class Ready(val preferences: TripUserPreferences) : SettingsLoadState
}

@Composable
fun TripApp(onThemeResolved: (Boolean) -> Unit) {
    val context = LocalContext.current.applicationContext
    val repository = remember(context) { tripPreferencesRepository(context) }
    val scope = rememberCoroutineScope()
    var retryCount by remember { mutableIntStateOf(0) }
    val settingsFlow = remember(repository, retryCount) {
        repository.preferences
            .map<TripUserPreferences, SettingsLoadState> { SettingsLoadState.Ready(it) }
            .catch { emit(SettingsLoadState.Failed) }
    }
    val settingsState by settingsFlow.collectAsState(initial = SettingsLoadState.Loading)

    val ready = settingsState as? SettingsLoadState.Ready
    val mode = ready?.preferences?.themeMode ?: TripThemeMode.System

    var onboardingSaving by remember { mutableStateOf(false) }
    var onboardingSaveFailed by remember { mutableStateOf(false) }
    var themeSaving by remember { mutableStateOf(false) }
    var themeSaveFailed by remember { mutableStateOf(false) }

    TripCanvasTheme(mode = mode) {
        val isDark = isTripDarkMode(mode)
        SideEffect { onThemeResolved(isDark) }

        when (val state = settingsState) {
            SettingsLoadState.Loading -> {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = TripDesign.palette.background
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        TripLoadingState(label = "설정을 불러오는 중")
                    }
                }
            }
            SettingsLoadState.Failed -> {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = TripDesign.palette.background
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        TripErrorState(
                            title = "앱 설정을 불러오지 못했습니다",
                            description = "설정 데이터에 접근할 수 없습니다. 다시 시도해 주세요.",
                            onRetry = { retryCount++ }
                        )
                    }
                }
            }
            is SettingsLoadState.Ready -> {
                key(state.preferences.onboardingCompleted) {
                    val navController = rememberNavController()

                    val updateTheme: (TripThemeMode) -> Unit = { selected ->
                        if (!themeSaving && selected != mode) {
                            themeSaving = true
                            themeSaveFailed = false
                            scope.launch {
                                try {
                                    repository.saveThemeMode(selected)
                                } catch (error: CancellationException) {
                                    throw error
                                } catch (error: Exception) {
                                    themeSaveFailed = true
                                } finally {
                                    themeSaving = false
                                }
                            }
                        }
                    }

                    NavHost(
                        navController = navController,
                        startDestination = TripDestinations.startDestination(
                            state.preferences.onboardingCompleted
                        ),
                        enterTransition = { EnterTransition.None },
                        exitTransition = { ExitTransition.None },
                        popEnterTransition = { EnterTransition.None },
                        popExitTransition = { ExitTransition.None }
                    ) {
                        composable(TripDestinations.Welcome) {
                            WelcomeScreen(
                                isSaving = onboardingSaving,
                                saveFailed = onboardingSaveFailed,
                                onContinue = {
                                    if (!onboardingSaving) {
                                        onboardingSaving = true
                                        onboardingSaveFailed = false
                                        scope.launch {
                                            try {
                                                repository.completeOnboarding()
                                            } catch (error: CancellationException) {
                                                throw error
                                            } catch (error: Exception) {
                                                onboardingSaveFailed = true
                                            } finally {
                                                onboardingSaving = false
                                            }
                                        }
                                    }
                                }
                            )
                        }
                        composable(TripDestinations.Home) {
                            HomeScreen(
                                onOpenSettings = {
                                    navController.navigate(TripDestinations.Settings)
                                }
                            )
                        }
                        composable(TripDestinations.Settings) {
                            SettingsScreen(
                                themeMode = mode,
                                themeSaving = themeSaving,
                                themeSaveFailed = themeSaveFailed,
                                onThemeModeChange = updateTheme,
                                onBack = { navController.popBackStack() },
                                onOpenWorkspacePreview = {
                                    navController.navigate(TripDestinations.WorkspacePreview)
                                },
                                onOpenDesignSystemPreview = {
                                    navController.navigate(TripDestinations.DesignSystemPreview)
                                }
                            )
                        }
                        composable(TripDestinations.WorkspacePreview) {
                            TripWorkspaceScreen(
                                onBack = { navController.popBackStack() },
                                isPreview = true
                            )
                        }
                        composable(TripDestinations.DesignSystemPreview) {
                            DesignSystemScreen(
                                themeMode = mode,
                                onThemeModeChange = updateTheme,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(
                            route = TripDestinations.WorkspaceRoute,
                            arguments = listOf(navArgument("tripId") {
                                type = NavType.StringType
                            })
                        ) {
                            TripWorkspaceScreen(
                                onBack = { navController.popBackStack() },
                                isPreview = false
                            )
                        }
                    }
                }
            }
        }
    }
}
