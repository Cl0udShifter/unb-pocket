import {
  Bell,
  Calendar,
  CalendarPlus,
  ChevronDown,
  ChevronRight,
  ChevronUp,
  Clock,
  GraduationCap,
  Lock,
  MapPin,
  RefreshCw,
  User,
  UserCheck,
} from 'lucide-react';
import React, { useMemo } from 'react';
import {
  DIA_SEMANA_SIGAA_BY_ID,
  DIA_SEMANA_SIGAA_LIST,
  type AtualizacaoTurmaFeed,
  type DiaSemanaSigaa,
  type TurmaResumo,
} from '../../core/model/models';
import { IcsCalendarExporter } from '../../core/parser/icsCalendarExporter';
import {
  Formatters,
  type FocusClassState,
  type ScheduledClassItem,
} from '../common/formatters';
import {
  EditorialCard,
  EditorialHeader,
  EmptyStateCard,
  ErrorBanner,
  HairlineDivider,
  HeaderActionIconButton,
  OfflineOriginStrip,
  PreloadStatusBanner,
  SegmentedPillBar,
  SkeletonBox,
  StatusTag,
  UnbPullToRefreshBox,
} from '../components/CommonComponents';
import type {
  AuthUiState,
  TurmasListTab,
  TurmasUiState,
} from '../viewmodel/appStores';

const LIST_TABS: readonly { id: TurmasListTab; label: string }[] = [
  { id: 'AULAS_DO_DIA', label: 'Aulas do Dia' },
  { id: 'TURMAS_SEMESTRE', label: 'Turmas' },
  { id: 'ATUALIZACOES', label: 'Avisos' },
];

interface TurmasScreenProps {
  readonly uiState: TurmasUiState;
  readonly authUiState: AuthUiState;
  readonly onSelectListTab: (tab: TurmasListTab) => void;
  readonly onSelectDay: (day: DiaSemanaSigaa) => void;
  readonly onToggleExpandClass: (frontEndIdTurma: string) => void;
  readonly onOpenTurma: (frontEndIdTurma: string) => void;
  readonly onRefresh: () => void;
  readonly onOpenLogin: () => void;
  readonly onOpenAccountSheet: () => void;
  readonly onOpenNotifications: () => void;
  readonly onDismissError: () => void;
}

const EMPTY_TURMAS: readonly TurmaResumo[] = [];

