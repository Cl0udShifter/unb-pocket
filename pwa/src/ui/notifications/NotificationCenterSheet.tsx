import {
  BellRing,
  CalendarPlus,
  Check,
  ChevronDown,
  ChevronUp,
  GraduationCap,
  RefreshCw,
  User,
  Utensils,
} from 'lucide-react';
import React, { useState } from 'react';
import {
  isNotificationPermitted,
  requestBrowserNotificationPermission,
} from '../../core/data/notification/pwaNotificationCoordinator';
import {
  AULA_LEAD_MINUTES_OPTIONS,
  LIMITE_REFEICOES_OPCOES,
  SIGAA_SYNC_INTERVAL_OPTIONS,
  SigaaNotificacaoHelpers,
  TIPO_REFEICAO_LIST,
  hasAnyRuSlotSelected,
  type CampusRu,
  type DiaSemanaRu,
  type RuNotificacaoConfig,
  type SigaaNotificacaoConfig,
  type SigaaSyncIntervalHours,
  type TipoRefeicao,
  type TurmaResumo,
} from '../../core/model/models';
import { IcsCalendarExporter } from '../../core/parser/icsCalendarExporter';
import {
  EditorialCard,
  EditorialSwitch,
  HairlineDivider,
  InnerTile,
} from '../components/CommonComponents';
import { ModalDialogSheet } from '../components/ModalDialogSheet';
import { RuNotificationScheduleGrid } from '../ru/RuScreen';

export type NotificationCenterSection = 'RU' | 'TURMAS' | 'DISCENTE';

