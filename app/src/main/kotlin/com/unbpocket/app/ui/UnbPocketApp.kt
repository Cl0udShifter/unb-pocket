package com.unbpocket.app.ui

import android.net.Uri
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.unbpocket.app.ui.auth.AccountQuickSheet
import com.unbpocket.app.ui.auth.LoginScreen
import com.unbpocket.app.ui.components.CapsuleBottomBar
import com.unbpocket.app.ui.components.TopDestination
import com.unbpocket.app.ui.components.UnbNavigationRail
import com.unbpocket.app.ui.discente.BoletimScreen
import com.unbpocket.app.ui.discente.DiscenteScreen
import com.unbpocket.app.ui.discente.DocumentosScreen
import com.unbpocket.app.ui.ru.RuScreen
import com.unbpocket.app.ui.theme.ThemeMode
import com.unbpocket.app.ui.theme.UnbTheme
import com.unbpocket.app.ui.turmas.TurmaVirtualScreen
import com.unbpocket.app.ui.turmas.TurmasScreen
import com.unbpocket.app.ui.viewmodel.AuthViewModel
import com.unbpocket.app.ui.viewmodel.DiscenteViewModel
import com.unbpocket.app.ui.viewmodel.RuViewModel
import com.unbpocket.app.ui.viewmodel.TurmasViewModel
import com.unbpocket.app.ui.viewmodel.UnbViewModelFactory
import com.unbpocket.core.model.auth.AuthState
import com.unbpocket.core.model.contract.UnbPocketCore

private object AppRoutes {
    const val LOGIN = "login"
    const val TURMA_VIRTUAL_PATTERN = "turma_virtual/{frontEndId}"
    const val BOLETIM = "boletim"
    const val DOCUMENTOS = "documentos"

    fun turmaVirtual(frontEndId: String): String =
        "turma_virtual/${Uri.encode(frontEndId)}"
}

