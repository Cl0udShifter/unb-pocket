import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { isNotificationPermitted } from '../core/data/notification/pwaNotificationCoordinator';
import type {
  RuDeepLinkTarget,
  SigaaDeepLinkTarget,
  UnbPocketCore,
} from '../core/model/models';
import { AccountQuickSheet, LoginScreen } from './auth/LoginScreen';
import {
  CapsuleBottomBar,
  PortalZoomSplashOverlay,
  UnbNavigationRail,
  type TopDestination,
} from './components/CommonComponents';
import {
  InAppDocumentViewerModal,
  type ViewableDocumentRequest,
} from './components/InAppDocumentViewerModal';
import { BoletimScreen } from './discente/BoletimScreen';
import { DiscenteScreen, RuFullScreenQrPass } from './discente/DiscenteScreen';
import { DocumentosScreen } from './discente/DocumentosScreen';
import {
  NotificationCenterSheet,
  type NotificationCenterSection,
} from './notifications/NotificationCenterSheet';
import { RuScreen } from './ru/RuScreen';
import type { ThemeManager } from './theme/themeManager';
import { TurmasScreen } from './turmas/TurmasScreen';
import { TurmaVirtualScreen } from './turmas/TurmaVirtualScreen';
import { createAppStores, useObservable } from './viewmodel/appStores';

type AppRoute =
  | { readonly kind: 'home' }
  | { readonly kind: 'login' }
  | { readonly kind: 'turma_virtual'; readonly frontEndId: string }
  | { readonly kind: 'boletim' }
  | { readonly kind: 'documentos' };