function downloadIcsFile(filename: string, content: string): void {
  const blob = new Blob([content], { type: 'text/calendar;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 15_000);
}

interface NotificationCenterSheetProps {
  readonly open: boolean;
  readonly initialSection: NotificationCenterSection | null;
  readonly campusRu: CampusRu;
  readonly ruNotificacoes: RuNotificacaoConfig;
  readonly sigaaNotificacoes: SigaaNotificacaoConfig;
  readonly turmas: readonly TurmaResumo[];
  readonly onSetRuHabilitadas: (enabled: boolean) => void;
  readonly onToggleRuSlot: (dia: DiaSemanaRu, refeicao: TipoRefeicao) => void;
  readonly onToggleRuDay: (dia: DiaSemanaRu) => void;
  readonly onToggleSigaaTurmasSecao: (enabled: boolean) => void;
  readonly onSetAulasLembrete: (enabled: boolean) => void;
  readonly onSetAntecedenciaAulaMinutos: (minutes: number) => void;
  readonly onSetAvisosEMateriaisNovos: (enabled: boolean) => void;
  readonly onSetProvasETarefasLembrete: (enabled: boolean) => void;
  readonly onSetNotasParciaisAlteradas: (enabled: boolean) => void;
  readonly onSetRiscoFaltaAlerta: (enabled: boolean) => void;
  readonly onToggleTurmaSilenciada: (turma: TurmaResumo) => void;
  readonly onToggleSigaaDiscenteSecao: (enabled: boolean) => void;
  readonly onSetSaldoRuBaixoAlerta: (enabled: boolean) => void;
  readonly onSetLimiteRefeicoesRuBaixo: (meals: number) => void;
  readonly onSetExtratoRuMovimentacaoAlerta: (enabled: boolean) => void;
  readonly onSetBoletimAlteradoAlerta: (enabled: boolean) => void;
  readonly onSetIndicesAlteradosAlerta: (enabled: boolean) => void;
  readonly onSetSyncInterval: (interval: SigaaSyncIntervalHours) => void;
  readonly onSetSomenteWifi: (onlyWifi: boolean) => void;
  readonly onResyncSchedules: () => void;
  readonly onDismiss: () => void;
}

export const NotificationCenterSheet: React.FC<NotificationCenterSheetProps> = ({
  open,
  initialSection,
  campusRu,
  ruNotificacoes,
  sigaaNotificacoes,
  turmas,
  onSetRuHabilitadas,
  onToggleRuSlot,
  onToggleRuDay,
  onToggleSigaaTurmasSecao,
  onSetAulasLembrete,
  onSetAntecedenciaAulaMinutos,
  onSetAvisosEMateriaisNovos,
  onSetProvasETarefasLembrete,
  onSetNotasParciaisAlteradas,
  onSetRiscoFaltaAlerta,
  onToggleTurmaSilenciada,
  onToggleSigaaDiscenteSecao,
  onSetSaldoRuBaixoAlerta,
  onSetLimiteRefeicoesRuBaixo,
  onSetExtratoRuMovimentacaoAlerta,
  onSetBoletimAlteradoAlerta,
  onSetIndicesAlteradosAlerta,
  onSetSyncInterval,
  onSetSomenteWifi,
  onResyncSchedules,
  onDismiss,
}) => {
  const [expandedRu, setExpandedRu] = useState(
    initialSection === 'RU' || (initialSection === null && ruNotificacoes.habilitado),
  );
  const [expandedTurmas, setExpandedTurmas] = useState(
    initialSection === 'TURMAS' ||
      (initialSection === null &&
        (SigaaNotificacaoHelpers.isTurmasAtivo(sigaaNotificacoes) ||
          !sigaaNotificacoes.habilitado)),
  );
  const [expandedDiscente, setExpandedDiscente] = useState(
    initialSection === 'DISCENTE' ||
      (initialSection === null && SigaaNotificacaoHelpers.isDiscenteAtivo(sigaaNotificacoes)),
  );

  const [permissionStatus, setPermissionStatus] = useState<string>(() =>
    typeof Notification === 'undefined' ? 'unsupported' : Notification.permission,
  );

  const runWithPermission = (action: () => void): void => {
    if (isNotificationPermitted() || typeof Notification === 'undefined') {
      action();
      return;
    }
    void requestBrowserNotificationPermission().then((res) => {
      setPermissionStatus(res);
      action();
      if (res === 'granted') {
        onResyncSchedules();
      }
    });
  };

  const sheetTitle =
    initialSection === 'RU'
      ? 'Notificações do RU'
      : initialSection === 'TURMAS'
        ? 'Notificações · Turmas'
        : initialSection === 'DISCENTE'
          ? 'Notificações · Discente'
          : 'Central de Notificações';

  const sheetSubtitle =
    initialSection === 'RU'
      ? 'Receba avisos 1h antes das refeições selecionadas.'
      : initialSection === 'TURMAS'
        ? 'Lembretes de aulas, avisos, materiais, provas, notas e faltas.'
        : initialSection === 'DISCENTE'
          ? 'Alertas de saldo RU, extrato, boletim e índices acadêmicos.'
          : 'Lembretes de cardápio, turmas e painel discente.';

  const visibleSections: readonly NotificationCenterSection[] = initialSection
    ? [initialSection]
    : ['RU', 'TURMAS', 'DISCENTE'];
  const isScoped = initialSection !== null;

  return (
    <ModalDialogSheet
      open={open}
      onClose={onDismiss}
      title={sheetTitle}
      subtitle={sheetSubtitle}
      headerRight={<BellRing size={20} strokeWidth={1.75} style={{ color: 'var(--ink)' }} />}
    >
      {permissionStatus !== 'granted' && (
        <InnerTile
          style={{
            backgroundColor: 'var(--warning-bg)',
            borderColor: 'var(--warning-border)',
            gap: '6px',
          }}
        >
          <p className="ty-title-strong" style={{ color: 'var(--warning-ink)' }}>
            {permissionStatus === 'unsupported'
              ? 'Dica para iPhone e iPad (Tela de Início)'
              : 'Permissão de notificações do navegador'}
          </p>
          <p className="ty-label" style={{ color: 'var(--warning-ink)' }}>
            {permissionStatus === 'unsupported'
              ? 'Adicione o UnB Pocket à Tela de Início no Safari (iOS 16.4+) ou exporte os lembretes em .ics para receber alarmes exatos no Calendário da Apple.'
              : 'Autorize notificações para exibir alertas ao sincronizar turmas, notas e cardápio, ou exporte a grade para o Calendário nativo do iOS.'}
          </p>
          {permissionStatus !== 'unsupported' && permissionStatus !== 'denied' && (
            <div>
              <button
                type="button"
                className="ty-label"
                style={{
                  color: 'var(--ink)',
                  padding: '6px 10px',
                  borderRadius: 'var(--radius-tag)',
                  backgroundColor: 'var(--surface)',
                }}
                onClick={() => runWithPermission(() => undefined)}
              >
                Permitir notificações agora
              </button>
            </div>
          )}
        </InnerTile>
      )}

      {visibleSections.map((section) => {
        if (section === 'RU') {
          const isOpen = isScoped ? true : expandedRu;
          return (
            <SectionCard
              key="RU"
              Icon={Utensils}
              title="Cardápio do RU"
              subtitle="Café (06h), Almoço (10h) e Jantar (16h)"
              checked={ruNotificacoes.habilitado}
              expanded={isOpen}
              collapsible={!isScoped}
              onToggleExpand={() => setExpandedRu((v) => !v)}
              onCheckedChange={(enabled) => {
                if (enabled) {
                  runWithPermission(() => {
                    onSetRuHabilitadas(true);
                    setExpandedRu(true);
                  });
                } else {
                  onSetRuHabilitadas(false);
                }
              }}
            >
              <RuNotificationScheduleGrid
                notificacoes={ruNotificacoes}
                onToggleSlot={(dia, refeicao) => {
                  const already = (ruNotificacoes.refeicoesPorDia[dia] ?? []).includes(refeicao);
                  if (!already || !ruNotificacoes.habilitado) {
                    runWithPermission(() => onToggleRuSlot(dia, refeicao));
                  } else {
                    onToggleRuSlot(dia, refeicao);
                  }
                }}
                onToggleDay={(dia) => {
                  const all =
                    (ruNotificacoes.refeicoesPorDia[dia] ?? []).length ===
                    TIPO_REFEICAO_LIST.length;
                  if (!all || !ruNotificacoes.habilitado) {
                    runWithPermission(() => onToggleRuDay(dia));
                  } else {
                    onToggleRuDay(dia);
                  }
                }}
              />

              {hasAnyRuSlotSelected(ruNotificacoes) && (
                <button
                  type="button"
                  className="btn-secondary"
                  style={{ width: '100%', marginTop: '8px' }}
                  onClick={() => {
                    const ics = IcsCalendarExporter.buildRuMealsScheduleIcs(
                      ruNotificacoes,
                      campusRu,
                    );
                    downloadIcsFile('lembretes-ru-unb.ics', ics);
                  }}
                >
                  <CalendarPlus size={17} strokeWidth={1.75} />
                  <span>Adicionar lembretes ao Calendário (iOS / .ics)</span>
                </button>
              )}
            </SectionCard>
          );
        }

        if (section === 'TURMAS') {
          const turmasChecked =
            sigaaNotificacoes.habilitado && sigaaNotificacoes.turmasHabilitado;
          const isOpen = isScoped ? true : expandedTurmas;

          return (
            <SectionCard
              key="TURMAS"
              Icon={GraduationCap}
              title="Aulas & Turmas"
              subtitle="Horários, avisos, materiais, provas, notas e faltas"
              checked={turmasChecked}
              expanded={isOpen}
              collapsible={!isScoped}
              onToggleExpand={() => setExpandedTurmas((v) => !v)}
              onCheckedChange={(enabled) => {
                if (enabled) {
                  runWithPermission(() => {
                    onToggleSigaaTurmasSecao(true);
                    setExpandedTurmas(true);
                  });
                } else {
                  onToggleSigaaTurmasSecao(false);
                }
              }}
            >
              <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                <SubToggleRow
                  title="Próximas aulas e sala"
                  subtitle="Usa a grade local em cache sem precisar de internet"
                  checked={sigaaNotificacoes.aulasLembrete}
                  disabled={!turmasChecked}
                  onChange={(v) =>
                    v ? runWithPermission(() => onSetAulasLembrete(true)) : onSetAulasLembrete(false)
                  }
                />

                {turmasChecked && sigaaNotificacoes.aulasLembrete && (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
                    <span className="ty-overline" style={{ color: 'var(--muted)' }}>
                      ANTECEDÊNCIA DO LEMBRETE
                    </span>
                    <div style={{ display: 'flex', flexWrap: 'wrap', gap: '8px' }}>
                      {AULA_LEAD_MINUTES_OPTIONS.map((opt) => (
                        <OptionChip
                          key={opt.minutes}
                          label={opt.label}
                          selected={sigaaNotificacoes.antecedenciaAulaMinutos === opt.minutes}
                          onClick={() => onSetAntecedenciaAulaMinutos(opt.minutes)}
                        />
                      ))}
                    </div>
                  </div>
                )}

                {turmas.length > 0 && (
                  <button
                    type="button"
                    className="btn-secondary"
                    style={{ width: '100%' }}
                    onClick={() => {
                      const activeTurmas = turmas.filter(
                        (t) =>
                          !sigaaNotificacoes.turmasSilenciadas.includes(t.frontEndIdTurma) &&
                          !sigaaNotificacoes.turmasSilenciadas.includes(t.idTurma),
                      );
                      const ics = IcsCalendarExporter.buildClassesScheduleIcs(
                        activeTurmas,
                        sigaaNotificacoes.antecedenciaAulaMinutos,
                      );
                      downloadIcsFile('grade-aulas-unb.ics', ics);
                    }}
                  >
                    <CalendarPlus size={17} strokeWidth={1.75} />
                    <span>Exportar grade com alarmes para Calendário (.ics)</span>
                  </button>
                )}

                <HairlineDivider />
                <SubToggleRow
                  title="Novos avisos e materiais"
                  subtitle="Comunicados do professor e arquivos nos tópicos"
                  checked={sigaaNotificacoes.avisosEMateriaisNovos}
                  disabled={!turmasChecked}
                  onChange={(v) =>
                    v
                      ? runWithPermission(() => onSetAvisosEMateriaisNovos(true))
                      : onSetAvisosEMateriaisNovos(false)
                  }
                />

                <HairlineDivider />
                <SubToggleRow
                  title="Provas e entregas de tarefas"
                  subtitle="Lembra às 20h na véspera e às 07h ou 1h antes no dia"
                  checked={sigaaNotificacoes.provasETarefasLembrete}
                  disabled={!turmasChecked}
                  onChange={(v) =>
                    v
                      ? runWithPermission(() => onSetProvasETarefasLembrete(true))
                      : onSetProvasETarefasLembrete(false)
                  }
                />

                <HairlineDivider />
                <SubToggleRow
                  title="Notas parciais postadas"
                  subtitle="Lançamentos e atualizações por unidade no AVA"
                  checked={sigaaNotificacoes.notasParciaisAlteradas}
                  disabled={!turmasChecked}
                  onChange={(v) =>
                    v
                      ? runWithPermission(() => onSetNotasParciaisAlteradas(true))
                      : onSetNotasParciaisAlteradas(false)
                  }
                />

                <HairlineDivider />
                <SubToggleRow
                  title="Risco de reprovação por faltas"
                  subtitle="Alerta quando restarem poucas faltas ou ao atingir risco"
                  checked={sigaaNotificacoes.riscoFaltaAlerta}
                  disabled={!turmasChecked}
                  onChange={(v) =>
                    v
                      ? runWithPermission(() => onSetRiscoFaltaAlerta(true))
                      : onSetRiscoFaltaAlerta(false)
                  }
                />

                {turmas.length > 0 && (
                  <>
                    <HairlineDivider />
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
                      <span className="ty-overline" style={{ color: 'var(--muted)' }}>
                        TURMAS MONITORADAS
                      </span>
                      <p className="ty-label" style={{ color: 'var(--muted)' }}>
                        Desmarque alguma disciplina se não quiser receber alertas dela.
                      </p>
                      <div style={{ display: 'flex', flexWrap: 'wrap', gap: '8px' }}>
                        {turmas.map((turma) => {
                          const isMuted =
                            sigaaNotificacoes.turmasSilenciadas.includes(turma.frontEndIdTurma) ||
                            sigaaNotificacoes.turmasSilenciadas.includes(turma.idTurma);
                          const shortLabel =
                            turma.codigoDisciplina?.trim() || turma.nomeDisciplina.slice(0, 18);
                          return (
                            <OptionChip
                              key={turma.frontEndIdTurma}
                              label={shortLabel}
                              selected={!isMuted}
                              showCheck={!isMuted}
                              onClick={() => onToggleTurmaSilenciada(turma)}
                            />
                          );
                        })}
                      </div>
                    </div>
                  </>
                )}
              </div>
            </SectionCard>
          );
        }

        const discenteChecked =
          sigaaNotificacoes.habilitado && sigaaNotificacoes.discenteHabilitado;
        const isOpen = isScoped ? true : expandedDiscente;

        return (
          <SectionCard
            key="DISCENTE"
            Icon={User}
            title="Painel Discente"
            subtitle="Saldo baixo RU, extrato, boletim e índices (IRA/MP)"
            checked={discenteChecked}
            expanded={isOpen}
            collapsible={!isScoped}
            onToggleExpand={() => setExpandedDiscente((v) => !v)}
            onCheckedChange={(enabled) => {
              if (enabled) {
                runWithPermission(() => {
                  onToggleSigaaDiscenteSecao(true);
                  setExpandedDiscente(true);
                });
              } else {
                onToggleSigaaDiscenteSecao(false);
              }
            }}
          >
            <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
              <SubToggleRow
                title="Saldo baixo no RU"
                subtitle="Avisa uma vez por queda quando atingir o limite de refeições"
                checked={sigaaNotificacoes.saldoRuBaixoAlerta}
                disabled={!discenteChecked}
                onChange={(v) =>
                  v
                    ? runWithPermission(() => onSetSaldoRuBaixoAlerta(true))
                    : onSetSaldoRuBaixoAlerta(false)
                }
              />

              {discenteChecked && sigaaNotificacoes.saldoRuBaixoAlerta && (
                <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
                  <span className="ty-overline" style={{ color: 'var(--muted)' }}>
                    ALERTAR QUANDO RESTAREM ATÉ
                  </span>
                  <div style={{ display: 'flex', flexWrap: 'wrap', gap: '8px' }}>
                    {LIMITE_REFEICOES_OPCOES.map((count) => (
                      <OptionChip
                        key={count}
                        label={count === 1 ? '1 refeição' : `${count} refeições`}
                        selected={sigaaNotificacoes.limiteRefeicoesRuBaixo === count}
                        onClick={() => onSetLimiteRefeicoesRuBaixo(count)}
                      />
                    ))}
                  </div>
                </div>
              )}

              <HairlineDivider />
              <SubToggleRow
                title="Movimentações no extrato RU"
                subtitle="Novas recargas ou débitos registrados nos últimos 7 dias"
                checked={sigaaNotificacoes.extratoRuMovimentacaoAlerta}
                disabled={!discenteChecked}
                onChange={(v) =>
                  v
                    ? runWithPermission(() => onSetExtratoRuMovimentacaoAlerta(true))
                    : onSetExtratoRuMovimentacaoAlerta(false)
                }
              />

              <HairlineDivider />
              <SubToggleRow
                title="Boletim atualizado"
                subtitle="Notas finais, menções (SS, MS, MM...) ou situação final"
                checked={sigaaNotificacoes.boletimAlteradoAlerta}
                disabled={!discenteChecked}
                onChange={(v) =>
                  v
                    ? runWithPermission(() => onSetBoletimAlteradoAlerta(true))
                    : onSetBoletimAlteradoAlerta(false)
                }
              />

              <HairlineDivider />
              <SubToggleRow
                title="Índices acadêmicos (IRA / MP)"
                subtitle="Mudanças em IRA, MP ou percentual integralizado"
                checked={sigaaNotificacoes.indicesAlteradosAlerta}
                disabled={!discenteChecked}
                onChange={(v) =>
                  v
                    ? runWithPermission(() => onSetIndicesAlteradosAlerta(true))
                    : onSetIndicesAlteradosAlerta(false)
                }
              />
            </div>
          </SectionCard>
        );
      })}

      {initialSection !== 'RU' && (
        <EditorialCard
          style={{
            padding: '12px',
            backgroundColor: 'var(--surface-raised)',
            gap: '8px',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <RefreshCw size={17} strokeWidth={1.75} style={{ color: 'var(--ink)' }} />
            <span className="ty-overline" style={{ color: 'var(--ink)' }}>
              VERIFICAÇÃO DE NOVIDADES (SIGAA)
            </span>
          </div>
          <p className="ty-label" style={{ color: 'var(--muted)' }}>
            Frequência alvo de verificação silenciosa ao abrir ou retomar o aplicativo.
          </p>
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: '8px' }}>
            {SIGAA_SYNC_INTERVAL_OPTIONS.map((opt) => (
              <OptionChip
                key={opt.id}
                label={opt.label}
                selected={sigaaNotificacoes.syncIntervalHours === opt.id}
                onClick={() => onSetSyncInterval(opt.id)}
              />
            ))}
          </div>
          <HairlineDivider />
          <SubToggleRow
            title="Sincronizar somente no Wi-Fi"
            subtitle="Evita consumo em redes móveis limitadas"
            checked={sigaaNotificacoes.somenteWifi}
            onChange={onSetSomenteWifi}
          />
        </EditorialCard>
      )}
    </ModalDialogSheet>
  );
};

const SectionCard: React.FC<{
  readonly Icon: typeof Utensils;
  readonly title: string;
  readonly subtitle: string;
  readonly checked: boolean;
  readonly expanded: boolean;
  readonly collapsible: boolean;
  readonly onToggleExpand: () => void;
  readonly onCheckedChange: (next: boolean) => void;
  readonly children: React.ReactNode;
}> = ({
  Icon,
  title,
  subtitle,
  checked,
  expanded,
  collapsible,
  onToggleExpand,
  onCheckedChange,
  children,
}) => (
  <EditorialCard style={{ padding: '14px', gap: '12px' }}>
    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: '8px' }}>
      <div
        role={collapsible ? 'button' : undefined}
        tabIndex={collapsible ? 0 : undefined}
        onClick={collapsible ? onToggleExpand : undefined}
        onKeyDown={
          collapsible
            ? (e) => {
                if (e.key === 'Enter' || e.key === ' ') {
                  e.preventDefault();
                  onToggleExpand();
                }
              }
            : undefined
        }
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: '10px',
          flex: 1,
          minWidth: 0,
          cursor: collapsible ? 'pointer' : 'default',
        }}
      >
        <Icon size={20} strokeWidth={1.75} style={{ color: 'var(--ink)', flexShrink: 0 }} />
        <div style={{ flex: 1, minWidth: 0 }}>
          <p className="ty-title" style={{ color: 'var(--ink)' }}>
            {title}
          </p>
          <p className="ty-label" style={{ color: 'var(--muted)' }}>
            {subtitle}
          </p>
        </div>
        {collapsible &&
          (expanded ? (
            <ChevronUp size={18} strokeWidth={1.75} style={{ color: 'var(--muted)' }} />
          ) : (
            <ChevronDown size={18} strokeWidth={1.75} style={{ color: 'var(--muted)' }} />
          ))}
      </div>

      <EditorialSwitch checked={checked} onCheckedChange={onCheckedChange} label={title} />
    </div>

    {expanded && (
      <>
        <HairlineDivider />
        {children}
      </>
    )}
  </EditorialCard>
);

