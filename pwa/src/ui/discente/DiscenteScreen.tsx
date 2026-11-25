import {
  Bell,
  ChevronRight,
  Download,
  FileText,
  GraduationCap,
  Lock,
  LogOut,
  PlusSquare,
  QrCode,
  RefreshCw,
  Share,
  Smartphone,
  Sun,
  User,
  UserCheck,
} from 'lucide-react';
import QRCode from 'qrcode';
import React, { useEffect, useMemo, useState } from 'react';
import type {
  CarteirinhaEstudantil,
  DiscenteResumo,
  PwaUpdateState,
  SaldoExtratoRu,
  UnbPocketCore,
} from '../../core/model/models';
import type { UiErrorBanner } from '../common/coreErrorMapper';
import { Formatters } from '../common/formatters';
import {
  CachedBlobImage,
  EditorialCard,
  EditorialHeader,
  EmptyStateCard,
  ErrorBanner,
  HeaderActionIconButton,
  InnerTile,
  OfflineOriginStrip,
  PreloadStatusBanner,
  SkeletonBox,
  SkeletonInlineBar,
  StatusTag,
  UnbPullToRefreshBox,
} from '../components/CommonComponents';
import { ConfirmDialog, ModalDialogSheet } from '../components/ModalDialogSheet';
import type { AuthUiState, DiscenteUiState } from '../viewmodel/appStores';

export function isRunningStandalone(): boolean {
  if (typeof window === 'undefined') return false;
  const standaloneMedia = window.matchMedia?.('(display-mode: standalone)').matches ?? false;
  const iosStandalone = Boolean((window.navigator as Navigator & { standalone?: boolean }).standalone);
  return standaloneMedia || iosStandalone;
}

function formatQrPayloadGrouped(raw: string): string {
  const digits = raw.replace(/\s+/g, '');
  return digits.replace(/(.{4})/g, '$1 ').trim();
}

interface DiscenteScreenProps {
  readonly core: UnbPocketCore;
  readonly uiState: DiscenteUiState;
  readonly authUiState: AuthUiState;
  readonly onToggleQrCardFace: () => void;
  readonly onRefreshProfile: () => void;
  readonly onSyncAll: () => void;
  readonly onOpenBoletim: () => void;
  readonly onOpenDocumentos: () => void;
  readonly onOpenLogin: () => void;
  readonly onOpenAccountSheet: () => void;
  readonly onOpenNotifications: () => void;
  readonly onCheckForUpdates: () => void;
  readonly onApplyUpdate: () => void;
  readonly onDismissUpdateError: () => void;
  readonly onLogout: () => void;
  readonly onDismissProfileError: () => void;
  readonly onDismissSyncError: () => void;
}

