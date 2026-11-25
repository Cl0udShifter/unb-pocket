import {
  Eye,
  EyeOff,
  Lock,
  LogOut,
  Monitor,
  Moon,
  RefreshCw,
  Sun,
  User,
} from 'lucide-react';
import React, { useState } from 'react';
import {
  BackHeaderButton,
  EditorialCard,
  EditorialHeader,
  ErrorBanner,
  HairlineDivider,
  InfoCalloutRow,
  InnerTile,
  PreloadStatusBanner,
  SkeletonInlineBar,
  StatusTag,
} from '../components/CommonComponents';
import { ConfirmDialog, ModalDialogSheet } from '../components/ModalDialogSheet';
import type { ThemeMode } from '../theme/themeManager';
import type { AuthUiState } from '../viewmodel/appStores';

interface LoginScreenProps {
  readonly uiState: AuthUiState;
  readonly onMatriculaChange: (value: string) => void;
  readonly onSenhaChange: (value: string) => void;
  readonly onSubmitLogin: () => void;
  readonly onBack: () => void;
  readonly onDismissError: () => void;
}

export const LoginScreen: React.FC<LoginScreenProps> = ({
  uiState,
  onMatriculaChange,
  onSenhaChange,
  onSubmitLogin,
  onBack,
  onDismissError,
}) => {
  const [showPassword, setShowPassword] = useState(false);

  const handleFormSubmit = (e: React.FormEvent): void => {
    e.preventDefault();
    onSubmitLogin();
  };

  return (
    <div className="screen-root">
      <EditorialHeader
        title="Entrar no SIGAA"
        navigationIcon={<BackHeaderButton onClick={onBack} label="Fechar login" />}
        subtitle={
          <p className="ty-label" style={{ color: 'var(--muted)' }}>
            Autenticação CAS · Universidade de Brasília
          </p>
        }
      />

      <div className="screen-scroll screen-scroll--subpage">
        <InfoCalloutRow text="Acesso estritamente somente leitura. Suas credenciais são enviadas ao CAS oficial da UnB e protegidas localmente com criptografia Web Crypto AES-GCM." />

        {uiState.loginError && (
          <ErrorBanner
            banner={uiState.loginError}
            onRetry={uiState.loginError.canRetry ? onSubmitLogin : null}
            onDismiss={onDismissError}
          />
        )}

        <form onSubmit={handleFormSubmit}>
          <EditorialCard style={{ gap: '12px' }}>
            <p className="ty-overline" style={{ color: 'var(--muted)' }}>
              CREDENCIAIS INSTITUCIONAIS
            </p>

            <div>
              <label
                htmlFor="unb-matricula-input"
                className="ty-label"
                style={{ color: 'var(--ink)', display: 'block', marginBottom: '6px' }}
              >
                Matrícula
              </label>
              <input
                id="unb-matricula-input"
                name="username"
                type="text"
                inputMode="numeric"
                autoComplete="username"
                autoCapitalize="off"
                autoCorrect="off"
                spellCheck={false}
                placeholder="Ex.: 231012345"
                disabled={uiState.isSubmittingLogin}
                value={uiState.matriculaInput}
                onChange={(e) => onMatriculaChange(e.target.value)}
                className="unb-input ty-tabular"
              />
            </div>

            <div>
              <label
                htmlFor="unb-senha-input"
                className="ty-label"
                style={{ color: 'var(--ink)', display: 'block', marginBottom: '6px' }}
              >
                Senha do SIGAA / CAS
              </label>
              <div className="unb-input-wrap">
                <input
                  id="unb-senha-input"
                  name="password"
                  type={showPassword ? 'text' : 'password'}
                  autoComplete="current-password"
                  disabled={uiState.isSubmittingLogin}
                  value={uiState.senhaInput}
                  onChange={(e) => onSenhaChange(e.target.value)}
                  className="unb-input"
                  style={{ paddingRight: '52px' }}
                />
                <button
                  type="button"
                  onClick={() => setShowPassword((prev) => !prev)}
                  aria-label={showPassword ? 'Ocultar senha' : 'Mostrar senha'}
                  style={{
                    position: 'absolute',
                    right: '2px',
                    width: '46px',
                    height: '46px',
                    display: 'inline-flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    color: 'var(--muted)',
                  }}
                >
                  {showPassword ? (
                    <EyeOff size={19} strokeWidth={1.75} />
                  ) : (
                    <Eye size={19} strokeWidth={1.75} />
                  )}
                </button>
              </div>
            </div>

            <button
              type="submit"
              disabled={uiState.isSubmittingLogin}
              className="btn-primary"
              style={{ width: '100%', minHeight: '52px', marginTop: '8px' }}
            >
              {uiState.isSubmittingLogin ? (
                <>
                  <SkeletonInlineBar width={24} height={7} color="var(--muted)" />
                  <span className="ty-title">
                    {uiState.syncStatusMessage ?? 'Autenticando no CAS...'}
                  </span>
                </>
              ) : (
                <>
                  <Lock size={18} strokeWidth={1.75} />
                  <span className="ty-title">Conectar conta SIGAA</span>
                </>
              )}
            </button>
          </EditorialCard>
        </form>

        {uiState.isSubmittingLogin && <PreloadStatusBanner progress={uiState.preloadProgress} />}

        <p className="ty-label" style={{ color: 'var(--muted)', padding: '0 4px' }}>
          O Cardápio semanal e a tabela de preços do RU continuam acessíveis sem login.
        </p>
      </div>
    </div>
  );
};

