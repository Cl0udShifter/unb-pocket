package com.unbpocket.app.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.view.WindowManager
import androidx.core.content.ContextCompat
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
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
import com.unbpocket.app.ui.discente.RuFullScreenQrPass
import com.unbpocket.app.ui.notifications.NotificationCenterBottomSheet
import com.unbpocket.app.ui.notifications.NotificationCenterSection
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
import com.unbpocket.core.model.common.PreloadPhase
import com.unbpocket.core.model.contract.UnbPocketCore
import com.unbpocket.core.model.notification.SigaaDeepLinkTarget
import com.unbpocket.core.model.notification.SigaaNotificacaoTarget
import com.unbpocket.core.model.ru.RuDeepLinkTarget

private object AppRoutes {
    const val HOME = "home"
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
    onSelectThemeMode: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
    pendingRuDeepLink: RuDeepLinkTarget? = null,
    onConsumeRuDeepLink: () -> Unit = {},
    pendingSigaaDeepLink: SigaaDeepLinkTarget? = null,
    onConsumeSigaaDeepLink: () -> Unit = {},
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
    val currentRoute = backStackEntry?.destination?.route ?: AppRoutes.HOME

    var activeTab by rememberSaveable { mutableStateOf(TopDestination.RU) }
    val visitedTabs = remember {
        mutableStateMapOf(
            TopDestination.RU to true,
            activeTab to true,
        )
    }

    val isTopLevelRoute = currentRoute == AppRoutes.HOME
    val currentTopDestination = if (isTopLevelRoute) activeTab else null

    val isExpandedWidth = LocalConfiguration.current.screenWidthDp >= 600
    val bottomContentPadding = if (!isExpandedWidth && isTopLevelRoute) 112.dp else 24.dp

    var showAccountSheet by remember { mutableStateOf(false) }
    var showNotificationCenter by remember { mutableStateOf(false) }
    var notificationInitialSection by remember { mutableStateOf<NotificationCenterSection?>(null) }
    val appContext = LocalContext.current

    val openNotificationCenter: (NotificationCenterSection?) -> Unit = { section ->
        notificationInitialSection = section
        showNotificationCenter = true
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        ruViewModel.refreshIfStaleSilently()
    }

    LaunchedEffect(authUiState.authState) {
        if (authUiState.authState is AuthState.Authenticated) {
            val hasPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    appContext,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
            if (hasPermission) {
                authViewModel.autoEnableSigaaNotificationsIfPermitted()
            }
        }
    }

    val navigateToTopDestination: (TopDestination) -> Unit = { dest ->
        visitedTabs[dest] = true
        activeTab = dest
        if (currentRoute != AppRoutes.HOME) {
            navController.popBackStack(AppRoutes.HOME, inclusive = false)
        }
    }

    LaunchedEffect(pendingRuDeepLink) {
        val target = pendingRuDeepLink ?: return@LaunchedEffect
        showAccountSheet = false
        showNotificationCenter = false
        discenteViewModel.resetQrCardToBalance()
        ruViewModel.applyDeepLink(target)
        navigateToTopDestination(TopDestination.RU)
        onConsumeRuDeepLink()
    }