export const DiscenteScreen: React.FC<DiscenteScreenProps> = ({
  core,
  uiState,
  authUiState,
  onToggleQrCardFace,
  onRefreshProfile,
  onSyncAll,
  onOpenBoletim,
  onOpenDocumentos,
  onOpenLogin,
  onOpenAccountSheet,
  onOpenNotifications,
  onCheckForUpdates,
  onApplyUpdate,
  onDismissUpdateError,
  onLogout,
  onDismissProfileError,
  onDismissSyncError,
}) => {
  const [showLogoutDialog, setShowLogoutDialog] = useState(false);
  const [showInstallGuide, setShowInstallGuide] = useState(false);

  const resumoSynced = uiState.discenteResumo;
  const resumo = resumoSynced?.value ?? null;
  const saldoSynced = uiState.saldoExtrato;
  const saldo = saldoSynced?.value ?? null;
  const isAuthenticated = uiState.authState.kind === 'Authenticated';

  const isEssentialSyncing =
    authUiState.isSubmittingLogin ||
    authUiState.isSyncingAll ||
    uiState.authState.kind === 'Reauthenticating' ||
    authUiState.preloadProgress.phase === 'RUNNING_ESSENTIAL';

  const isLoadingEssencial =
    uiState.isRefreshingProfile || isEssentialSyncing || !uiState.hasResolvedInitialCache;

  const hasDataOrAuth =
    resumo !== null ||
    saldo !== null ||
    isAuthenticated ||
    isEssentialSyncing ||
    !uiState.hasResolvedInitialCache;

  const subtitle = resumo
    ? [resumo.perfil.semestreAtual.trim() || null, resumo.perfil.curso.trim() || null]
        .filter(Boolean)
        .join(' · ') || 'Universidade de Brasília'
    : isAuthenticated || isEssentialSyncing
      ? 'Conta SIGAA conectada'
      : 'Conecte sua conta para acessar perfil e RU';

  return (
    <div className="screen-root">
      <EditorialHeader
        title="Perfil Discente"
        subtitle={
          <p
            className="ty-label"
            style={{
              color: 'var(--muted)',
              whiteSpace: 'nowrap',
              overflow: 'hidden',
              textOverflow: 'ellipsis',
            }}
          >
            {subtitle}
          </p>
        }
        actions={
          <>
            {hasDataOrAuth && (
              <HeaderActionIconButton
                icon={RefreshCw}
                label="Atualizar perfil e saldo"
                isLoading={uiState.isRefreshingProfile || isEssentialSyncing}
                onClick={onRefreshProfile}
              />
            )}
            <HeaderActionIconButton
              icon={Bell}
              label="Notificações do Discente"
              onClick={onOpenNotifications}
            />
            <HeaderActionIconButton
              icon={isAuthenticated ? UserCheck : User}
              label={isAuthenticated ? 'Conta UnB conectada' : 'Entrar na conta UnB'}
              onClick={onOpenAccountSheet}
            />
          </>
        }
      />

      {!hasDataOrAuth ? (
        <div className="screen-scroll">
          {uiState.profileError && (
            <ErrorBanner
              banner={uiState.profileError}
              onRetry={uiState.profileError.canRetry ? onRefreshProfile : null}
              onDismiss={onDismissProfileError}
            />
          )}

          <EmptyStateCard
            icon={Lock}
            title="Entre com sua conta do SIGAA"
            body="Veja seu IRA, Média Ponderada, integralização curricular, saldo e extrato do RU, passe QR da catraca, boletim e declarações oficiais."
            actionLabel="Entrar no SIGAA"
            onAction={onOpenLogin}
          />

          <PwaAppUpdateCard
            pwaUpdate={uiState.pwaUpdate}
            updateError={uiState.updateError}
            onCheckForUpdates={onCheckForUpdates}
            onApplyUpdate={onApplyUpdate}
            onOpenInstallGuide={() => setShowInstallGuide(true)}
            onDismissUpdateError={onDismissUpdateError}
          />
        </div>
      ) : (
        <UnbPullToRefreshBox
          isRefreshing={uiState.isRefreshingProfile || isEssentialSyncing}
          onRefresh={onRefreshProfile}
        >
          <PreloadStatusBanner progress={authUiState.preloadProgress} />

            {authUiState.syncError && (
              <ErrorBanner
                banner={authUiState.syncError}
                onRetry={authUiState.syncError.canRetry ? onSyncAll : null}
                onLoginClick={authUiState.syncError.requiresLogin ? onOpenLogin : null}
                onDismiss={onDismissSyncError}
              />
            )}

            {uiState.profileError && (
              <ErrorBanner
                banner={uiState.profileError}
                onRetry={uiState.profileError.canRetry ? onRefreshProfile : null}
                onLoginClick={uiState.profileError.requiresLogin ? onOpenLogin : null}
                onDismiss={onDismissProfileError}
              />
            )}

            {!resumo && isLoadingEssencial ? (
              <>
                <SkeletonBox style={{ width: '100%', height: '104px', borderRadius: '16px' }} />
                <SkeletonBox style={{ width: '100%', height: '188px', borderRadius: '16px' }} />
              </>
            ) : resumo ? (
              <>
                <StudentIdentityCard core={core} resumo={resumo} />
                <AcademicMetricsCard resumo={resumo} />
              </>
            ) : null}

            <NavEntryCard
              Icon={GraduationCap}
              title="Boletim & Menções"
              subtitle="Disciplinas cursadas, menções e histórico por semestre"
              onClick={onOpenBoletim}
            />

            <NavEntryCard
              Icon={FileText}
              title="Documentos Digitais"
              subtitle="Declaração de vínculo, atestado, histórico e passe livre"
              onClick={onOpenDocumentos}
            />

            <RuBalanceCard
              saldo={saldo}
              isLoadingSaldo={!saldo && isLoadingEssencial}
              onFlipToQr={onToggleQrCardFace}
            />

            <PwaAppUpdateCard
              pwaUpdate={uiState.pwaUpdate}
              updateError={uiState.updateError}
              onCheckForUpdates={onCheckForUpdates}
              onApplyUpdate={onApplyUpdate}
              onOpenInstallGuide={() => setShowInstallGuide(true)}
              onDismissUpdateError={onDismissUpdateError}
            />

            {isAuthenticated ? (
              <div style={{ display: 'flex', gap: '8px', paddingTop: '4px' }}>
                <button
                  type="button"
                  className="btn-secondary"
                  disabled={authUiState.isSyncingAll}
                  style={{ flex: 1 }}
                  onClick={onSyncAll}
                >
                  {authUiState.isSyncingAll ? (
                    <SkeletonInlineBar width={20} height={6} color="var(--ink)" />
                  ) : (
                    <RefreshCw size={17} strokeWidth={1.75} />
                  )}
                  <span className="ty-label">
                    {authUiState.isSyncingAll ? 'Sincronizando...' : 'Sincronizar dados'}
                  </span>
                </button>

                <button
                  type="button"
                  className="btn-danger-outline"
                  disabled={authUiState.isLoggingOut}
                  style={{ flex: 1 }}
                  onClick={() => setShowLogoutDialog(true)}
                >
                  <LogOut size={17} strokeWidth={1.75} />
                  <span className="ty-label">Desconectar</span>
                </button>
              </div>
            ) : (
              <button
                type="button"
                className="btn-primary"
                style={{ width: '100%' }}
                onClick={onOpenLogin}
              >
                Conectar conta SIGAA
              </button>
            )}

            {(resumoSynced ?? saldoSynced) && (
              <OfflineOriginStrip
                origin={(resumoSynced ?? saldoSynced)!.origin}
                syncedAtEpochMillis={(resumoSynced ?? saldoSynced)!.syncedAtEpochMillis}
              />
            )}
        </UnbPullToRefreshBox>
      )}

      <ConfirmDialog
        open={showLogoutDialog}
        title="Desconectar conta SIGAA?"
        message="As credenciais salvas no cofre criptografado e os dados acadêmicos em cache serão removidos deste dispositivo."
        confirmLabel="Desconectar"
        isDestructive
        onCancel={() => setShowLogoutDialog(false)}
        onConfirm={() => {
          setShowLogoutDialog(false);
          onLogout();
        }}
      />

      <IosAddToHomeScreenSheet
        open={showInstallGuide}
        onDismiss={() => setShowInstallGuide(false)}
      />
    </div>
  );
};