interface AccountQuickSheetProps {
  readonly open: boolean;
  readonly authUiState: AuthUiState;
  readonly themeMode: ThemeMode;
  readonly systemPrefersDark: boolean;
  readonly onSelectThemeMode: (mode: ThemeMode) => void;
  readonly onOpenLogin: () => void;
  readonly onSyncAll: () => void;
  readonly onLogout: () => void;
  readonly onDismiss: () => void;
}

export const AccountQuickSheet: React.FC<AccountQuickSheetProps> = ({
  open,
  authUiState,
  themeMode,
  systemPrefersDark,
  onSelectThemeMode,
  onOpenLogin,
  onSyncAll,
  onLogout,
  onDismiss,
}) => {
  const [showLogoutDialog, setShowLogoutDialog] = useState(false);
  const auth = authUiState.authState;

  const themeOptions: readonly {
    mode: ThemeMode;
    label: string;
    Icon: typeof Monitor;
  }[] = [
    {
      mode: 'SYSTEM',
      label: `Sistema (${systemPrefersDark ? 'Escuro' : 'Claro'})`,
      Icon: Monitor,
    },
    { mode: 'LIGHT', label: 'Claro', Icon: Sun },
    { mode: 'DARK', label: 'Escuro', Icon: Moon },
  ];

  return (
    <>
      <ModalDialogSheet
        open={open}
        onClose={onDismiss}
        title="Conta & Aparência"
      >
        {auth.kind === 'Authenticated' ? (
          <>
            <InnerTile bordered>
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  gap: '12px',
                }}
              >
                <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                  <div
                    style={{
                      width: '40px',
                      height: '40px',
                      borderRadius: '50%',
                      backgroundColor: 'var(--pill-active-bg)',
                      color: 'var(--pill-active-ink)',
                      display: 'inline-flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      flexShrink: 0,
                    }}
                  >
                    <User size={20} strokeWidth={1.75} />
                  </div>
                  <div>
                    <p className="ty-title-strong ty-tabular" style={{ color: 'var(--ink)' }}>
                      Matrícula {auth.matricula}
                    </p>
                    <p className="ty-label" style={{ color: 'var(--muted)' }}>
                      Sessão SIGAA ativa
                    </p>
                  </div>
                </div>
                <StatusTag text="ATIVO" tone="INFO" />
              </div>
            </InnerTile>

            <PreloadStatusBanner
              progress={authUiState.preloadProgress}
              containerColor="var(--surface-raised)"
              trackColor="var(--border)"
            />

            <InnerTile
              onClick={
                !authUiState.isSyncingAll
                  ? () => {
                      onSyncAll();
                      onDismiss();
                    }
                  : undefined
              }
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                <RefreshCw size={20} strokeWidth={1.75} style={{ color: 'var(--ink)' }} />
                <div>
                  <p className="ty-title" style={{ color: 'var(--ink)' }}>
                    {authUiState.isSyncingAll
                      ? 'Sincronizando...'
                      : 'Sincronizar dados acadêmicos'}
                  </p>
                  <p className="ty-label" style={{ color: 'var(--muted)' }}>
                    Turmas, avisos, perfil e saldo RU
                  </p>
                </div>
              </div>
            </InnerTile>
          </>
        ) : auth.kind === 'Reauthenticating' ? (
          <InnerTile bordered>
            <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
              <SkeletonInlineBar width={22} height={8} color="var(--muted)" />
              <div>
                <p className="ty-title" style={{ color: 'var(--ink)' }}>
                  Renovando sessão CAS
                </p>
                <p className="ty-label ty-tabular" style={{ color: 'var(--muted)' }}>
                  Matrícula {auth.matricula}
                </p>
              </div>
            </div>
          </InnerTile>
        ) : (
          <InnerTile bordered style={{ gap: '12px' }}>
            <p className="ty-title" style={{ color: 'var(--ink)' }}>
              Conta SIGAA não conectada
            </p>
            <p className="ty-body" style={{ color: 'var(--muted)' }}>
              Conecte sua matrícula UnB para sincronizar horários de turmas, saldo RU, carteirinha
              e boletim.
            </p>
            <button
              type="button"
              className="btn-primary"
              style={{ width: '100%' }}
              onClick={() => {
                onDismiss();
                onOpenLogin();
              }}
            >
              Entrar com CAS / SIGAA
            </button>
          </InnerTile>
        )}

        <HairlineDivider />

        <InnerTile style={{ gap: '12px' }}>
          <div>
            <p className="ty-title" style={{ color: 'var(--ink)' }}>
              Aparência
            </p>
            <p className="ty-label" style={{ color: 'var(--muted)' }}>
              Escolha o tema visual do aplicativo
            </p>
          </div>

          <div
            role="tablist"
            aria-label="Tema visual"
            style={{
              display: 'flex',
              gap: '4px',
              padding: '4px',
              borderRadius: 'var(--radius-capsule)',
              backgroundColor: 'var(--ground)',
              border: '1px solid var(--border)',
            }}
          >
            {themeOptions.map(({ mode, label, Icon }) => {
              const selected = themeMode === mode;
              return (
                <button
                  key={mode}
                  type="button"
                  role="tab"
                  aria-selected={selected}
                  onClick={() => onSelectThemeMode(mode)}
                  style={{
                    flex: 1,
                    minHeight: '40px',
                    padding: '6px 8px',
                    borderRadius: 'var(--radius-capsule)',
                    backgroundColor: selected ? 'var(--pill-active-bg)' : 'transparent',
                    color: selected ? 'var(--pill-active-ink)' : 'var(--muted)',
                    display: 'inline-flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    gap: '6px',
                  }}
                >
                  <Icon size={15} strokeWidth={1.75} style={{ flexShrink: 0 }} />
                  <span
                    className="ty-label"
                    style={{
                      whiteSpace: 'nowrap',
                      overflow: 'hidden',
                      textOverflow: 'ellipsis',
                    }}
                  >
                    {label}
                  </span>
                </button>
              );
            })}
          </div>
        </InnerTile>

        {auth.kind === 'Authenticated' && (
          <InnerTile onClick={() => setShowLogoutDialog(true)}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
              <LogOut size={20} strokeWidth={1.75} style={{ color: 'var(--danger-text)' }} />
              <div>
                <p className="ty-title" style={{ color: 'var(--danger-text)' }}>
                  Desconectar conta
                </p>
                <p className="ty-label" style={{ color: 'var(--muted)' }}>
                  Remove credenciais e cache SIGAA
                </p>
              </div>
            </div>
          </InnerTile>
        )}
      </ModalDialogSheet>

      <ConfirmDialog
        open={showLogoutDialog}
        title="Desconectar conta SIGAA?"
        message="Suas credenciais salvas e o cache local do SIGAA serão removidos deste dispositivo."
        confirmLabel="Desconectar"
        isDestructive
        onCancel={() => setShowLogoutDialog(false)}
        onConfirm={() => {
          setShowLogoutDialog(false);
          onDismiss();
          onLogout();
        }}
      />
    </>
  );
};
