import { GraduationCap, RefreshCw } from 'lucide-react';
import React from 'react';
import type { BoletimSemestre, NotaDisciplinaBoletim } from '../../core/model/models';
import {
  BackHeaderButton,
  EditorialCard,
  EditorialHeader,
  EmptyStateCard,
  ErrorBanner,
  HeaderActionIconButton,
  InnerTile,
  OfflineOriginStrip,
  PreloadStatusBanner,
  SkeletonBox,
  StatusTag,
  type StatusTagTone,
} from '../components/CommonComponents';
import type { AuthUiState, DiscenteUiState } from '../viewmodel/appStores';

interface BoletimScreenProps {
  readonly uiState: DiscenteUiState;
  readonly authUiState: AuthUiState;
  readonly onBack: () => void;
  readonly onRefresh: () => void;
  readonly onOpenLogin: () => void;
  readonly onDismissError: () => void;
}

export const BoletimScreen: React.FC<BoletimScreenProps> = ({
  uiState,
  authUiState,
  onBack,
  onRefresh,
  onOpenLogin,
  onDismissError,
}) => {
  const semestres = uiState.boletim?.value ?? [];
  const isPreloadingBoletim =
    authUiState.preloadProgress.phase === 'RUNNING_ESSENTIAL' ||
    (authUiState.preloadProgress.phase === 'RUNNING_BACKGROUND' &&
      authUiState.preloadProgress.currentTarget === 'Boletim Geral');
  const isLoading =
    uiState.isRefreshingBoletim ||
    authUiState.isSyncingAll ||
    authUiState.isSubmittingLogin ||
    isPreloadingBoletim ||
    !uiState.hasResolvedBoletimCache;

  return (
    <div className="screen-root">
      <EditorialHeader
        title="Boletim & Menções"
        navigationIcon={<BackHeaderButton onClick={onBack} label="Voltar ao perfil" />}
        subtitle={
          <p className="ty-label" style={{ color: 'var(--muted)' }}>
            Histórico por período letivo no SIGAA
          </p>
        }
        actions={
          <HeaderActionIconButton
            icon={RefreshCw}
            label="Atualizar boletim"
            isLoading={uiState.isRefreshingBoletim || isPreloadingBoletim}
            onClick={onRefresh}
          />
        }
      />

      <div className="screen-scroll screen-scroll--subpage">
        {uiState.boletimError && (
          <ErrorBanner
            banner={uiState.boletimError}
            onRetry={uiState.boletimError.canRetry ? onRefresh : null}
            onLoginClick={uiState.boletimError.requiresLogin ? onOpenLogin : null}
            onDismiss={onDismissError}
          />
        )}

        <PreloadStatusBanner progress={authUiState.preloadProgress} />

        {!uiState.boletim && isLoading ? (
          [0, 1].map((i) => (
            <SkeletonBox
              key={i}
              style={{ width: '100%', height: '210px', borderRadius: '16px' }}
            />
          ))
        ) : semestres.length === 0 ? (
          <EmptyStateCard
            icon={GraduationCap}
            title="Boletim não carregado"
            body="Toque em atualizar para buscar menções e notas por semestre do SIGAA."
            actionLabel="Carregar boletim"
            onAction={onRefresh}
          />
        ) : (
          semestres.map((sem, idx) => (
            <BoletimSemestreCard key={`${idx}_${sem.semestre}`} semestre={sem} />
          ))
        )}

        {uiState.boletim && (
          <OfflineOriginStrip
            origin={uiState.boletim.origin}
            syncedAtEpochMillis={uiState.boletim.syncedAtEpochMillis}
          />
        )}
      </div>
    </div>
  );
};

const BoletimSemestreCard: React.FC<{ readonly semestre: BoletimSemestre }> = ({ semestre }) => (
  <EditorialCard style={{ gap: '12px' }}>
    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
      <span className="ty-overline" style={{ color: 'var(--muted)' }}>
        PERÍODO {semestre.semestre}
      </span>
      <span className="ty-label ty-tabular" style={{ color: 'var(--muted)' }}>
        {semestre.disciplinas.length} disciplina(s)
      </span>
    </div>

    <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
      {semestre.disciplinas.map((disc, idx) => (
        <BoletimDisciplinaTile key={`${idx}_${disc.codigo}`} disc={disc} />
      ))}
    </div>
  </EditorialCard>
);

const BoletimDisciplinaTile: React.FC<{ readonly disc: NotaDisciplinaBoletim }> = ({ disc }) => {
  const mencao = disc.resultadoMencao?.trim() ?? '';
  const upper = mencao.toUpperCase();
  let tone: StatusTagTone = 'NEUTRAL';
  if (['SS', 'MS', 'MM'].includes(upper)) tone = 'INFO';
  else if (['MI', 'II', 'SR'].includes(upper)) tone = 'DANGER';
  else if (['TR', 'CC', 'DP'].includes(upper)) tone = 'WARNING';

  const details = [
    disc.nota1?.trim() ? `Nota: ${disc.nota1}` : null,
    disc.faltas !== null ? `Faltas: ${disc.faltas}` : null,
    disc.situacao.trim() || null,
  ].filter(Boolean);

  return (
    <InnerTile>
      <div
        style={{
          display: 'flex',
          alignItems: 'flex-start',
          justifyContent: 'space-between',
          gap: '8px',
        }}
      >
        <div style={{ flex: 1, minWidth: 0 }}>
          <p className="ty-mono" style={{ color: 'var(--muted)' }}>
            {disc.codigo}
          </p>
          <p className="ty-title" style={{ color: 'var(--ink)', marginTop: '2px' }}>
            {disc.disciplina}
          </p>
        </div>
        {mencao && <StatusTag text={mencao} tone={tone} />}
      </div>

      {details.length > 0 && (
        <p className="ty-label ty-tabular" style={{ color: 'var(--muted)', marginTop: '6px' }}>
          {details.join(' · ')}
        </p>
      )}
    </InnerTile>
  );
};