const StudentIdentityCard: React.FC<{
  readonly core: UnbPocketCore;
  readonly resumo: DiscenteResumo;
}> = ({ core, resumo }) => {
  const { perfil } = resumo;
  return (
    <EditorialCard>
      <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
        <div
          style={{
            width: '64px',
            height: '64px',
            borderRadius: '50%',
            backgroundColor: 'var(--surface-raised)',
            overflow: 'hidden',
            display: 'inline-flex',
            alignItems: 'center',
            justifyContent: 'center',
            flexShrink: 0,
          }}
        >
          <CachedBlobImage
            core={core}
            localPath={perfil.fotoLocalPath}
            alt="Foto do discente"
            style={{ width: '100%', height: '100%', objectFit: 'cover' }}
            fallback={
              <span className="ty-title-strong" style={{ color: 'var(--ink)' }}>
                {Formatters.initialsFromName(perfil.nomeCompleto)}
              </span>
            }
          />
        </div>

        <div style={{ flex: 1, minWidth: 0 }}>
          <p className="ty-title-strong" style={{ color: 'var(--ink)' }}>
            {perfil.nomeCompleto}
          </p>
          <p className="ty-mono" style={{ color: 'var(--muted)', marginTop: '2px' }}>
            Matrícula: {perfil.matricula}
          </p>
          {perfil.nivel && (
            <p className="ty-label" style={{ color: 'var(--muted)' }}>
              {perfil.nivel}
            </p>
          )}
          <div style={{ display: 'flex', gap: '6px', marginTop: '8px', flexWrap: 'wrap' }}>
            {perfil.status && <StatusTag text={perfil.status.toUpperCase()} />}
            {perfil.semestreAtual && <StatusTag text={perfil.semestreAtual} />}
          </div>
        </div>
      </div>
    </EditorialCard>
  );
};