export const TurmasScreen: React.FC<TurmasScreenProps> = ({
  uiState,
  authUiState,
  onSelectListTab,
  onSelectDay,
  onToggleExpandClass,
  onOpenTurma,
  onRefresh,
  onOpenLogin,
  onOpenAccountSheet,
  onOpenNotifications,
  onDismissError,
}) => {
  const turmasList = uiState.turmas?.value ?? EMPTY_TURMAS;
  const atualizacoes = uiState.atualizacoes?.value ?? [];
  const semestreHeader = turmasList.find((t) => t.semestre.trim().length > 0)?.semestre ?? null;
  const isAuthenticated = uiState.authState.kind === 'Authenticated';

  const isEssentialSyncing =
    authUiState.isSubmittingLogin ||
    authUiState.isSyncingAll ||
    uiState.authState.kind === 'Reauthenticating' ||
    authUiState.preloadProgress.phase === 'RUNNING_ESSENTIAL';

  const isLoadingList =
    uiState.isRefreshingList || isEssentialSyncing || !uiState.hasResolvedInitialCache;

  const hasOfflineOrAuthData =
    turmasList.length > 0 ||
    isAuthenticated ||
    isEssentialSyncing ||
    !uiState.hasResolvedInitialCache;

  const nowMinutes = Formatters.nowMinutesInBrasilia();
  const dayClasses = useMemo(
    () => Formatters.classesForDay(turmasList, uiState.selectedDay),
    [turmasList, uiState.selectedDay],
  );
  const focusClass = useMemo(
    () =>
      Formatters.resolveFocusClass(
        dayClasses,
        uiState.selectedDay === uiState.todayDay,
        nowMinutes,
      ),
    [dayClasses, uiState.selectedDay, uiState.todayDay, nowMinutes],
  );

  const selectedDayMeta = DIA_SEMANA_SIGAA_BY_ID[uiState.selectedDay];
  const subtitleText = semestreHeader
    ? `Semestre ${semestreHeader}`
    : isAuthenticated || isEssentialSyncing
      ? 'Semestre Atual · SIGAA'
      : 'Conecte sua conta SIGAA para sincronizar';

  return (
    <div className="screen-root">
      <EditorialHeader
        title="Aulas & Turmas"
        subtitle={
          <p className="ty-label" style={{ color: 'var(--muted)' }}>
            {subtitleText}
          </p>
        }
        actions={
          <>
            {hasOfflineOrAuthData && (
              <HeaderActionIconButton
                icon={RefreshCw}
                label="Atualizar turmas"
                isLoading={uiState.isRefreshingList || isEssentialSyncing}
                onClick={onRefresh}
              />
            )}
            <HeaderActionIconButton
              icon={Bell}
              label="Notificações de Turmas"
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

      {!hasOfflineOrAuthData ? (
        <div className="screen-scroll">
          {uiState.listError && (
            <ErrorBanner
              banner={uiState.listError}
              onRetry={uiState.listError.canRetry ? onRefresh : null}
              onDismiss={onDismissError}
            />
          )}
          <EmptyStateCard
            icon={Lock}
            title="Entre com sua conta do SIGAA"
            body="Conecte sua matrícula UnB para acompanhar seu horário semanal oficial, aulas do dia, materiais, frequências e avisos das turmas."
            actionLabel="Entrar no SIGAA"
            onAction={onOpenLogin}
          />
        </div>
      ) : (
        <UnbPullToRefreshBox
          isRefreshing={uiState.isRefreshingList || isEssentialSyncing}
          onRefresh={onRefresh}
        >
          {uiState.listError && (
              <ErrorBanner
                banner={uiState.listError}
                onRetry={uiState.listError.canRetry ? onRefresh : null}
                onLoginClick={uiState.listError.requiresLogin ? onOpenLogin : null}
                onDismiss={onDismissError}
              />
            )}

            <PreloadStatusBanner progress={authUiState.preloadProgress} />

            <SegmentedPillBar
              options={LIST_TABS.map((t) => t.label)}
              selectedIndex={LIST_TABS.findIndex((t) => t.id === uiState.selectedListTab)}
              onSelect={(idx) => onSelectListTab(LIST_TABS[idx].id)}
            />

            {uiState.selectedListTab === 'AULAS_DO_DIA' && (
              <>
                <SigaaDaySelectorRow
                  selectedDay={uiState.selectedDay}
                  todayDay={uiState.todayDay}
                  onSelectDay={onSelectDay}
                />

                {!uiState.turmas && isLoadingList ? (
                  <>
                    <SkeletonBox style={{ width: '100%', height: '146px', borderRadius: '16px' }} />
                    <SkeletonBox style={{ width: '124px', height: '11px', borderRadius: '4px' }} />
                    <SkeletonBox style={{ width: '100%', height: '104px', borderRadius: '16px' }} />
                    <SkeletonBox style={{ width: '100%', height: '104px', borderRadius: '16px' }} />
                  </>
                ) : turmasList.length === 0 ? (
                  <EmptyStateCard
                    icon={Calendar}
                    title="Nenhuma turma encontrada"
                    body="Não há disciplinas ativas registradas para o semestre atual."
                    actionLabel="Sincronizar SIGAA"
                    onAction={onRefresh}
                  />
                ) : dayClasses.length === 0 ? (
                  <EmptyStateCard
                    icon={Clock}
                    title={`Dia livre (${selectedDayMeta.sigla})`}
                    body={`Você não possui aulas presenciais com horário marcado para ${selectedDayMeta.nomeCompleto.toLowerCase()}.`}
                    actionLabel="Ver todas as turmas"
                    onAction={() => onSelectListTab('TURMAS_SEMESTRE')}
                  />
                ) : (
                  <>
                    {focusClass && (
                      <FocusClassHeroCard
                        focus={focusClass}
                        onOpenTurma={() => onOpenTurma(focusClass.item.turma.frontEndIdTurma)}
                      />
                    )}

                    <p
                      className="ty-overline"
                      style={{ color: 'var(--muted)', paddingTop: '4px' }}
                    >
                      GRADE DE {selectedDayMeta.nomeCompleto.toUpperCase()}
                    </p>

                    {dayClasses.map((scheduled, idx) => (
                      <ScheduledClassRowCard
                        key={`${idx}_${scheduled.turma.frontEndIdTurma}_${scheduled.startMinutes}`}
                        scheduled={scheduled}
                        isExpanded={
                          uiState.expandedClassTurmaId === scheduled.turma.frontEndIdTurma
                        }
                        onToggleExpand={() =>
                          onToggleExpandClass(scheduled.turma.frontEndIdTurma)
                        }
                        onOpenTurma={() => onOpenTurma(scheduled.turma.frontEndIdTurma)}
                      />
                    ))}
                  </>
                )}
              </>
            )}

            {uiState.selectedListTab === 'TURMAS_SEMESTRE' && (
              <>
                {!uiState.turmas && isLoadingList ? (
                  [0, 1, 2].map((i) => (
                    <SkeletonBox
                      key={i}
                      style={{ width: '100%', height: '124px', borderRadius: '16px' }}
                    />
                  ))
                ) : turmasList.length === 0 ? (
                  <EmptyStateCard
                    icon={GraduationCap}
                    title="Sem turmas no semestre"
                    body="Toque em atualizar para sincronizar as turmas do Portal do Discente."
                    actionLabel="Atualizar turmas"
                    onAction={onRefresh}
                  />
                ) : (
                  <>
                    {turmasList.map((turma, idx) => (
                      <SemesterTurmaCard
                        key={`${idx}_${turma.frontEndIdTurma}`}
                        turma={turma}
                        onClick={() => onOpenTurma(turma.frontEndIdTurma)}
                      />
                    ))}

                    <button
                      type="button"
                      className="btn-secondary"
                      style={{ width: '100%' }}
                      onClick={() => {
                        const ics = IcsCalendarExporter.buildClassesScheduleIcs(
                          turmasList,
                          authUiState.sigaaNotificacoes.antecedenciaAulaMinutos,
                        );
                        const blob = new Blob([ics], { type: 'text/calendar;charset=utf-8' });
                        const url = URL.createObjectURL(blob);
                        const a = document.createElement('a');
                        a.href = url;
                        a.download = 'grade-semestre-unb.ics';
                        document.body.appendChild(a);
                        a.click();
                        a.remove();
                        setTimeout(() => URL.revokeObjectURL(url), 15_000);
                      }}
                    >
                      <CalendarPlus size={17} strokeWidth={1.75} />
                      <span>Exportar grade para Calendário (.ics)</span>
                    </button>
                  </>
                )}
              </>
            )}

            {uiState.selectedListTab === 'ATUALIZACOES' && (
              <>
                {!uiState.atualizacoes && isLoadingList ? (
                  [0, 1, 2].map((i) => (
                    <SkeletonBox
                      key={i}
                      style={{ width: '100%', height: '94px', borderRadius: '16px' }}
                    />
                  ))
                ) : atualizacoes.length === 0 ? (
                  <EmptyStateCard
                    icon={Bell}
                    title="Sem atualizações recentes"
                    body="Quando docentes publicarem notícias ou arquivos nas suas turmas, eles aparecerão aqui."
                    actionLabel="Atualizar"
                    onAction={onRefresh}
                  />
                ) : (
                  atualizacoes.map((update, idx) => (
                    <TurmaFeedUpdateCard
                      key={`${idx}_${update.idTurma}_${update.dataTexto}`}
                      item={update}
                      onClick={() => {
                        const match = turmasList.find(
                          (t) =>
                            t.idTurma === update.idTurma ||
                            t.frontEndIdTurma === update.idTurma,
                        );
                        const targetId = match?.frontEndIdTurma ?? update.idTurma;
                        if (targetId) onOpenTurma(targetId);
                      }}
                    />
                  ))
                )}
              </>
            )}

            {uiState.turmas && (
              <OfflineOriginStrip
                origin={uiState.turmas.origin}
                syncedAtEpochMillis={uiState.turmas.syncedAtEpochMillis}
              />
            )}
        </UnbPullToRefreshBox>
      )}
    </div>
  );
};

const SigaaDaySelectorRow: React.FC<{
  readonly selectedDay: DiaSemanaSigaa;
  readonly todayDay: DiaSemanaSigaa;
  readonly onSelectDay: (day: DiaSemanaSigaa) => void;
}> = ({ selectedDay, todayDay, onSelectDay }) => (
  <div
    role="tablist"
    aria-label="Dia da semana"
    style={{
      display: 'grid',
      gridTemplateColumns: 'repeat(6, minmax(0, 1fr))',
      gap: '6px',
      width: '100%',
    }}
  >
    {DIA_SEMANA_SIGAA_LIST.map((day) => {
      const selected = day.id === selectedDay;
      const isToday = day.id === todayDay;
      return (
        <button
          key={day.id}
          type="button"
          role="tab"
          aria-selected={selected}
          onClick={() => onSelectDay(day.id)}
          style={{
            minHeight: '56px',
            borderRadius: 'var(--radius-inner)',
            backgroundColor: selected ? 'var(--pill-active-bg)' : 'var(--surface)',
            border: `1px solid ${selected ? 'var(--pill-active-bg)' : 'var(--border)'}`,
            color: selected ? 'var(--pill-active-ink)' : 'var(--ink)',
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            justifyContent: 'center',
            gap: '5px',
          }}
        >
          <span className="ty-label">{day.sigla}</span>
          <span
            style={{
              width: '5px',
              height: '5px',
              borderRadius: '50%',
              backgroundColor:
                isToday && selected
                  ? 'var(--pill-active-ink)'
                  : isToday
                    ? 'var(--ink)'
                    : 'transparent',
            }}
          />
        </button>
      );
    })}
  </div>
);

const FocusClassHeroCard: React.FC<{
  readonly focus: FocusClassState;
  readonly onOpenTurma: () => void;
}> = ({ focus, onOpenTurma }) => {
  const turma = focus.item.turma;
  const rawDocente = turma.docenteNome?.trim();
  const footerLeft = rawDocente
    ? /^prof\b/i.test(rawDocente)
      ? rawDocente
      : `Prof. ${rawDocente}`
    : turma.numeroTurma?.trim()
      ? `Turma ${turma.numeroTurma}`
      : '';

  return (
    <button
      type="button"
      onClick={onOpenTurma}
      style={{
        width: '100%',
        borderRadius: 'var(--radius-card)',
        backgroundColor: 'var(--pill-active-bg)',
        color: 'var(--pill-active-ink)',
        padding: '16px',
        display: 'flex',
        flexDirection: 'column',
        gap: '8px',
        textAlign: 'left',
      }}
    >
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: '8px',
          width: '100%',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px', flex: 1 }}>
          {focus.isLiveNow && (
            <span
              style={{
                width: '8px',
                height: '8px',
                borderRadius: '50%',
                backgroundColor: 'var(--pill-active-ink)',
              }}
            />
          )}
          <span className="ty-label ty-tabular" style={{ opacity: 0.86 }}>
            {focus.statusLabel}
          </span>
        </div>
        {turma.codigoDisciplina && (
          <span className="ty-mono" style={{ opacity: 0.8 }}>
            {turma.codigoDisciplina}
          </span>
        )}
      </div>

      <p className="ty-title-strong">{turma.nomeDisciplina}</p>

      <div style={{ display: 'flex', alignItems: 'center', gap: '6px', opacity: 0.86 }}>
        <MapPin size={16} strokeWidth={1.75} style={{ flexShrink: 0 }} />
        <span className="ty-body">{turma.local.trim() || 'Local não informado'}</span>
      </div>

      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: '8px',
          marginTop: '4px',
          width: '100%',
        }}
      >
        <span
          className="ty-label"
          style={{
            opacity: 0.78,
            whiteSpace: 'nowrap',
            overflow: 'hidden',
            textOverflow: 'ellipsis',
          }}
        >
          {footerLeft}
        </span>
        <span style={{ display: 'inline-flex', alignItems: 'center', gap: '2px' }} className="ty-label">
          <span>Turma Virtual</span>
          <ChevronRight size={17} strokeWidth={1.75} />
        </span>
      </div>
    </button>
  );
};