    LaunchedEffect(pendingSigaaDeepLink) {
        val target = pendingSigaaDeepLink ?: return@LaunchedEffect
        showAccountSheet = false
        showNotificationCenter = false
        discenteViewModel.resetQrCardToBalance()
        val destination = when (target.target) {
            SigaaNotificacaoTarget.TURMAS -> TopDestination.TURMAS
            SigaaNotificacaoTarget.DISCENTE -> TopDestination.DISCENTE
        }
        navigateToTopDestination(destination)
        onConsumeSigaaDeepLink()
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
            activeTab = activeTab,
            visitedTabs = visitedTabs,
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
            onOpenLogin = navigateToLogin,
            onOpenAccountSheet = { showAccountSheet = true },
            onOpenNotificationCenter = openNotificationCenter,
        )
    }

    val isQrPassOpen = discenteUiState.isFlippedToQr
    val qrFlipRotation by animateFloatAsState(
        targetValue = if (isQrPassOpen) 180f else 0f,
        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
        label = "fullScreenQrFlip",
    )
    val showingQrPassFace = qrFlipRotation > 90f
    val density = LocalDensity.current.density
    val view = LocalView.current

    BackHandler(enabled = isQrPassOpen) {
        discenteViewModel.resetQrCardToBalance()
    }

    BackHandler(enabled = !isQrPassOpen && isTopLevelRoute && activeTab != TopDestination.RU) {
        activeTab = TopDestination.RU
    }

    DisposableEffect(isQrPassOpen) {
        val window = (view.context as? Activity)?.window
        val originalBrightness = window?.attributes?.screenBrightness
        if (window != null && isQrPassOpen) {
            val lp = window.attributes
            lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
            window.attributes = lp
        }
        onDispose {
            if (window != null && originalBrightness != null) {
                val lp = window.attributes
                lp.screenBrightness = originalBrightness
                window.attributes = lp
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Keep the main app composed while the QR pass is shown so flipping back never
        // triggers a heavy mid-animation NavHost/LazyColumn recomposition at 90 degrees.
        val appFlipModifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = if (showingQrPassFace) 0f else 1f
                rotationY = qrFlipRotation
                cameraDistance = 14f * density
            }

        if (isExpandedWidth) {
            Row(
                modifier = appFlipModifier.background(UnbTheme.colors.ground),
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
                modifier = appFlipModifier.background(UnbTheme.colors.ground),
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

        if (showingQrPassFace) {
            val isPreloadingCarteirinha = authUiState.preloadProgress.phase == PreloadPhase.RUNNING_BACKGROUND &&
                authUiState.preloadProgress.currentTarget == "Carteirinha Estudantil"
            val isLoadingQr = discenteUiState.isRefreshingCarteirinha ||
                (discenteUiState.carteirinha?.value == null &&
                    (isPreloadingCarteirinha || !discenteUiState.hasResolvedCarteirinhaCache))

            RuFullScreenQrPass(
                carteirinha = discenteUiState.carteirinha?.value,
                isLoading = isLoadingQr,
                hasError = discenteUiState.carteirinhaError != null,
                onDismiss = { discenteViewModel.resetQrCardToBalance() },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        rotationY = qrFlipRotation - 180f
                        cameraDistance = 14f * density
                    },
            )
        }
    }

    if (showAccountSheet) {
        AccountQuickSheet(
            authUiState = authUiState,
            themeMode = themeMode,
            onSelectThemeMode = onSelectThemeMode,
            onOpenLogin = navigateToLogin,
            onSyncAll = { authViewModel.syncAll() },
            onLogout = {
                discenteViewModel.resetQrCardToBalance()
                authViewModel.logout(clearCachedData = true)
            },
            onDismiss = { showAccountSheet = false },
        )
    }

    if (showNotificationCenter) {
        NotificationCenterBottomSheet(
            initialSection = notificationInitialSection,
            ruNotificacoes = ruUiState.preferencias.notificacoes,
            sigaaNotificacoes = authUiState.sigaaNotificacoes,
            turmas = authUiState.turmasParaNotificacao,
            onSetRuHabilitadas = ruViewModel::setNotificacoesHabilitadas,
            onToggleRuSlot = ruViewModel::toggleNotificacaoSlot,
            onToggleRuDay = ruViewModel::toggleNotificacaoDia,
            onToggleSigaaTurmasSecao = authViewModel::toggleSigaaTurmasSecao,
            onSetAulasLembrete = authViewModel::setAulasLembrete,
            onSetAntecedenciaAulaMinutos = authViewModel::setAntecedenciaAulaMinutos,
            onSetAvisosEMateriaisNovos = authViewModel::setAvisosEMateriaisNovos,
            onSetProvasETarefasLembrete = authViewModel::setProvasETarefasLembrete,
            onSetNotasParciaisAlteradas = authViewModel::setNotasParciaisAlteradas,
            onSetRiscoFaltaAlerta = authViewModel::setRiscoFaltaAlerta,
            onToggleTurmaSilenciada = authViewModel::toggleTurmaSilenciada,
            onToggleSigaaDiscenteSecao = authViewModel::toggleSigaaDiscenteSecao,
            onSetSaldoRuBaixoAlerta = authViewModel::setSaldoRuBaixoAlerta,
            onSetLimiteRefeicoesRuBaixo = authViewModel::setLimiteRefeicoesRuBaixo,
            onSetExtratoRuMovimentacaoAlerta = authViewModel::setExtratoRuMovimentacaoAlerta,
            onSetBoletimAlteradoAlerta = authViewModel::setBoletimAlteradoAlerta,
            onSetIndicesAlteradosAlerta = authViewModel::setIndicesAlteradosAlerta,
            onSetSyncInterval = authViewModel::setSigaaSyncInterval,
            onSetSomenteWifi = authViewModel::setSigaaSomenteWifi,
            onResyncSchedules = {
                if (ruUiState.preferencias.notificacoes.habilitado) {
                    ruViewModel.setNotificacoesHabilitadas(true)
                }
                authViewModel.resyncSigaaNotificacoesSchedule()
            },
            onDismiss = { showNotificationCenter = false },
        )
    }
}

@Composable
private fun UnbNavHost(
    navController: NavHostController,
    activeTab: TopDestination,
    visitedTabs: Map<TopDestination, Boolean>,
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
    onOpenLogin: () -> Unit,
    onOpenAccountSheet: () -> Unit,
    onOpenNotificationCenter: (NotificationCenterSection?) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = AppRoutes.HOME,
        modifier = modifier,
        enterTransition = {
            slideInHorizontally(animationSpec = tween(220)) { it / 6 } + fadeIn(tween(180))
        },
        exitTransition = {
            fadeOut(animationSpec = tween(150))
        },
        popEnterTransition = {
            fadeIn(animationSpec = tween(180))
        },
        popExitTransition = {
            slideOutHorizontally(animationSpec = tween(200)) { it / 6 } + fadeOut(tween(160))
        },
    ) {
        composable(AppRoutes.HOME) {
            Box(modifier = Modifier.fillMaxSize()) {
                val ruSelected = activeTab == TopDestination.RU
                val ruAlpha by animateFloatAsState(
                    targetValue = if (ruSelected) 1f else 0f,
                    animationSpec = tween(durationMillis = 120),
                    label = "tabFadeRu",
                )
                if (visitedTabs[TopDestination.RU] == true || ruSelected) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .zIndex(if (ruSelected) 1f else 0f)
                            .graphicsLayer { alpha = ruAlpha },
                    ) {
                        LaunchedEffect(ruSelected) {
                            if (ruSelected) {
                                ruViewModel.refreshIfStaleSilently()
                            }
                        }
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
                            onRefreshMenu = { ruViewModel.refreshMenu(force = false) },
                            onRefreshExtras = { ruViewModel.refreshPricesAndNotices(force = true) },
                            onSetCampusSheetOpen = ruViewModel::setCampusSheetOpen,
                            onSetAllergenSheetOpen = ruViewModel::setAllergenSheetOpen,
                            onSetNoticesSheetOpen = ruViewModel::setNoticesAndPricesSheetOpen,
                            onOpenNotifications = { onOpenNotificationCenter(NotificationCenterSection.RU) },
                            onOpenAccountSheet = onOpenAccountSheet,
                            onOpenLogin = onOpenLogin,
                            onDismissMenuError = ruViewModel::dismissMenuError,
                            onDismissExtrasError = ruViewModel::dismissExtrasError,
                        )
                    }
                }

                val turmasSelected = activeTab == TopDestination.TURMAS
                val turmasAlpha by animateFloatAsState(
                    targetValue = if (turmasSelected) 1f else 0f,
                    animationSpec = tween(durationMillis = 120),
                    label = "tabFadeTurmas",
                )
                if (visitedTabs[TopDestination.TURMAS] == true || turmasSelected) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .zIndex(if (turmasSelected) 1f else 0f)
                            .graphicsLayer { alpha = turmasAlpha },
                    ) {
                        LaunchedEffect(
                            turmasSelected,
                            authUiState.authState,
                            turmasUiState.turmas == null,
                            authUiState.isSubmittingLogin,
                            authUiState.isSyncingAll,
                            authUiState.preloadProgress.phase,
                        ) {
                            if (turmasSelected &&
                                authUiState.authState is AuthState.Authenticated &&
                                turmasUiState.turmas == null &&
                                !turmasUiState.isRefreshingList &&
                                !authUiState.isSubmittingLogin &&
                                !authUiState.isSyncingAll &&
                                authUiState.preloadProgress.phase != PreloadPhase.RUNNING_ESSENTIAL
                            ) {
                                turmasViewModel.refreshSemestre()
                            }
                        }

                        TurmasScreen(
                            uiState = turmasUiState,
                            authUiState = authUiState,
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
                            onOpenNotifications = { onOpenNotificationCenter(NotificationCenterSection.TURMAS) },
                            onDismissError = turmasViewModel::dismissListError,
                        )
                    }
                }

                val discenteSelected = activeTab == TopDestination.DISCENTE
                val discenteAlpha by animateFloatAsState(
                    targetValue = if (discenteSelected) 1f else 0f,
                    animationSpec = tween(durationMillis = 120),
                    label = "tabFadeDiscente",
                )
                if (visitedTabs[TopDestination.DISCENTE] == true || discenteSelected) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .zIndex(if (discenteSelected) 1f else 0f)
                            .graphicsLayer { alpha = discenteAlpha },
                    ) {
                        LaunchedEffect(
                            discenteSelected,
                            authUiState.authState,
                            discenteUiState.discenteResumo == null,
                            authUiState.isSubmittingLogin,
                            authUiState.isSyncingAll,
                            authUiState.preloadProgress.phase,
                        ) {
                            if (discenteSelected &&
                                authUiState.authState is AuthState.Authenticated &&
                                discenteUiState.discenteResumo == null &&
                                !discenteUiState.isRefreshingProfile &&
                                !authUiState.isSubmittingLogin &&
                                !authUiState.isSyncingAll &&
                                authUiState.preloadProgress.phase != PreloadPhase.RUNNING_ESSENTIAL
                            ) {
                                discenteViewModel.refreshProfileAndSaldo()
                            }
                        }

                        DiscenteScreen(
                            uiState = discenteUiState,
                            authUiState = authUiState,
                            bottomContentPadding = bottomContentPadding,
                            onToggleQrCardFace = discenteViewModel::toggleQrCardFace,
                            onRefreshProfile = discenteViewModel::refreshProfileAndSaldo,
                            onSyncAll = authViewModel::syncAll,
                            onOpenBoletim = { navController.navigate(AppRoutes.BOLETIM) },
                            onOpenDocumentos = { navController.navigate(AppRoutes.DOCUMENTOS) },
                            onOpenLogin = onOpenLogin,
                            onOpenAccountSheet = onOpenAccountSheet,
                            onOpenNotifications = { onOpenNotificationCenter(NotificationCenterSection.DISCENTE) },
                            onLogout = {
                                discenteViewModel.resetQrCardToBalance()
                                authViewModel.logout(clearCachedData = true)
                            },
                            onDismissProfileError = discenteViewModel::dismissProfileError,
                            onDismissSyncError = authViewModel::dismissSyncError,
                        )
                    }
                }
            }
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
            LaunchedEffect(
                authUiState.authState,
                discenteUiState.boletim == null,
                authUiState.isSubmittingLogin,
                authUiState.isSyncingAll,
                authUiState.preloadProgress.phase,
            ) {
                if (authUiState.authState is AuthState.Authenticated &&
                    discenteUiState.boletim == null &&
                    !discenteUiState.isRefreshingBoletim &&
                    !authUiState.isSubmittingLogin &&
                    !authUiState.isSyncingAll &&
                    authUiState.preloadProgress.phase != PreloadPhase.RUNNING_ESSENTIAL
                ) {
                    discenteViewModel.refreshBoletim()
                }
            }

            BoletimScreen(
                uiState = discenteUiState,
                authUiState = authUiState,
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