const AcademicMetricsCard: React.FC<{ readonly resumo: DiscenteResumo }> = ({ resumo }) => {
  const { indices } = resumo;
  const pctValue = Math.min(100, Math.max(0, indices.percentualIntegralizadoExato ?? 0));
  const pctLabel = Formatters.formatIntegralizacaoPercent(
    indices.percentualIntegralizadoExato,
    indices.percentualIntegralizadoTexto,
  );

  const totalCh = indices.chTotalCurriculo;
  const obPending = indices.chObrigatoriaPendente;
  const optPending = indices.chOptativaPendente;
  const compPending = indices.chComplementarPendente ?? 0;
  const completedCh =
    totalCh !== null
      ? Math.max(0, totalCh - (obPending ?? 0) - (optPending ?? 0) - compPending)
      : null;

  return (
    <EditorialCard style={{ gap: '12px' }}>
      <span className="ty-overline" style={{ color: 'var(--muted)' }}>
        RENDIMENTO ACADÊMICO
      </span>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '8px' }}>
        <InnerTile style={{ alignItems: 'center', textAlign: 'center' }}>
          <span className="ty-overline" style={{ color: 'var(--muted)' }}>
            IRA
          </span>
          <span className="ty-metric-serif" style={{ color: 'var(--ink)', marginTop: '6px' }}>
            {indices.ira !== null ? indices.ira.toFixed(4) : '-'}
          </span>
          <span className="ty-label" style={{ color: 'var(--muted)', marginTop: '4px' }}>
            Índice de Rendimento
          </span>
        </InnerTile>

        <InnerTile style={{ alignItems: 'center', textAlign: 'center' }}>
          <span className="ty-overline" style={{ color: 'var(--muted)' }}>
            MÉDIA PONDERADA
          </span>
          <span className="ty-metric-serif" style={{ color: 'var(--ink)', marginTop: '6px' }}>
            {indices.mp !== null ? indices.mp.toFixed(4) : '-'}
          </span>
          <span className="ty-label" style={{ color: 'var(--muted)', marginTop: '4px' }}>
            Ponderada Geral
          </span>
        </InnerTile>
      </div>

      <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <span className="ty-title" style={{ color: 'var(--ink)' }}>
            Integralização Curricular
          </span>
          <span className="ty-body-strong ty-tabular" style={{ color: 'var(--ink)' }}>
            {pctLabel}
          </span>
        </div>

        <div
          style={{
            width: '100%',
            height: '8px',
            borderRadius: '4px',
            backgroundColor: 'var(--surface-raised)',
            overflow: 'hidden',
          }}
        >
          <div
            style={{
              width: `${pctValue}%`,
              height: '100%',
              backgroundColor: 'var(--pill-active-bg)',
              borderRadius: '4px',
            }}
          />
        </div>

        {totalCh !== null && completedCh !== null && (
          <>
            <div style={{ display: 'flex', justifyContent: 'space-between' }}>
              <span className="ty-label ty-tabular" style={{ color: 'var(--muted)' }}>
                Concluídas: {completedCh}h
              </span>
              <span className="ty-label ty-tabular" style={{ color: 'var(--muted)' }}>
                Total: {totalCh}h
              </span>
            </div>
            {(obPending !== null || optPending !== null || compPending > 0) && (
              <p className="ty-label ty-tabular" style={{ color: 'var(--muted)' }}>
                Pendentes: Obrigatórias {obPending ?? 0}h · Optativas {optPending ?? 0}h
                {compPending > 0 ? ` · Complementares ${compPending}h` : ''}
              </p>
            )}
          </>
        )}
      </div>
    </EditorialCard>
  );
};