@Composable
fun UnbPocketApp(
    core: UnbPocketCore,
    themeMode: ThemeMode,
    onToggleTheme: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val factory = remember(core) { UnbViewModelFactory(core) }
    val authViewModel: AuthViewModel = viewModel(factory = factory)
    val ruViewModel: RuViewModel = viewModel(factory = factory)
    val turmasViewModel: TurmasViewModel = viewModel(factory = factory)
    val discenteViewModel: DiscenteViewModel = viewModel(factory = factory)

    val authUiState by authViewModel.uiState.collectAsStateWithLifecycle()
    val ruUiState by ruViewModel.uiState.collectAsStateWithLifecycle()
    val turmasUiState by turmasViewModel.listUiState.collectAsStateWithLifecycle()
    val turmaDetailUiState by turmasViewModel.detailUiState.collectAsStateWithLifecycle()
    val discenteUiState by discenteViewModel.uiState.collectAsStateWithLifecycle()

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: TopDestination.RU.route

    val currentTopDestination = TopDestination.entries.firstOrNull { it.route == currentRoute }
    val isTopLevelRoute = currentTopDestination != null

    val isExpandedWidth = LocalConfiguration.current.screenWidthDp >= 600
    val bottomContentPadding = if (!isExpandedWidth && isTopLevelRoute) 112.dp else 24.dp

    var showAccountSheet by remember { mutableStateOf(false) }

    val navigateToTopDestination: (TopDestination) -> Unit = { dest ->
        if (currentRoute != dest.route) {
            navController.navigate(dest.route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    val navigateToLogin: () -> Unit = {
        if (currentRoute != AppRoutes.LOGIN) {
            navController.navigate(AppRoutes.LOGIN) {
                launchSingleTop = true
            }
        }
    }

    val navContent: @Composable (Modifier) -> Unit = { contentModifier ->
        UnbNavHost(
            navController = navController,
            modifier = contentModifier,
            bottomContentPadding = bottomContentPadding,
            authUiState = authUiState,
            ruUiState = ruUiState,
            turmasUiState = turmasUiState,
            turmaDetailUiState = turmaDetailUiState,
            discenteUiState = discenteUiState,
            authViewModel = authViewModel,
            ruViewModel = ruViewModel,
            turmasViewModel = turmasViewModel,
            discenteViewModel = discenteViewModel,
            onToggleTheme = onToggleTheme,
            onOpenLogin = navigateToLogin,
            onOpenAccountSheet = { showAccountSheet = true },
        )
    }

    if (isExpandedWidth) {
        Row(
            modifier = modifier
                .fillMaxSize()
                .background(UnbTheme.colors.ground),
        ) {
            if (currentTopDestination != null) {
                UnbNavigationRail(
                    currentDestination = currentTopDestination,
                    onSelect = navigateToTopDestination,
                )
            }
            navContent(Modifier.weight(1f))
        }
    } else {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(UnbTheme.colors.ground),
        ) {
            navContent(Modifier.fillMaxSize())
            if (currentTopDestination != null) {
                CapsuleBottomBar(
                    currentDestination = currentTopDestination,
                    onSelect = navigateToTopDestination,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }

    if (showAccountSheet) {
        AccountQuickSheet(
            authUiState = authUiState,
            themeMode = themeMode,
            onToggleTheme = onToggleTheme,
            onOpenLogin = navigateToLogin,
            onOpenDiscente = { navigateToTopDestination(TopDestination.DISCENTE) },
            onSyncAll = { authViewModel.syncAll() },
            onLogout = { authViewModel.logout(clearCachedData = true) },
            onDismiss = { showAccountSheet = false },
        )
    }
}

@Composable
private fun UnbNavHost(
    navController: NavHostController,
    bottomContentPadding: androidx.compose.ui.unit.Dp,
    authUiState: com.unbpocket.app.ui.viewmodel.AuthUiState,
    ruUiState: com.unbpocket.app.ui.viewmodel.RuUiState,
    turmasUiState: com.unbpocket.app.ui.viewmodel.TurmasUiState,
    turmaDetailUiState: com.unbpocket.app.ui.viewmodel.TurmaVirtualUiState,
    discenteUiState: com.unbpocket.app.ui.viewmodel.DiscenteUiState,
    authViewModel: AuthViewModel,
    ruViewModel: RuViewModel,
    turmasViewModel: TurmasViewModel,
    discenteViewModel: DiscenteViewModel,
    onToggleTheme: () -> Unit,
    onOpenLogin: () -> Unit,
    onOpenAccountSheet: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val topRoutes = remember { TopDestination.entries.map { it.route }.toSet() }

    NavHost(
        navController = navController,
        startDestination = TopDestination.RU.route,
        modifier = modifier,
        enterTransition = {
            if (targetState.destination.route in topRoutes && initialState.destination.route in topRoutes) {
                fadeIn(animationSpec = tween(180))
            } else {
                slideInHorizontally(animationSpec = tween(220)) { it / 6 } + fadeIn(tween(180))
            }
        },
        exitTransition = {
            if (targetState.destination.route in topRoutes && initialState.destination.route in topRoutes) {
                fadeOut(animationSpec = tween(140))
            } else {
                fadeOut(animationSpec = tween(150))
            }
        },
        popEnterTransition = {
            fadeIn(animationSpec = tween(180))
        },
        popExitTransition = {
            slideOutHorizontally(animationSpec = tween(200)) { it / 6 } + fadeOut(tween(160))
        },
    ) {
        composable(TopDestination.RU.route) {
            RuScreen(
                uiState = ruUiState,
                authState = authUiState.authState,
                bottomContentPadding = bottomContentPadding,
                onSelectCampus = ruViewModel::selectCampus,
                onSelectDay = ruViewModel::selectDay,
                onSelectMeal = ruViewModel::selectMeal,
                onToggleAllergen = ruViewModel::toggleAllergen,
                onSetHideRestricted = ruViewModel::setHideRestricted,
                onClearAllergens = ruViewModel::clearAllergens,
                onRefreshMenu = { ruViewModel.refreshMenu(force = true) },
                onRefreshExtras = { ruViewModel.refreshPricesAndNotices(force = true) },
                onSetCampusSheetOpen = ruViewModel::setCampusSheetOpen,
                onSetAllergenSheetOpen = ruViewModel::setAllergenSheetOpen,
                onSetNoticesSheetOpen = ruViewModel::setNoticesAndPricesSheetOpen,
                onOpenAccountSheet = onOpenAccountSheet,
                onOpenLogin = onOpenLogin,
                onDismissMenuError = ruViewModel::dismissMenuError,
                onDismissExtrasError = ruViewModel::dismissExtrasError,
            )
        }

        composable(TopDestination.TURMAS.route) {
            LaunchedEffect(authUiState.authState, turmasUiState.turmas == null) {
                if (authUiState.authState is AuthState.Authenticated &&
                    turmasUiState.turmas == null &&
                    !turmasUiState.isRefreshingList
                ) {
                    turmasViewModel.refreshSemestre()
                }
            }

            TurmasScreen(
                uiState = turmasUiState,
                bottomContentPadding = bottomContentPadding,
                onSelectListTab = turmasViewModel::selectListTab,
                onSelectDay = turmasViewModel::selectDay,
                onToggleExpandClass = turmasViewModel::toggleExpandedClass,
                onOpenTurma = { frontEndId ->
                    navController.navigate(AppRoutes.turmaVirtual(frontEndId))
                },
                onRefresh = { turmasViewModel.refreshSemestre() },
                onOpenLogin = onOpenLogin,
                onOpenAccountSheet = onOpenAccountSheet,
                onDismissError = turmasViewModel::dismissListError,
            )
        }

        composable(TopDestination.DISCENTE.route) {
            LaunchedEffect(authUiState.authState, discenteUiState.discenteResumo == null) {
                if (authUiState.authState is AuthState.Authenticated &&
                    discenteUiState.discenteResumo == null &&
                    !discenteUiState.isRefreshingProfile
                ) {
                    discenteViewModel.refreshProfileAndSaldo()
                }
            }

            DiscenteScreen(
                uiState = discenteUiState,
                authUiState = authUiState,
                bottomContentPadding = bottomContentPadding,
                onToggleTheme = onToggleTheme,
                onToggleQrCardFace = discenteViewModel::toggleQrCardFace,
                onToggleBrightnessBoost = discenteViewModel::toggleBrightnessBoost,
                onRefreshProfile = discenteViewModel::refreshProfileAndSaldo,
                onRefreshCarteirinha = { discenteViewModel.refreshCarteirinha(force = true) },
                onSyncAll = authViewModel::syncAll,
                onOpenBoletim = { navController.navigate(AppRoutes.BOLETIM) },
                onOpenDocumentos = { navController.navigate(AppRoutes.DOCUMENTOS) },
                onOpenLogin = onOpenLogin,
                onLogout = {
                    discenteViewModel.resetQrCardToBalance()
                    authViewModel.logout(clearCachedData = true)
                },
                onDismissProfileError = discenteViewModel::dismissProfileError,
                onDismissCarteirinhaError = discenteViewModel::dismissCarteirinhaError,
                onDismissSyncError = authViewModel::dismissSyncError,
            )
        }

        composable(AppRoutes.LOGIN) {
            LoginScreen(
                uiState = authUiState,
                onMatriculaChange = authViewModel::onMatriculaChange,
                onSenhaChange = authViewModel::onSenhaChange,
                onSubmitLogin = {
                    authViewModel.submitLogin {
                        navController.popBackStack()
                    }
                },
                onBack = { navController.popBackStack() },
                onDismissError = authViewModel::dismissLoginError,
            )
        }

        composable(
            route = AppRoutes.TURMA_VIRTUAL_PATTERN,
            arguments = listOf(navArgument("frontEndId") { type = NavType.StringType }),
        ) { entry ->
            val encoded = entry.arguments?.getString("frontEndId").orEmpty()
            val frontEndId = remember(encoded) { Uri.decode(encoded) }
            LaunchedEffect(frontEndId) {
                if (frontEndId.isNotBlank() && turmaDetailUiState.activeTurmaId != frontEndId) {
                    turmasViewModel.openTurma(frontEndId)
                }
            }

            TurmaVirtualScreen(
                uiState = turmaDetailUiState,
                onBack = {
                    turmasViewModel.closeTurma()
                    navController.popBackStack()
                },
                onSelectTab = turmasViewModel::selectDetailTab,
                onRefreshTab = turmasViewModel::refreshActiveDetailTab,
                onToggleNoticia = turmasViewModel::toggleNoticia,
                onDownloadMaterial = turmasViewModel::downloadMaterial,
                onDownloadAnexo = turmasViewModel::downloadAnexoNoticia,
                onConsumeDownloadedFile = turmasViewModel::consumeDownloadedFile,
                onOpenLogin = onOpenLogin,
                onDismissError = turmasViewModel::dismissDetailError,
            )
        }

        composable(AppRoutes.BOLETIM) {
            LaunchedEffect(authUiState.authState, discenteUiState.boletim == null) {
                if (authUiState.authState is AuthState.Authenticated &&
                    discenteUiState.boletim == null &&
                    !discenteUiState.isRefreshingBoletim
                ) {
                    discenteViewModel.refreshBoletim()
                }
            }

            BoletimScreen(
                uiState = discenteUiState,
                onBack = { navController.popBackStack() },
                onRefresh = discenteViewModel::refreshBoletim,
                onOpenLogin = onOpenLogin,
                onDismissError = discenteViewModel::dismissBoletimError,
            )
        }

        composable(AppRoutes.DOCUMENTOS) {
            DocumentosScreen(
                uiState = discenteUiState,
                onBack = { navController.popBackStack() },
                onEmitirDocumento = discenteViewModel::emitirDocumento,
                onConsumeEmitted = discenteViewModel::consumeEmittedDocumento,
                onOpenLogin = onOpenLogin,
                onDismissError = discenteViewModel::dismissDocumentosError,
            )
        }
    }
}