const ScheduledClassRowCard: React.FC<{
  readonly scheduled: ScheduledClassItem;
  readonly isExpanded: boolean;
  readonly onToggleExpand: () => void;
  readonly onOpenTurma: () => void;
}> = ({ scheduled, isExpanded, onToggleExpand, onOpenTurma }) => {
  const turma = scheduled.turma;
  return (
    <EditorialCard onClick={onOpenTurma} style={{ gap: '6px' }}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
          <Clock size={16} strokeWidth={1.75} style={{ color: 'var(--muted)' }} />
          <span className="ty-body-strong ty-tabular" style={{ color: 'var(--ink)' }}>
            {scheduled.startText} - {scheduled.endText}
          </span>
        </div>

        <button
          type="button"
          aria-label={isExpanded ? 'Recolher detalhes' : 'Expandir detalhes'}
          onClick={(e) => {
            e.stopPropagation();
            onToggleExpand();
          }}
          style={{
            width: '40px',
            height: '40px',
            display: 'inline-flex',
            alignItems: 'center',
            justifyContent: 'center',
            color: 'var(--muted)',
          }}
        >
          {isExpanded ? <ChevronUp size={18} strokeWidth={1.75} /> : <ChevronDown size={18} strokeWidth={1.75} />}
        </button>
      </div>

      <p className="ty-title-strong" style={{ color: 'var(--ink)' }}>
        {turma.nomeDisciplina}
      </p>

      <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
        <MapPin size={16} strokeWidth={1.75} style={{ color: 'var(--muted)', flexShrink: 0 }} />
        <span className="ty-body" style={{ color: 'var(--muted)' }}>
          {turma.local.trim() || 'Local a definir no SIGAA'}
        </span>
      </div>

      {isExpanded && (
        <div
          style={{ display: 'flex', flexDirection: 'column', gap: '10px', marginTop: '8px' }}
          onClick={(e) => e.stopPropagation()}
        >
          <HairlineDivider />
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: '6px' }}>
            {turma.codigoDisciplina && <StatusTag text={turma.codigoDisciplina} />}
            {turma.numeroTurma && <StatusTag text={`Turma ${turma.numeroTurma}`} />}
            {turma.codigoHorarioRaw && <StatusTag text={turma.codigoHorarioRaw} />}
          </div>
          {turma.docenteNome && (
            <p className="ty-label" style={{ color: 'var(--muted)' }}>
              Docente: {turma.docenteNome}
            </p>
          )}
          <button type="button" className="btn-tonal" style={{ width: '100%' }} onClick={onOpenTurma}>
            Abrir Turma Virtual
          </button>
        </div>
      )}
    </EditorialCard>
  );
};