const NavEntryCard: React.FC<{
  readonly Icon: typeof GraduationCap;
  readonly title: string;
  readonly subtitle: string;
  readonly onClick: () => void;
}> = ({ Icon, title, subtitle, onClick }) => (
  <EditorialCard onClick={onClick}>
    <div style={{ display: 'flex', alignItems: 'center', gap: '12px', minHeight: '40px' }}>
      <div
        style={{
          width: '40px',
          height: '40px',
          borderRadius: '50%',
          backgroundColor: 'var(--surface-raised)',
          display: 'inline-flex',
          alignItems: 'center',
          justifyContent: 'center',
          color: 'var(--ink)',
          flexShrink: 0,
        }}
      >
        <Icon size={20} strokeWidth={1.75} />
      </div>
      <div style={{ flex: 1, minWidth: 0 }}>
        <p className="ty-title" style={{ color: 'var(--ink)' }}>
          {title}
        </p>
        <p className="ty-label" style={{ color: 'var(--muted)' }}>
          {subtitle}
        </p>
      </div>
      <ChevronRight size={19} strokeWidth={1.75} style={{ color: 'var(--muted)' }} />
    </div>
  </EditorialCard>
);

const RuBalanceCard: React.FC<{
  readonly saldo: SaldoExtratoRu | null;
  readonly isLoadingSaldo: boolean;
  readonly onFlipToQr: () => void;
}> = ({ saldo, isLoadingSaldo, onFlipToQr }) => {
  const transactions = saldo?.transacoesUltimos7Dias ?? [];
  const remainingSummary = saldo
    ? Formatters.formatRuRemainingMealsSummary(saldo.refeicoesRestantes)
    : null;

  return (
    <EditorialCard style={{ gap: '12px' }}>
      <div>
        <span className="ty-overline" style={{ color: 'var(--muted)' }}>
          RESTAURANTE UNIVERSITÁRIO
        </span>
        <p className="ty-label" style={{ color: 'var(--muted)', marginTop: '2px' }}>
          Saldo Disponível
        </p>
      </div>

      <InnerTile style={{ padding: '22px 16px', alignItems: 'center', textAlign: 'center' }}>
        {!saldo && isLoadingSaldo ? (
          <>
            <SkeletonBox style={{ width: '136px', height: '34px', borderRadius: '8px' }} />
            <SkeletonBox
              style={{ width: '184px', height: '12px', borderRadius: '4px', marginTop: '8px' }}
            />
          </>
        ) : (
          <>
            <span className="ty-metric-balance" style={{ color: 'var(--ink)' }}>
              {saldo ? Formatters.formatCentavosBrl(saldo.saldoAtualCentavos) : 'R$ -'}
            </span>
            {saldo && (
              <>
                <span className="ty-label" style={{ color: 'var(--muted)', marginTop: '6px' }}>
                  {Formatters.formatRuGroupSummary(saldo.grupoAcesso)}
                </span>
                {remainingSummary && (
                  <span className="ty-label" style={{ color: 'var(--ink)', marginTop: '2px' }}>
                    {remainingSummary}
                  </span>
                )}
              </>
            )}
          </>
        )}
      </InnerTile>

      <button
        type="button"
        className="btn-tonal"
        style={{ width: '100%' }}
        onClick={onFlipToQr}
      >
        <QrCode size={18} strokeWidth={1.75} />
        <span className="ty-title">Girar para QR Code da Catraca</span>
      </button>

      {transactions.length > 0 && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', marginTop: '4px' }}>
          <span className="ty-overline" style={{ color: 'var(--muted)' }}>
            TRANSAÇÕES RECENTES
          </span>
          {transactions.slice(0, 6).map((tx, idx) => (
            <InnerTile key={`${idx}_${tx.dataHoraTexto}_${tx.valorCentavos}`}>
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  gap: '8px',
                }}
              >
                <div style={{ flex: 1, minWidth: 0 }}>
                  <p
                    className="ty-title"
                    style={{
                      color: 'var(--ink)',
                      whiteSpace: 'nowrap',
                      overflow: 'hidden',
                      textOverflow: 'ellipsis',
                    }}
                  >
                    {tx.descricao}
                  </p>
                  <p className="ty-label ty-tabular" style={{ color: 'var(--muted)' }}>
                    {tx.dataHoraTexto}
                  </p>
                </div>
                <span
                  className="ty-body-strong ty-tabular"
                  style={{
                    color: tx.valorCentavos < 0 ? 'var(--ink)' : 'var(--positive-text)',
                    flexShrink: 0,
                  }}
                >
                  {Formatters.formatCentavosBrl(tx.valorCentavos)}
                </span>
              </div>
            </InnerTile>
          ))}
        </div>
      )}
    </EditorialCard>
  );
};