const SubToggleRow: React.FC<{
  readonly title: string;
  readonly subtitle: string;
  readonly checked: boolean;
  readonly disabled?: boolean;
  readonly onChange: (next: boolean) => void;
}> = ({ title, subtitle, checked, disabled = false, onChange }) => (
  <div
    style={{
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'space-between',
      gap: '12px',
      opacity: disabled ? 0.55 : 1,
    }}
  >
    <div style={{ flex: 1 }}>
      <p className="ty-title" style={{ color: 'var(--ink)', fontSize: '0.9375rem' }}>
        {title}
      </p>
      <p className="ty-label" style={{ color: 'var(--muted)' }}>
        {subtitle}
      </p>
    </div>
    <EditorialSwitch
      checked={checked}
      disabled={disabled}
      onCheckedChange={onChange}
      label={title}
    />
  </div>
);

const OptionChip: React.FC<{
  readonly label: string;
  readonly selected: boolean;
  readonly showCheck?: boolean;
  readonly onClick: () => void;
}> = ({ label, selected, showCheck = false, onClick }) => (
  <button
    type="button"
    aria-pressed={selected}
    className={`filter-chip ${selected ? 'filter-chip--selected' : ''}`}
    onClick={onClick}
  >
    {showCheck && <Check size={14} strokeWidth={2} />}
    <span>{label}</span>
  </button>
);