function parseHashRoute(hash: string): AppRoute {
  const clean = hash.replace(/^#\/?/, '');
  if (clean === 'login') return { kind: 'login' };
  if (clean === 'boletim') return { kind: 'boletim' };
  if (clean === 'documentos') return { kind: 'documentos' };
  if (clean.startsWith('turma_virtual/')) {
    const id = decodeURIComponent(clean.slice('turma_virtual/'.length));
    if (id) return { kind: 'turma_virtual', frontEndId: id };
  }
  return { kind: 'home' };
}

function routeToHash(route: AppRoute): string {
  switch (route.kind) {
    case 'home':
      return '#/';
    case 'login':
      return '#/login';
    case 'boletim':
      return '#/boletim';
    case 'documentos':
      return '#/documentos';
    case 'turma_virtual':
      return `#/turma_virtual/${encodeURIComponent(route.frontEndId)}`;
  }
}

interface UnbPocketAppProps {
  readonly core: UnbPocketCore;
  readonly themeManager: ThemeManager;
  readonly pendingRuDeepLink?: RuDeepLinkTarget | null;
  readonly onConsumeRuDeepLink?: () => void;
  readonly pendingSigaaDeepLink?: SigaaDeepLinkTarget | null;
  readonly onConsumeSigaaDeepLink?: () => void;
}

export const UnbPocketApp: React.FC<UnbPocketAppProps> = ({
  core,
  themeManager,
  pendingRuDeepLink = null,
  onConsumeRuDeepLink,
  pendingSigaaDeepLink = null,
  onConsumeSigaaDeepLink,
}) => {
  const stores = useMemo(() => createAppStores(core), [core]);
  useEffect(() => () => stores.dispose(), [stores]);

  const authUiState = useObservable(stores.authStore.uiState);
  const ruUiState = useObservable(stores.ruStore.uiState);
  const turmasUiState = useObservable(stores.turmasStore.listUiState);
  const turmaDetailUiState = useObservable(stores.turmasStore.detailUiState);
  const discenteUiState = useObservable(stores.discenteStore.uiState);
  const themeMode = useObservable(themeManager.mode);
  const systemPrefersDark = useObservable(themeManager.systemPrefersDark);

  const [route, setRoute] = useState<AppRoute>(() =>
    typeof window !== 'undefined' ? parseHashRoute(window.location.hash) : { kind: 'home' },
  );
  const [activeTab, setActiveTab] = useState<TopDestination>('RU');
  const [isExpandedWidth, setIsExpandedWidth] = useState<boolean>(() =>
    typeof window !== 'undefined' ? window.innerWidth >= 600 : false,
  );
  const [showSplash, setShowSplash] = useState<boolean>(() => {
    try {
      if (typeof sessionStorage === 'undefined') return false;
      if (sessionStorage.getItem('unb_pocket_splash_shown') === '1') return false;
      sessionStorage.setItem('unb_pocket_splash_shown', '1');
      return true;
    } catch {
      return false;
    }
  });

  const [showAccountSheet, setShowAccountSheet] = useState(false);
  const [showNotificationCenter, setShowNotificationCenter] = useState(false);
  const [notificationInitialSection, setNotificationInitialSection] =
    useState<NotificationCenterSection | null>(null);
  const [activeDocument, setActiveDocument] = useState<ViewableDocumentRequest | null>(null);

  const pushRoute = useCallback((next: AppRoute) => {
    const nextHash = routeToHash(next);
    if (typeof window !== 'undefined' && window.location.hash !== nextHash) {
      window.history.pushState(null, '', nextHash);
    }
    setRoute(next);
  }, []);

  const popToHome = useCallback(() => {
    if (typeof window !== 'undefined' && window.location.hash && window.location.hash !== '#/') {
      window.history.replaceState(null, '', '#/');
    }
    setRoute({ kind: 'home' });
  }, []);

  useEffect(() => {
    if (typeof window === 'undefined') return;
    const onHashOrPop = (): void => {
      const next = parseHashRoute(window.location.hash);
      setRoute(next);
      if (next.kind !== 'turma_virtual') {
        stores.turmasStore.closeTurma();
      }
    };
    window.addEventListener('popstate', onHashOrPop);
    window.addEventListener('hashchange', onHashOrPop);
    return () => {
      window.removeEventListener('popstate', onHashOrPop);
      window.removeEventListener('hashchange', onHashOrPop);
    };
  }, [stores.turmasStore]);

  useEffect(() => {
    if (typeof window === 'undefined') return;
    const onResize = (): void => setIsExpandedWidth(window.innerWidth >= 600);
    window.addEventListener('resize', onResize);
    return () => window.removeEventListener('resize', onResize);
  }, []);

  useEffect(() => {
    if (typeof document === 'undefined') return;
    const onVisibility = (): void => {
      if (document.visibilityState === 'visible') {
        stores.ruStore.syncAutomaticSelectionOnResume();
        stores.turmasStore.syncAutomaticDayOnResume();
        stores.ruStore.refreshIfStaleSilently();
      }
    };
    document.addEventListener('visibilitychange', onVisibility);
    return () => document.removeEventListener('visibilitychange', onVisibility);
  }, [stores.ruStore, stores.turmasStore]);

  useEffect(() => {
    if (authUiState.authState.kind === 'Authenticated' && isNotificationPermitted()) {
      stores.authStore.autoEnableSigaaNotificationsIfPermitted();
    }
  }, [authUiState.authState, stores.authStore]);

  // Open Turma Virtual when navigated via hash
  useEffect(() => {
    if (
      route.kind === 'turma_virtual' &&
      turmaDetailUiState.activeTurmaId !== route.frontEndId
    ) {
      void stores.turmasStore.openTurma(route.frontEndId);
    }
  }, [route, turmaDetailUiState.activeTurmaId, stores.turmasStore]);

  // Auto-load Turmas / Discente / Boletim when authenticated and cache is empty
  useEffect(() => {
    if (
      route.kind === 'home' &&
      activeTab === 'TURMAS' &&
      authUiState.authState.kind === 'Authenticated' &&
      turmasUiState.turmas === null &&
      !turmasUiState.isRefreshingList &&
      !authUiState.isSubmittingLogin &&
      !authUiState.isSyncingAll &&
      authUiState.preloadProgress.phase !== 'RUNNING_ESSENTIAL'
    ) {
      void stores.turmasStore.refreshSemestre();
    }
  }, [
    route.kind,
    activeTab,
    authUiState.authState,
    turmasUiState.turmas,
    turmasUiState.isRefreshingList,
    authUiState.isSubmittingLogin,
    authUiState.isSyncingAll,
    authUiState.preloadProgress.phase,
    stores.turmasStore,
  ]);

  useEffect(() => {
    if (
      route.kind === 'home' &&
      activeTab === 'DISCENTE' &&
      authUiState.authState.kind === 'Authenticated' &&
      discenteUiState.discenteResumo === null &&
      !discenteUiState.isRefreshingProfile &&
      !authUiState.isSubmittingLogin &&
      !authUiState.isSyncingAll &&
      authUiState.preloadProgress.phase !== 'RUNNING_ESSENTIAL'
    ) {
      void stores.discenteStore.refreshProfileAndSaldo();
    }
  }, [
    route.kind,
    activeTab,
    authUiState.authState,
    discenteUiState.discenteResumo,
    discenteUiState.isRefreshingProfile,
    authUiState.isSubmittingLogin,
    authUiState.isSyncingAll,
    authUiState.preloadProgress.phase,
    stores.discenteStore,
  ]);

  useEffect(() => {
    if (
      route.kind === 'boletim' &&
      authUiState.authState.kind === 'Authenticated' &&
      discenteUiState.boletim === null &&
      !discenteUiState.isRefreshingBoletim &&
      !authUiState.isSubmittingLogin &&
      !authUiState.isSyncingAll &&
      authUiState.preloadProgress.phase !== 'RUNNING_ESSENTIAL'
    ) {
      void stores.discenteStore.refreshBoletim();
    }
  }, [
    route.kind,
    authUiState.authState,
    discenteUiState.boletim,
    discenteUiState.isRefreshingBoletim,
    authUiState.isSubmittingLogin,
    authUiState.isSyncingAll,
    authUiState.preloadProgress.phase,
    stores.discenteStore,
  ]);

  // Deep link handling
  useEffect(() => {
    if (!pendingRuDeepLink) return;
    setShowAccountSheet(false);
    setShowNotificationCenter(false);
    stores.discenteStore.resetQrCardToBalance();
    stores.ruStore.applyDeepLink(pendingRuDeepLink);
    setActiveTab('RU');
    popToHome();
    onConsumeRuDeepLink?.();
  }, [pendingRuDeepLink, onConsumeRuDeepLink, popToHome, stores.discenteStore, stores.ruStore]);

  useEffect(() => {
    if (!pendingSigaaDeepLink) return;
    setShowAccountSheet(false);
    setShowNotificationCenter(false);
    stores.discenteStore.resetQrCardToBalance();
    setActiveTab(pendingSigaaDeepLink.target === 'TURMAS' ? 'TURMAS' : 'DISCENTE');
    popToHome();
    onConsumeSigaaDeepLink?.();
  }, [pendingSigaaDeepLink, onConsumeSigaaDeepLink, popToHome, stores.discenteStore]);

  const navigateToTopDestination = useCallback(
    (dest: TopDestination) => {
      setActiveTab(dest);
      if (dest === 'RU') {
        stores.ruStore.refreshIfStaleSilently();
      }
      if (route.kind !== 'home') {
        popToHome();
      }
    },
    [route.kind, popToHome, stores.ruStore],
  );

  const openNotificationCenter = useCallback((section: NotificationCenterSection | null) => {
    setNotificationInitialSection(section);
    setShowNotificationCenter(true);
  }, []);

  const navigateToLogin = useCallback(() => {
    pushRoute({ kind: 'login' });
  }, [pushRoute]);

  const isTopLevelRoute = route.kind === 'home';
  const isQrPassOpen = discenteUiState.isFlippedToQr;
  const [isFlipAnimating, setIsFlipAnimating] = useState(false);
  const prevQrOpenRef = React.useRef(isQrPassOpen);

  useEffect(() => {
    if (prevQrOpenRef.current === isQrPassOpen) return;
    prevQrOpenRef.current = isQrPassOpen;
    setIsFlipAnimating(true);
    const timer = setTimeout(() => {
      setIsFlipAnimating(false);
    }, 450);
    return () => clearTimeout(timer);
  }, [isQrPassOpen]);

  const isPreloadingCarteirinha =
    authUiState.preloadProgress.phase === 'RUNNING_BACKGROUND' &&
    authUiState.preloadProgress.currentTarget === 'Carteirinha Estudantil';
  const isLoadingQr =
    discenteUiState.isRefreshingCarteirinha ||
    (discenteUiState.carteirinha?.value === undefined &&
      (isPreloadingCarteirinha || !discenteUiState.hasResolvedCarteirinhaCache));

  const renderMainRoute = (): React.ReactNode => {
    switch (route.kind) {
      case 'login':
        return (
          <LoginScreen
            uiState={authUiState}
            onMatriculaChange={stores.authStore.onMatriculaChange}
            onSenhaChange={stores.authStore.onSenhaChange}
            onSubmitLogin={() => {
              void stores.authStore.submitLogin(() => {
                popToHome();
              });
            }}
            onBack={popToHome}
            onDismissError={stores.authStore.dismissLoginError}
          />
        );

      case 'turma_virtual':
        return (
          <TurmaVirtualScreen
            uiState={turmaDetailUiState}
            onBack={() => {
              stores.turmasStore.closeTurma();
              popToHome();
            }}
            onSelectTab={stores.turmasStore.selectDetailTab}
            onRefreshTab={() => void stores.turmasStore.refreshActiveDetailTab()}
            onToggleNoticia={(id) => void stores.turmasStore.toggleNoticia(id)}
            onDownloadMaterial={(id) => void stores.turmasStore.downloadMaterial(id)}
            onDownloadAnexo={(id) => void stores.turmasStore.downloadAnexoNoticia(id)}
            onOpenDownloadedFile={setActiveDocument}
            onConsumeDownloadedFile={stores.turmasStore.consumeDownloadedFile}
            onOpenLogin={navigateToLogin}
            onDismissError={stores.turmasStore.dismissDetailError}
          />
        );

      case 'boletim':
        return (
          <BoletimScreen
            uiState={discenteUiState}
            authUiState={authUiState}
            onBack={popToHome}
            onRefresh={() => void stores.discenteStore.refreshBoletim()}
            onOpenLogin={navigateToLogin}
            onDismissError={stores.discenteStore.dismissBoletimError}
          />
        );

      case 'documentos':
        return (
          <DocumentosScreen
            core={core}
            uiState={discenteUiState}
            onBack={popToHome}
            onEmitirDocumento={(tipo) => void stores.discenteStore.emitirDocumento(tipo)}
            onOpenDocumentViewer={setActiveDocument}
            onConsumeEmitted={stores.discenteStore.consumeEmittedDocumento}
            onOpenLogin={navigateToLogin}
            onDismissError={stores.discenteStore.dismissDocumentosError}
          />
        );

      case 'home':
        if (activeTab === 'RU') {
          return (
            <RuScreen
              uiState={ruUiState}
              authState={authUiState.authState}
              onSelectCampus={stores.ruStore.selectCampus}
              onSelectDay={stores.ruStore.selectDay}
              onSelectMeal={stores.ruStore.selectMeal}
              onToggleAllergen={stores.ruStore.toggleAllergen}
              onSetHideRestricted={stores.ruStore.setHideRestricted}
              onClearAllergens={stores.ruStore.clearAllergens}
              onRefreshMenu={() => void stores.ruStore.refreshMenu(true)}
              onRefreshExtras={() => void stores.ruStore.refreshPricesAndNotices(true)}
              onSetCampusSheetOpen={stores.ruStore.setCampusSheetOpen}
              onSetAllergenSheetOpen={stores.ruStore.setAllergenSheetOpen}
              onSetNoticesSheetOpen={stores.ruStore.setNoticesAndPricesSheetOpen}
              onOpenNotifications={() => openNotificationCenter('RU')}
              onOpenAccountSheet={() => setShowAccountSheet(true)}
              onOpenLogin={navigateToLogin}
              onOpenCachedDocument={setActiveDocument}
              onDismissMenuError={stores.ruStore.dismissMenuError}
              onDismissExtrasError={stores.ruStore.dismissExtrasError}
            />
          );
        }

        if (activeTab === 'TURMAS') {
          return (
            <TurmasScreen
              uiState={turmasUiState}
              authUiState={authUiState}
              onSelectListTab={stores.turmasStore.selectListTab}
              onSelectDay={stores.turmasStore.selectDay}
              onToggleExpandClass={stores.turmasStore.toggleExpandedClass}
              onOpenTurma={(frontEndId) =>
                pushRoute({ kind: 'turma_virtual', frontEndId })
              }
              onRefresh={() => void stores.turmasStore.refreshSemestre()}
              onOpenLogin={navigateToLogin}
              onOpenAccountSheet={() => setShowAccountSheet(true)}
              onOpenNotifications={() => openNotificationCenter('TURMAS')}
              onDismissError={stores.turmasStore.dismissListError}
            />
          );
        }

        return (
          <DiscenteScreen
            core={core}
            uiState={discenteUiState}
            authUiState={authUiState}
            onToggleQrCardFace={stores.discenteStore.toggleQrCardFace}
            onRefreshProfile={() => void stores.discenteStore.refreshProfileAndSaldo()}
            onSyncAll={() => void stores.authStore.syncAll()}
            onOpenBoletim={() => pushRoute({ kind: 'boletim' })}
            onOpenDocumentos={() => pushRoute({ kind: 'documentos' })}
            onOpenLogin={navigateToLogin}
            onOpenAccountSheet={() => setShowAccountSheet(true)}
            onOpenNotifications={() => openNotificationCenter('DISCENTE')}
            onCheckForUpdates={() => void stores.discenteStore.checkForUpdates(true)}
            onApplyUpdate={() => void stores.discenteStore.applyUpdate()}
            onDismissUpdateError={stores.discenteStore.dismissUpdateError}
            onLogout={() => {
              stores.discenteStore.resetQrCardToBalance();
              void stores.authStore.logout(true);
            }}
            onDismissProfileError={stores.discenteStore.dismissProfileError}
            onDismissSyncError={stores.authStore.dismissSyncError}
          />
        );
    }
  };

  return (
    <>
      <div className="app-perspective-stage">
        {/* Frente: App Shell */}
        <div
          className={`app-flip-face ${isQrPassOpen ? 'app-flip-face--hidden' : ''}`.trim()}
          aria-hidden={isQrPassOpen}
          style={{
            transform: isQrPassOpen ? 'rotateY(180deg)' : 'rotateY(0deg)',
            visibility: !isQrPassOpen || isFlipAnimating ? 'visible' : 'hidden',
            pointerEvents: isQrPassOpen ? 'none' : 'auto',
          }}
        >
          <div className="app-shell">
            {isExpandedWidth && isTopLevelRoute && (
              <UnbNavigationRail
                currentDestination={activeTab}
                onSelect={navigateToTopDestination}
              />
            )}

            <main className="app-shell-main">{renderMainRoute()}</main>

            {!isExpandedWidth && isTopLevelRoute && (
              <CapsuleBottomBar
                currentDestination={activeTab}
                onSelect={navigateToTopDestination}
              />
            )}
          </div>
        </div>

        {/* Verso: Passe QR Tela Cheia (#FFFFFF puro) com rotacao 3D simetrica */}
        <div
          className={`app-flip-face ${!isQrPassOpen ? 'app-flip-face--hidden' : ''}`.trim()}
          aria-hidden={!isQrPassOpen}
          style={{
            transform: isQrPassOpen ? 'rotateY(0deg)' : 'rotateY(-180deg)',
            visibility: isQrPassOpen || isFlipAnimating ? 'visible' : 'hidden',
            pointerEvents: isQrPassOpen ? 'auto' : 'none',
          }}
        >
          <RuFullScreenQrPass
            active={isQrPassOpen}
            core={core}
            carteirinha={discenteUiState.carteirinha?.value ?? null}
            isLoading={isLoadingQr}
            hasError={discenteUiState.carteirinhaError !== null}
            onDismiss={stores.discenteStore.resetQrCardToBalance}
            style={{
              width: '100%',
              height: '100%',
            }}
          />
        </div>
      </div>

      <AccountQuickSheet
        open={showAccountSheet}
        authUiState={authUiState}
        themeMode={themeMode}
        systemPrefersDark={systemPrefersDark}
        onSelectThemeMode={themeManager.setMode}
        onOpenLogin={navigateToLogin}
        onSyncAll={() => void stores.authStore.syncAll()}
        onLogout={() => {
          stores.discenteStore.resetQrCardToBalance();
          void stores.authStore.logout(true);
        }}
        onDismiss={() => setShowAccountSheet(false)}
      />

      <NotificationCenterSheet
        open={showNotificationCenter}
        initialSection={notificationInitialSection}
        campusRu={ruUiState.preferencias.campusSelecionado}
        ruNotificacoes={ruUiState.preferencias.notificacoes}
        sigaaNotificacoes={authUiState.sigaaNotificacoes}
        turmas={authUiState.turmasParaNotificacao}
        onSetRuHabilitadas={stores.ruStore.setNotificacoesHabilitadas}
        onToggleRuSlot={stores.ruStore.toggleNotificacaoSlot}
        onToggleRuDay={stores.ruStore.toggleNotificacaoDia}
        onToggleSigaaTurmasSecao={stores.authStore.toggleSigaaTurmasSecao}
        onSetAulasLembrete={stores.authStore.setAulasLembrete}
        onSetAntecedenciaAulaMinutos={stores.authStore.setAntecedenciaAulaMinutos}
        onSetAvisosEMateriaisNovos={stores.authStore.setAvisosEMateriaisNovos}
        onSetProvasETarefasLembrete={stores.authStore.setProvasETarefasLembrete}
        onSetNotasParciaisAlteradas={stores.authStore.setNotasParciaisAlteradas}
        onSetRiscoFaltaAlerta={stores.authStore.setRiscoFaltaAlerta}
        onToggleTurmaSilenciada={stores.authStore.toggleTurmaSilenciada}
        onToggleSigaaDiscenteSecao={stores.authStore.toggleSigaaDiscenteSecao}
        onSetSaldoRuBaixoAlerta={stores.authStore.setSaldoRuBaixoAlerta}
        onSetLimiteRefeicoesRuBaixo={stores.authStore.setLimiteRefeicoesRuBaixo}
        onSetExtratoRuMovimentacaoAlerta={stores.authStore.setExtratoRuMovimentacaoAlerta}
        onSetBoletimAlteradoAlerta={stores.authStore.setBoletimAlteradoAlerta}
        onSetIndicesAlteradosAlerta={stores.authStore.setIndicesAlteradosAlerta}
        onSetSyncInterval={stores.authStore.setSigaaSyncInterval}
        onSetSomenteWifi={stores.authStore.setSigaaSomenteWifi}
        onResyncSchedules={() => {
          if (ruUiState.preferencias.notificacoes.habilitado) {
            stores.ruStore.setNotificacoesHabilitadas(true);
          }
          stores.authStore.resyncSigaaNotificacoesSchedule();
        }}
        onDismiss={() => setShowNotificationCenter(false)}
      />

      <InAppDocumentViewerModal
        doc={activeDocument}
        core={core}
        onClose={() => setActiveDocument(null)}
      />

      {showSplash && <PortalZoomSplashOverlay onFinished={() => setShowSplash(false)} />}
    </>
  );
};