const PwaAppUpdateCard: React.FC<{
  readonly pwaUpdate: PwaUpdateState;
  readonly updateError: UiErrorBanner | null;
  readonly onCheckForUpdates: () => void;
  readonly onApplyUpdate: () => void;
  readonly onOpenInstallGuide: () => void;
  readonly onDismissUpdateError: () => void;
}> = ({
  pwaUpdate,
  updateError,
  onCheckForUpdates,
  onApplyUpdate,
  onOpenInstallGuide,
  onDismissUpdateError,
}) => {
  const standalone = isRunningStandalone();
  const subtitleText = pwaUpdate.checking
    ? 'Verificando atualizações...'
    : pwaUpdate.updateReady
      ? 'Nova versão pronta para aplicar'
      : pwaUpdate.lastCheckedAtEpochMillis
        ? `Atualizado · ${Formatters.formatSyncTimestamp(pwaUpdate.lastCheckedAtEpochMillis)}`
        : standalone
          ? 'Instalado na Tela de Início · Somente leitura'
          : 'UnB Pocket PWA · Somente leitura';

  return (
    <EditorialCard style={{ gap: '12px' }}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: '8px' }}>
        <div>
          <p className="ty-title" style={{ color: 'var(--ink)' }}>
            Versão do Aplicativo
          </p>
          <p className="ty-label" style={{ color: 'var(--muted)' }}>
            {subtitleText}
          </p>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
          {pwaUpdate.updateReady && <StatusTag text="NOVA" tone="INFO" />}
          <span className="ty-mono" style={{ color: 'var(--muted)' }}>
            v{pwaUpdate.currentVersionName}
          </span>
        </div>
      </div>

      {updateError && (
        <ErrorBanner
          banner={updateError}
          onRetry={updateError.canRetry ? onCheckForUpdates : null}
          onDismiss={onDismissUpdateError}
        />
      )}

      {pwaUpdate.updateReady ? (
        <button type="button" className="btn-primary" style={{ width: '100%' }} onClick={onApplyUpdate}>
          <Download size={18} strokeWidth={1.75} />
          <span>Atualizar aplicativo agora</span>
        </button>
      ) : (
        <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
          <button
            type="button"
            className="btn-secondary"
            disabled={pwaUpdate.checking}
            style={{ flex: 1 }}
            onClick={onCheckForUpdates}
          >
            <RefreshCw size={16} strokeWidth={1.75} />
            <span className="ty-label">
              {pwaUpdate.checking ? 'Verificando...' : 'Verificar atualizações'}
            </span>
          </button>

          {!standalone && (
            <button
              type="button"
              className="btn-tonal"
              style={{ flex: 1 }}
              onClick={onOpenInstallGuide}
            >
              <Smartphone size={16} strokeWidth={1.75} />
              <span className="ty-label">Instalar no iOS</span>
            </button>
          )}
        </div>
      )}
    </EditorialCard>
  );
};