const SemesterTurmaCard: React.FC<{
  readonly turma: TurmaResumo;
  readonly onClick: () => void;
}> = ({ turma, onClick }) => {
  const scheduleDisplay =
    turma.horarioExtenso.length > 0
      ? turma.horarioExtenso.join(' · ')
      : turma.codigoHorarioRaw.trim() || null;

  return (
    <EditorialCard onClick={onClick} style={{ gap: '8px' }}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px', flexWrap: 'wrap' }}>
          {turma.codigoDisciplina && <StatusTag text={turma.codigoDisciplina} />}
          {turma.numeroTurma && <StatusTag text={`T${turma.numeroTurma}`} />}
          {turma.cargaHorariaHoras !== null && (
            <span className="ty-mono" style={{ color: 'var(--muted)' }}>
              {turma.cargaHorariaHoras}h
            </span>
          )}
        </div>
        <ChevronRight size={19} strokeWidth={1.75} style={{ color: 'var(--muted)' }} />
      </div>

      <p className="ty-title-strong" style={{ color: 'var(--ink)' }}>
        {turma.nomeDisciplina}
      </p>

      {turma.docenteNome && (
        <p className="ty-label" style={{ color: 'var(--muted)' }}>
          {turma.docenteNome}
        </p>
      )}

      {(scheduleDisplay || turma.local.trim()) && (
        <>
          <HairlineDivider />
          {scheduleDisplay && (
            <p className="ty-label ty-tabular" style={{ color: 'var(--ink)' }}>
              {scheduleDisplay}
            </p>
          )}
          {turma.local.trim() && (
            <p className="ty-label" style={{ color: 'var(--muted)' }}>
              {turma.local}
            </p>
          )}
        </>
      )}
    </EditorialCard>
  );
};

const TurmaFeedUpdateCard: React.FC<{
  readonly item: AtualizacaoTurmaFeed;
  readonly onClick: () => void;
}> = ({ item, onClick }) => (
  <EditorialCard onClick={onClick} style={{ gap: '6px' }}>
    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: '8px' }}>
      <span className="ty-label ty-tabular" style={{ color: 'var(--muted)' }}>
        {item.dataTexto}
      </span>
      <ChevronRight size={18} strokeWidth={1.75} style={{ color: 'var(--muted)' }} />
    </div>
    <p className="ty-label" style={{ color: 'var(--muted)' }}>
      {item.disciplinaSemestre}
    </p>
    <p className="ty-title" style={{ color: 'var(--ink)' }}>
      {item.tituloNoticia}
    </p>
  </EditorialCard>
);
