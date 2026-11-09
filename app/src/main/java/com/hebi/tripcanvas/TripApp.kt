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
import com.hebi.tripcanvas.data.ChecklistRepository
import com.hebi.tripcanvas.data.ExpenseRepository
import com.hebi.tripcanvas.data.ItineraryRepository
import com.hebi.tripcanvas.data.PlaceRepository
import com.hebi.tripcanvas.data.TripRepository
import com.hebi.tripcanvas.data.local.TripDatabase
import com.hebi.tripcanvas.data.TripUserPreferences
import com.hebi.tripcanvas.data.tripPreferencesRepository
import com.hebi.tripcanvas.designsystem.components.TripErrorState
import com.hebi.tripcanvas.designsystem.components.TripLoadingState
import com.hebi.tripcanvas.designsystem.theme.TripCanvasTheme
import com.hebi.tripcanvas.designsystem.theme.TripDesign
import com.hebi.tripcanvas.designsystem.theme.TripThemeMode
import com.hebi.tripcanvas.designsystem.theme.isTripDarkMode
import com.hebi.tripcanvas.navigation.TripDestinations
import com.hebi.tripcanvas.screens.BackupScreen
import com.hebi.tripcanvas.screens.ChecklistScreen
import com.hebi.tripcanvas.screens.ExpenseFormScreen
import com.hebi.tripcanvas.screens.ExpenseListScreen
import com.hebi.tripcanvas.screens.HomeScreen
import com.hebi.tripcanvas.screens.PlaceFormScreen
import com.hebi.tripcanvas.screens.PlaceListScreen
import com.hebi.tripcanvas.screens.ScheduleFormScreen
import com.hebi.tripcanvas.screens.TripDayScreen
import com.hebi.tripcanvas.screens.TripFormScreen
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
    val tripDatabase = remember(context) { TripDatabase.get(context) }
    val tripRepository = remember(tripDatabase) { TripRepository(tripDatabase.tripDao()) }
    val placeRepository = remember(tripDatabase) {
        PlaceRepository(tripDatabase.placeDao(), tripDatabase.tripDao())
    }
    val itineraryRepository = remember(tripDatabase) {
        ItineraryRepository(
            tripDatabase.itineraryDao(), tripDatabase.tripDao(), tripDatabase.placeDao()
        )
    }
    val expenseRepository = remember(tripDatabase) {
        ExpenseRepository(
            tripDatabase.expenseDao(), tripDatabase.tripDao(), tripDatabase.placeDao()
        )
    }
    val checklistRepository = remember(tripDatabase) {
        ChecklistRepository(tripDatabase.checklistDao(), tripDatabase.tripDao())
    }
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
                                repository = tripRepository,
                                onOpenSettings = { navController.navigate(TripDestinations.Settings) },
                                onCreateTrip = { navController.navigate(TripDestinations.CreateTrip) },
                                onOpenTrip = { id ->
                                    navController.navigate(TripDestinations.workspace(id))
                                },
                                onEditTrip = { id ->
                                    navController.navigate(TripDestinations.editTrip(id))
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
                                },
                                onOpenBackup = {
                                    navController.navigate(TripDestinations.BackupRoute)
                                }
                            )
                        }
                        composable(TripDestinations.CreateTrip) {
                            TripFormScreen(
                                repository = tripRepository,
                                tripId = null,
                                onBack = { navController.popBackStack() },
                                onSaved = { id ->
                                    navController.navigate(TripDestinations.workspace(id)) {
                                        popUpTo(TripDestinations.CreateTrip) { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable(
                            route = TripDestinations.EditTripRoute,
                            arguments = listOf(navArgument("tripId") { type = NavType.StringType })
                        ) { entry ->
                            TripFormScreen(
                                repository = tripRepository,
                                tripId = entry.arguments?.getString("tripId"),
                                onBack = { navController.popBackStack() },
                                onSaved = { navController.popBackStack() }
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
                        ) { entry ->
                            val tripId = entry.arguments?.getString("tripId")
                            TripWorkspaceScreen(
                                onBack = { navController.popBackStack() },
                                isPreview = false,
                                repository = tripRepository,
                                tripId = tripId,
                                onEdit = {
                                    if (tripId != null) {
                                        navController.navigate(TripDestinations.editTrip(tripId))
                                    }
                                },
                                itineraryRepository = itineraryRepository,
                                onSchedule = {
                                    if (tripId != null) {
                                        navController.navigate(TripDestinations.day(tripId))
                                    }
                                },
                                onPlaces = {
                                    if (tripId != null) {
                                        navController.navigate(TripDestinations.places(tripId))
                                    }
                                },
                                checklistRepository = checklistRepository,
                                expenseRepository = expenseRepository,
                                onChecklist = {
                                    if (tripId != null) {
                                        navController.navigate(TripDestinations.checklist(tripId))
                                    }
                                },
                                onExpenses = {
                                    if (tripId != null) {
                                        navController.navigate(TripDestinations.expenses(tripId))
                                    }
                                }
                            )
                        }
                        composable(TripDestinations.BackupRoute) {
                            BackupScreen(
                                database = tripDatabase,
                                tripRepository = tripRepository,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(
                            route = TripDestinations.ChecklistRoute,
                            arguments = listOf(navArgument("tripId") { type = NavType.StringType })
                        ) { entry ->
                            val tripId = requireNotNull(entry.arguments?.getString("tripId"))
                            ChecklistScreen(
                                tripId = tripId,
                                repository = checklistRepository,
                                tripRepository = tripRepository,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(
                            route = TripDestinations.ExpensesRoute,
                            arguments = listOf(navArgument("tripId") { type = NavType.StringType })
                        ) { entry ->
                            val tripId = requireNotNull(entry.arguments?.getString("tripId"))
                            ExpenseListScreen(
                                tripId = tripId,
                                trips = tripRepository,
                                repository = expenseRepository,
                                places = placeRepository,
                                onBack = { navController.popBackStack() },
                                onNew = {
                                    navController.navigate(TripDestinations.newExpense(tripId))
                                },
                                onEdit = { expenseId ->
                                    navController.navigate(TripDestinations.editExpense(tripId, expenseId))
                                }
                            )
                        }
                        composable(
                            route = TripDestinations.NewExpenseRoute,
                            arguments = listOf(navArgument("tripId") { type = NavType.StringType })
                        ) { entry ->
                            val tripId = requireNotNull(entry.arguments?.getString("tripId"))
                            ExpenseFormScreen(
                                tripId = tripId,
                                itemId = null,
                                trips = tripRepository,
                                repository = expenseRepository,
                                places = placeRepository,
                                onBack = { navController.popBackStack() },
                                onSaved = { navController.popBackStack() }
                            )
                        }
                        composable(
                            route = TripDestinations.EditExpenseRoute,
                            arguments = listOf(
                                navArgument("tripId") { type = NavType.StringType },
                                navArgument("expenseId") { type = NavType.StringType }
                            )
                        ) { entry ->
                            val tripId = requireNotNull(entry.arguments?.getString("tripId"))
                            val expenseId = requireNotNull(entry.arguments?.getString("expenseId"))
                            ExpenseFormScreen(
                                tripId = tripId,
                                itemId = expenseId,
                                trips = tripRepository,
                                repository = expenseRepository,
                                places = placeRepository,
                                onBack = { navController.popBackStack() },
                                onSaved = { navController.popBackStack() }
                            )
                        }
                        composable(
                            route = TripDestinations.DayRoute,
                            arguments = listOf(navArgument("tripId") { type = NavType.StringType })
                        ) { entry ->
                            val tripId = requireNotNull(entry.arguments?.getString("tripId"))
                            TripDayScreen(
                                tripRepository = tripRepository,
                                itineraryRepository = itineraryRepository,
                                placeRepository = placeRepository,
                                tripId = tripId,
                                onBack = { navController.popBackStack() },
                                onAdd = { date ->
                                    navController.navigate(TripDestinations.newSchedule(tripId, date))
                                },
                                onEdit = { id ->
                                    navController.navigate(TripDestinations.editSchedule(tripId, id))
                                },
                                onPlaces = { navController.navigate(TripDestinations.places(tripId)) }
                            )
                        }
                        composable(
                            route = TripDestinations.NewScheduleRoute,
                            arguments = listOf(
                                navArgument("tripId") { type = NavType.StringType },
                                navArgument("date") { type = NavType.StringType }
                            )
                        ) { entry ->
                            val tripId = requireNotNull(entry.arguments?.getString("tripId"))
                            val date = requireNotNull(entry.arguments?.getString("date"))
                            val selectedPlace by entry.savedStateHandle
                                .getStateFlow("selectedPlaceId", "").collectAsState()
                            ScheduleFormScreen(
                                tripRepository = tripRepository,
                                itineraryRepository = itineraryRepository,
                                placeRepository = placeRepository,
                                tripId = tripId,
                                itemId = null,
                                initialDate = date,
                                selectedPlaceId = selectedPlace,
                                onBack = { navController.popBackStack() },
                                onSaved = { navController.popBackStack() },
                                onPickPlace = {
                                    navController.navigate(TripDestinations.pickPlace(tripId))
                                }
                            )
                        }
                        composable(
                            route = TripDestinations.EditScheduleRoute,
                            arguments = listOf(
                                navArgument("tripId") { type = NavType.StringType },
                                navArgument("itemId") { type = NavType.StringType }
                            )
                        ) { entry ->
                            val tripId = requireNotNull(entry.arguments?.getString("tripId"))
                            val itemId = requireNotNull(entry.arguments?.getString("itemId"))
                            val selectedPlace by entry.savedStateHandle
                                .getStateFlow("selectedPlaceId", "").collectAsState()
                            ScheduleFormScreen(
                                tripRepository = tripRepository,
                                itineraryRepository = itineraryRepository,
                                placeRepository = placeRepository,
                                tripId = tripId,
                                itemId = itemId,
                                initialDate = "",
                                selectedPlaceId = selectedPlace,
                                onBack = { navController.popBackStack() },
                                onSaved = { navController.popBackStack() },
                                onPickPlace = {
                                    navController.navigate(TripDestinations.pickPlace(tripId))
                                }
                            )
                        }
                        composable(
                            route = TripDestinations.PlacesRoute,
                            arguments = listOf(navArgument("tripId") { type = NavType.StringType })
                        ) { entry ->
                            val tripId = requireNotNull(entry.arguments?.getString("tripId"))
                            PlaceListScreen(
                                repository = placeRepository,
                                tripId = tripId,
                                selectionMode = false,
                                onSelect = {},
                                onCreate = { navController.navigate(TripDestinations.newPlace(tripId)) },
                                onEdit = { id ->
                                    navController.navigate(TripDestinations.editPlace(tripId, id))
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(
                            route = TripDestinations.PickPlaceRoute,
                            arguments = listOf(navArgument("tripId") { type = NavType.StringType })
                        ) { entry ->
                            val tripId = requireNotNull(entry.arguments?.getString("tripId"))
                            val newId by entry.savedStateHandle
                                .getStateFlow("newPlaceId", "").collectAsState()
                            val selectPlace: (String) -> Unit = { placeId ->
                                navController.previousBackStackEntry?.savedStateHandle
                                    ?.set("selectedPlaceId", placeId)
                                navController.popBackStack()
                            }
                            PlaceListScreen(
                                repository = placeRepository,
                                tripId = tripId,
                                selectionMode = true,
                                onSelect = selectPlace,
                                onCreate = { navController.navigate(TripDestinations.newPlace(tripId)) },
                                onEdit = { id ->
                                    navController.navigate(TripDestinations.editPlace(tripId, id))
                                },
                                onBack = { navController.popBackStack() },
                                newlyCreatedPlaceId = newId
                            )
                        }
                        composable(
                            route = TripDestinations.NewPlaceRoute,
                            arguments = listOf(navArgument("tripId") { type = NavType.StringType })
                        ) { entry ->
                            val tripId = requireNotNull(entry.arguments?.getString("tripId"))
                            PlaceFormScreen(
                                repository = placeRepository,
                                tripId = tripId,
                                placeId = null,
                                onBack = { navController.popBackStack() },
                                onSaved = { id ->
                                    navController.previousBackStackEntry?.savedStateHandle
                                        ?.set("newPlaceId", id)
                                    navController.popBackStack()
                                }
                            )
                        }
                        composable(
                            route = TripDestinations.EditPlaceRoute,
                            arguments = listOf(
                                navArgument("tripId") { type = NavType.StringType },
                                navArgument("placeId") { type = NavType.StringType }
                            )
                        ) { entry ->
                            val tripId = requireNotNull(entry.arguments?.getString("tripId"))
                            val placeId = requireNotNull(entry.arguments?.getString("placeId"))
                            PlaceFormScreen(
                                repository = placeRepository,
                                tripId = tripId,
                                placeId = placeId,
                                onBack = { navController.popBackStack() },
                                onSaved = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