export const IosAddToHomeScreenSheet: React.FC<{
  readonly open: boolean;
  readonly onDismiss: () => void;
}> = ({ open, onDismiss }) => (
  <ModalDialogSheet
    open={open}
    onClose={onDismiss}
    title="Instalar no iPhone / iPad"
    subtitle="Use o UnB Pocket em tela cheia com cache offline persistente"
  >
    <InnerTile style={{ gap: '6px' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
        <Share size={18} strokeWidth={1.75} style={{ color: 'var(--ink)' }} />
        <p className="ty-title" style={{ color: 'var(--ink)' }}>
          1. Toque em Compartilhar no Safari
        </p>
      </div>
      <p className="ty-body" style={{ color: 'var(--muted)' }}>
        Na barra inferior ou superior do Safari, toque no ícone quadrado com seta para cima.
      </p>
    </InnerTile>

    <InnerTile style={{ gap: '6px' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
        <PlusSquare size={18} strokeWidth={1.75} style={{ color: 'var(--ink)' }} />
        <p className="ty-title" style={{ color: 'var(--ink)' }}>
          2. Escolha &quot;Adicionar à Tela de Início&quot;
        </p>
      </div>
      <p className="ty-body" style={{ color: 'var(--muted)' }}>
        Confirme em &quot;Adicionar&quot;. O UnB Pocket abrirá sem barras do navegador, com ícone
        próprio, suporte a Face ID / Senhas da Apple e notificações locais (iOS 16.4+).
      </p>
    </InnerTile>

    <InnerTile style={{ gap: '4px' }}>
      <p className="ty-label" style={{ color: 'var(--ink)' }}>
        Abriu pelo WhatsApp ou Instagram?
      </p>
      <p className="ty-body" style={{ color: 'var(--muted)' }}>
        Toque nos três pontos do canto e selecione &quot;Abrir no Navegador&quot; para habilitar a
        instalação no Safari.
      </p>
    </InnerTile>
  </ModalDialogSheet>
);

export const RuFullScreenQrPass: React.FC<{
  readonly active?: boolean;
  readonly core: UnbPocketCore;
  readonly carteirinha: CarteirinhaEstudantil | null;
  readonly isLoading: boolean;
  readonly hasError: boolean;
  readonly onDismiss: () => void;
  readonly style?: React.CSSProperties;
}> = ({ active = true, core, carteirinha, isLoading, hasError, onDismiss, style }) => {
  const [svgMarkup, setSvgMarkup] = useState<string | null>(null);

  // Keep iOS/mobile screen awake while holding QR at the RU turnstile
  useEffect(() => {
    if (!active) return;
    let sentinel: WakeLockSentinel | null = null;
    if (typeof navigator !== 'undefined' && 'wakeLock' in navigator) {
      void navigator.wakeLock
        .request('screen')
        .then((s) => {
          sentinel = s;
        })
        .catch(() => undefined);
    }
    return () => {
      void sentinel?.release().catch(() => undefined);
    };
  }, [active]);

  useEffect(() => {
    const payload = carteirinha?.qrPayload16Digitos?.trim();
    if (!payload) {
      setSvgMarkup(null);
      return;
    }
    let cancelled = false;
    void QRCode.toString(payload, {
      type: 'svg',
      margin: 2,
      errorCorrectionLevel: 'M',
      color: {
        dark: '#0B0C0E',
        light: '#FFFFFF',
      },
    })
      .then((svg) => {
        if (!cancelled) setSvgMarkup(svg);
      })
      .catch(() => {
        if (!cancelled) setSvgMarkup(null);
      });
    return () => {
      cancelled = true;
    };
  }, [carteirinha?.qrPayload16Digitos]);

  const svgDataUri = useMemo(() => {
    if (!svgMarkup) return null;
    return `data:image/svg+xml;utf8,${encodeURIComponent(svgMarkup)}`;
  }, [svgMarkup]);

  return (
    <div
      role="button"
      tabIndex={0}
      aria-label="Passe QR da Catraca RU. Toque em qualquer lugar para voltar."
      onClick={onDismiss}
      onKeyDown={(e) => {
        if (e.key === 'Escape' || e.key === 'Enter' || e.key === ' ') {
          e.preventDefault();
          onDismiss();
        }
      }}
      style={{
        ...style,
        backgroundColor: 'var(--qr-canvas)',
        color: 'var(--qr-ink)',
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        padding: 'calc(var(--safe-top) + 24px) 24px calc(var(--safe-bottom) + 24px)',
        textAlign: 'center',
        cursor: 'pointer',
      }}
    >
      {carteirinha ? (
        <>
          <div
            style={{
              width: '280px',
              height: '280px',
              padding: '12px',
              backgroundColor: '#FFFFFF',
              borderRadius: '12px',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
            }}
          >
            {svgDataUri ? (
              <img
                src={svgDataUri}
                alt="QR Code da carteirinha estudantil UnB"
                style={{ width: '100%', height: '100%', objectFit: 'contain' }}
              />
            ) : (
              <CachedBlobImage
                core={core}
                localPath={carteirinha.qrImageLocalPath}
                alt="QR Code da carteirinha estudantil UnB"
                style={{ width: '100%', height: '100%', objectFit: 'contain' }}
                fallback={
                  <p className="ty-body" style={{ color: '#1A1A1C' }}>
                    QR Code indisponível. Toque para voltar.
                  </p>
                }
              />
            )}
          </div>

          <p
            className="ty-mono"
            style={{
              color: '#0B0C0E',
              fontSize: '0.9375rem',
              letterSpacing: '0.08em',
              marginTop: '16px',
            }}
          >
            {formatQrPayloadGrouped(carteirinha.qrPayload16Digitos)}
          </p>

          {carteirinha.validadeTexto && (
            <p className="ty-label" style={{ color: '#5C5C60', marginTop: '4px' }}>
              Validade: {carteirinha.validadeTexto.replace(/^validade\s*:?\s*/i, '')}
            </p>
          )}

          <p className="ty-body" style={{ color: '#1A1A1C', marginTop: '18px', maxWidth: '300px' }}>
            Mostre o QR Code na catraca a uma distância média
          </p>

          <div
            style={{
              marginTop: '18px',
              padding: '6px 12px',
              borderRadius: '999px',
              backgroundColor: '#F2F2F0',
              color: '#48484C',
              display: 'inline-flex',
              alignItems: 'center',
              gap: '6px',
            }}
          >
            <Sun size={14} strokeWidth={1.75} />
            <span className="ty-label">Aumente o brilho se necessário · Toque para voltar</span>
          </div>
        </>
      ) : isLoading ? (
        <>
          <div
            style={{
              width: '260px',
              height: '260px',
              borderRadius: '12px',
              backgroundColor: '#ECECE9',
            }}
          />
          <p className="ty-body" style={{ color: '#5C5C60', marginTop: '20px' }}>
            Emitindo Carteirinha Estudantil no SIGAA...
          </p>
        </>
      ) : (
        <>
          <p className="ty-title-strong" style={{ color: '#1A1A1C' }}>
            {hasError ? 'Não foi possível carregar a Carteirinha' : 'Carteirinha não emitida'}
          </p>
          <p className="ty-body" style={{ color: '#5C5C60', marginTop: '8px', maxWidth: '300px' }}>
            Conecte sua conta SIGAA com internet uma vez para salvar o QR Code offline neste
            dispositivo. Toque na tela para voltar.
          </p>
        </>
      )}
    </div>
  );
};
