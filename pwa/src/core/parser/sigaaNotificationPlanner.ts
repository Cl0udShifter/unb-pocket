import {
  SigaaNotificacaoHelpers,
  type AtualizacaoTurmaFeed,
  type AvaliacaoTurma,
  type BoletimSemestre,
  type DiaSemanaSigaa,
  type HorarioSlot,
  type IndicesIntegralizacao,
  type MapaFrequenciaTurma,
  type NotaDisciplinaBoletim,
  type NotasParciaisTurma,
  type SaldoExtratoRu,
  type SigaaNotificacaoConfig,
  type SigaaNotificacaoConteudo,
  type TarefaTurma,
  type TransacaoRu,
  type TurmaDetalheAva,
  type TurmaResumo,
} from '../model/models';
import {
  addDaysIso,
  brasiliaLocalToEpochMillis,
  formatIsoDate,
  getBrasiliaParts,
  isValidCalendarDate,
  parseIsoDate,
} from './brasiliaTime';
import { RuMenuFreshness } from './ruMenuFreshness';

export interface ScheduledSigaaReminder {
  readonly notificacao: SigaaNotificacaoConteudo;
  readonly triggerAtEpochMillis: number;
  readonly eventAtEpochMillis: number;
  readonly eventEndAtEpochMillis: number;
}

export interface FrequenciaBaselineState {
  readonly faltasComputadas: number;
  readonly limiteMaximoFaltas: number;
  readonly faltasRestantes: number;
  readonly emRiscoReprovacaoPorFalta: boolean;
}

export interface NotasParciaisBaselineState {
  readonly unidades: Record<string, string>;
  readonly mediaFinal: string | null;
  readonly situacao: string | null;
}

export interface IndicesBaselineState {
  readonly ira: number | null;
  readonly mp: number | null;
  readonly percentualIntegralizadoExato: number | null;
  readonly percentualIntegralizadoTexto: string | null;
}

export interface SigaaNotificationBaseline {
  readonly seededAtEpochMillis: number;
  readonly feedInitialized: boolean;
  readonly feedAssinaturas: readonly string[];
  readonly avisosPorTurma: Record<string, readonly string[]>;
  readonly materiaisPorTurma: Record<string, readonly string[]>;
  readonly notasPorTurma: Record<string, NotasParciaisBaselineState>;
  readonly frequenciaPorTurma: Record<string, FrequenciaBaselineState>;
  readonly ruSaldoInitialized: boolean;
  readonly ruTransacoesAssinaturas: readonly string[];
  readonly lastAlertedLowRuSaldoCentavos: number | null;
  readonly lastAlertedLowRuMeals: number | null;
  readonly boletimInitialized: boolean;
  readonly boletimDisciplinas: Record<string, string>;
  readonly indices: IndicesBaselineState | null;
}

export interface SigaaSnapshotInput {
  readonly turmas: readonly TurmaResumo[];
  readonly feed: readonly AtualizacaoTurmaFeed[];
  readonly feedAvailable: boolean;
  readonly detalhesPorTurma: Record<string, TurmaDetalheAva>;
  readonly notasPorTurma: Record<string, NotasParciaisTurma>;
  readonly frequenciaPorTurma: Record<string, MapaFrequenciaTurma>;
  readonly saldoExtratoRu: SaldoExtratoRu | null;
  readonly boletim: readonly BoletimSemestre[] | null;
  readonly indices: IndicesIntegralizacao | null;
}

export interface SigaaDiffResult {
  readonly notifications: readonly SigaaNotificacaoConteudo[];
  readonly updatedBaseline: SigaaNotificationBaseline;
}

const TRIGGER_GRACE_WINDOW_MILLIS = 120 * 60 * 1000;
const CLASS_CATCHUP_WINDOW_MILLIS = 75 * 60 * 1000;
const CLASS_POST_START_CATCHUP_MILLIS = 45 * 60 * 1000;

const TIME_REGEX = /(\d{1,2})\s*[:hH]\s*(\d{2})/;
const DATE_TOKEN_REGEX = /(\d{2}\/\d{2}\/\d{4}|\d{2}\/\d{2}\/\d{2}|\d{2}\/\d{2})(?!\d)/g;

export const SigaaNotificationPlanner = {
  TRIGGER_GRACE_WINDOW_MILLIS,
  CLASS_CATCHUP_WINDOW_MILLIS,

  buildUpcomingReminders(params: {
    turmas: readonly TurmaResumo[];
    avaliacoesByTurma: Record<string, readonly AvaliacaoTurma[]>;
    tarefasByTurma: Record<string, readonly TarefaTurma[]>;
    config: SigaaNotificacaoConfig;
    nowEpochMillis: number;
    horizonDays?: number;
  }): ScheduledSigaaReminder[] {
    const { turmas, avaliacoesByTurma, tarefasByTurma, config, nowEpochMillis } = params;
    const horizonDays = Math.max(1, params.horizonDays ?? 7);
    if (!SigaaNotificacaoHelpers.hasAnyLocalScheduleEnabled(config)) return [];

    const todayIso = getBrasiliaParts(nowEpochMillis).isoDate;
    const endEpochMillis = nowEpochMillis + horizonDays * 24 * 60 * 60 * 1000;
    const minTriggerEpochMillis = nowEpochMillis - TRIGGER_GRACE_WINDOW_MILLIS;
    const leadMinutes = resolveValidLeadMinutes(config.antecedenciaAulaMinutos);

    const reminders: ScheduledSigaaReminder[] = [];
    for (const turma of turmas) {
      if (isTurmaMuted(turma, config)) continue;

      if (config.aulasLembrete) {
        reminders.push(
          ...buildClassRemindersForTurma(
            turma,
            leadMinutes,
            todayIso,
            horizonDays,
            nowEpochMillis,
            minTriggerEpochMillis,
            endEpochMillis,
          ),
        );
      }

      if (config.provasETarefasLembrete) {
        const avaliacoes =
          avaliacoesByTurma[turma.frontEndIdTurma] ??
          avaliacoesByTurma[turma.idTurma] ??
          [];
        for (const avaliacao of avaliacoes) {
          reminders.push(
            ...buildExamReminders(
              turma,
              avaliacao,
              todayIso,
              nowEpochMillis,
              minTriggerEpochMillis,
              endEpochMillis,
            ),
          );
        }

        const tarefas =
          tarefasByTurma[turma.frontEndIdTurma] ??
          tarefasByTurma[turma.idTurma] ??
          [];
        for (const tarefa of tarefas) {
          reminders.push(
            ...buildTaskReminders(
              turma,
              tarefa,
              todayIso,
              nowEpochMillis,
              minTriggerEpochMillis,
              endEpochMillis,
            ),
          );
        }
      }
    }

    return reminders.sort((a, b) => a.triggerAtEpochMillis - b.triggerAtEpochMillis);
  },

  extractDueReminders(
    reminders: readonly ScheduledSigaaReminder[],
    alreadySentKeys: ReadonlySet<string>,
    nowEpochMillis: number,
  ): ScheduledSigaaReminder[] {
    return reminders.filter((reminder) => {
      const grace =
        reminder.notificacao.tipo === 'AULA_PROXIMA'
          ? CLASS_CATCHUP_WINDOW_MILLIS
          : TRIGGER_GRACE_WINDOW_MILLIS;
      const catchupCutoff = Math.min(
        reminder.triggerAtEpochMillis + grace,
        reminder.eventEndAtEpochMillis,
      );
      return (
        reminder.triggerAtEpochMillis <= nowEpochMillis &&
        nowEpochMillis <= catchupCutoff &&
        !alreadySentKeys.has(reminder.notificacao.dedupKey)
      );
    });
  },

  seedBaseline(snapshot: SigaaSnapshotInput, nowEpochMillis: number): SigaaNotificationBaseline {
    const feedAssinaturas = Array.from(new Set(snapshot.feed.map(feedSignature)));
    const avisosPorTurma = buildNoticesSignatureMap(snapshot.detalhesPorTurma);
    const materiaisPorTurma = buildMaterialsSignatureMap(snapshot.detalhesPorTurma);
    const notasPorTurma: Record<string, NotasParciaisBaselineState> = {};
    for (const [k, v] of Object.entries(snapshot.notasPorTurma)) {
      notasPorTurma[k] = toNotasBaseline(v);
    }
    const frequenciaPorTurma: Record<string, FrequenciaBaselineState> = {};
    for (const [k, v] of Object.entries(snapshot.frequenciaPorTurma)) {
      frequenciaPorTurma[k] = toFreqBaseline(v);
    }

    return {
      seededAtEpochMillis: nowEpochMillis,
      feedInitialized: snapshot.feedAvailable,
      feedAssinaturas,
      avisosPorTurma,
      materiaisPorTurma,
      notasPorTurma,
      frequenciaPorTurma,
      ruSaldoInitialized: snapshot.saldoExtratoRu !== null,
      ruTransacoesAssinaturas:
        snapshot.saldoExtratoRu?.transacoesUltimos7Dias.map(transactionSignature) ?? [],
      lastAlertedLowRuSaldoCentavos: null,
      lastAlertedLowRuMeals: null,
      boletimInitialized: snapshot.boletim !== null,
      boletimDisciplinas: snapshot.boletim ? buildBoletimSignatureMap(snapshot.boletim) : {},
      indices: snapshot.indices ? toIndicesBaseline(snapshot.indices) : null,
    };
  },

  detectSnapshotChanges(
    previous: SigaaNotificationBaseline | null,
    snapshot: SigaaSnapshotInput,
    config: SigaaNotificacaoConfig,
    nowEpochMillis: number,
  ): SigaaDiffResult {
    if (!previous || previous.seededAtEpochMillis <= 0) {
      return {
        notifications: [],
        updatedBaseline: this.seedBaseline(snapshot, nowEpochMillis),
      };
    }

    const notifications: SigaaNotificacaoConteudo[] = [];
    const turmaByAnyId = buildTurmaLookup(snapshot.turmas);
    const mutedById = resolveMutedIds(snapshot.turmas, config);

    const currentNoticesMap = buildNoticesSignatureMap(snapshot.detalhesPorTurma);
    const currentMaterialsMap = buildMaterialsSignatureMap(snapshot.detalhesPorTurma);
    const notifiedTitlesByTurma = new Map<string, Set<string>>();

    const updatedNoticesMap: Record<string, readonly string[]> = { ...previous.avisosPorTurma };
    const updatedMaterialsMap: Record<string, readonly string[]> = { ...previous.materiaisPorTurma };

    const isTurmasAtivo = SigaaNotificacaoHelpers.isTurmasAtivo(config);
    const isDiscenteAtivo = SigaaNotificacaoHelpers.isDiscenteAtivo(config);

    // 1) Novos avisos e materiais por turma
    for (const [turmaKey, detalhe] of Object.entries(snapshot.detalhesPorTurma)) {
      const prevNotices = previous.avisosPorTurma[turmaKey];
      const prevMaterials = previous.materiaisPorTurma[turmaKey];
      const currNotices = currentNoticesMap[turmaKey] ?? [];
      const currMaterials = currentMaterialsMap[turmaKey] ?? [];

      if (
        prevNotices !== undefined &&
        isTurmasAtivo &&
        config.avisosEMateriaisNovos &&
        !mutedById.has(turmaKey) &&
        !mutedById.has(detalhe.idTurma)
      ) {
        const addedTitles = collectNewNoticeTitles(detalhe, new Set(prevNotices));
        if (addedTitles.length > 0) {
          const seenSet = notifiedTitlesByTurma.get(detalhe.idTurma) ?? new Set<string>();
          for (const t of addedTitles) seenSet.add(normalizeComparable(t));
          notifiedTitlesByTurma.set(detalhe.idTurma, seenSet);

          const resumo =
            addedTitles.length === 1
              ? addedTitles[0]
              : `${addedTitles.length} novos comunicados: ${addedTitles[0]}`;
          const dedupKey = `turma_aviso:${detalhe.idTurma}:${stableHash([...addedTitles].sort().join('|'))}`;
          notifications.push({
            dedupKey,
            notificationId: stableNotificationId(dedupKey),
            tipo: 'TURMA_AVISO_OU_MATERIAL',
            titulo: `Novo aviso em ${detalhe.nomeDisciplina}`,
            resumo,
            detalhesExpandido: addedTitles.join('\n'),
            target: 'TURMAS',
            triggerAtEpochMillis: nowEpochMillis,
          });
        }
      }

      if (
        prevMaterials !== undefined &&
        isTurmasAtivo &&
        config.avisosEMateriaisNovos &&
        !mutedById.has(turmaKey) &&
        !mutedById.has(detalhe.idTurma)
      ) {
        const addedMaterials = collectNewMaterialNames(detalhe, new Set(prevMaterials));
        if (addedMaterials.length > 0) {
          const resumo =
            addedMaterials.length === 1
              ? `Material: ${addedMaterials[0]}`
              : `${addedMaterials.length} novos materiais: ${addedMaterials[0]}`;
          const dedupKey = `turma_material:${detalhe.idTurma}:${stableHash([...addedMaterials].sort().join('|'))}`;
          notifications.push({
            dedupKey,
            notificationId: stableNotificationId(dedupKey),
            tipo: 'TURMA_AVISO_OU_MATERIAL',
            titulo: `Novo material em ${detalhe.nomeDisciplina}`,
            resumo,
            detalhesExpandido: addedMaterials.join('\n'),
            target: 'TURMAS',
            triggerAtEpochMillis: nowEpochMillis,
          });
        }
      }

      updatedNoticesMap[turmaKey] = currNotices;
      updatedMaterialsMap[turmaKey] = currMaterials;
    }

    // 2) Feed geral
    const nextFeedInitialized = previous.feedInitialized || snapshot.feedAvailable;
    const nextFeedSignatures = snapshot.feedAvailable
      ? Array.from(new Set(snapshot.feed.map(feedSignature)))
      : previous.feedAssinaturas;

    if (
      snapshot.feedAvailable &&
      previous.feedInitialized &&
      isTurmasAtivo &&
      config.avisosEMateriaisNovos
    ) {
      const prevFeedSet = new Set(previous.feedAssinaturas);
      for (const item of snapshot.feed) {
        const sig = feedSignature(item);
        const alreadyNotified =
          notifiedTitlesByTurma.get(item.idTurma)?.has(normalizeComparable(item.tituloNoticia)) ??
          false;
        if (!prevFeedSet.has(sig) && !mutedById.has(item.idTurma) && !alreadyNotified) {
          const turma = turmaByAnyId.get(item.idTurma);
          const disciplinaNome = turma?.nomeDisciplina ?? item.disciplinaSemestre;
          const dedupKey = `feed:${item.idTurma}:${stableHash(sig)}`;
          notifications.push({
            dedupKey,
            notificationId: stableNotificationId(dedupKey),
            tipo: 'TURMA_AVISO_OU_MATERIAL',
            titulo: `Atualização em ${disciplinaNome}`,
            resumo: item.tituloNoticia,
            target: 'TURMAS',
            triggerAtEpochMillis: nowEpochMillis,
          });
        }
      }
    }

    // 3) Notas parciais
    const updatedNotasMap: Record<string, NotasParciaisBaselineState> = {
      ...previous.notasPorTurma,
    };
    for (const [turmaKey, notas] of Object.entries(snapshot.notasPorTurma)) {
      const prevNotas = previous.notasPorTurma[turmaKey];
      const currState = toNotasBaseline(notas);
      if (prevNotas && isTurmasAtivo && config.notasParciaisAlteradas && !mutedById.has(turmaKey)) {
        const changedUnits: string[] = [];
        for (const [unitName, unitValue] of Object.entries(currState.unidades)) {
          const oldVal = prevNotas.unidades[unitName];
          if (unitValue && unitValue !== '-' && unitValue !== oldVal) {
            changedUnits.push(`${unitName}: ${unitValue}`);
          }
        }
        if (
          currState.mediaFinal &&
          currState.mediaFinal !== '-' &&
          currState.mediaFinal !== prevNotas.mediaFinal
        ) {
          changedUnits.push(`Média final: ${currState.mediaFinal}`);
        }
        if (
          currState.situacao &&
          currState.situacao !== prevNotas.situacao &&
          changedUnits.length === 0
        ) {
          changedUnits.push(`Situação: ${currState.situacao}`);
        }
        if (changedUnits.length > 0) {
          const nomeTurma =
            turmaByAnyId.get(turmaKey)?.nomeDisciplina ??
            snapshot.detalhesPorTurma[turmaKey]?.nomeDisciplina ??
            turmaKey;
          const dedupKey = `nota_parcial:${turmaKey}:${stableHash(changedUnits.join('|'))}`;
          notifications.push({
            dedupKey,
            notificationId: stableNotificationId(dedupKey),
            tipo: 'NOTA_PARCIAL_ALTERADA',
            titulo: `Nota atualizada em ${nomeTurma}`,
            resumo: changedUnits.join(' · '),
            detalhesExpandido: changedUnits.join('\n'),
            target: 'TURMAS',
            triggerAtEpochMillis: nowEpochMillis,
          });
        }
      }
      updatedNotasMap[turmaKey] = currState;
    }

    // 4) Risco por faltas
    const updatedFreqMap: Record<string, FrequenciaBaselineState> = {
      ...previous.frequenciaPorTurma,
    };
    for (const [turmaKey, freq] of Object.entries(snapshot.frequenciaPorTurma)) {
      const prevFreq = previous.frequenciaPorTurma[turmaKey];
      const currState = toFreqBaseline(freq);
      if (prevFreq && isTurmasAtivo && config.riscoFaltaAlerta && !mutedById.has(turmaKey)) {
        const prevSeverity = riskSeverityOf(prevFreq);
        const currSeverity = riskSeverityOf(currState);
        const worsened = currSeverity > prevSeverity;
        const moreAbsencesInRisk =
          currSeverity > 0 && currState.faltasComputadas > prevFreq.faltasComputadas;
        if (worsened || moreAbsencesInRisk) {
          const nomeTurma =
            turmaByAnyId.get(turmaKey)?.nomeDisciplina ??
            snapshot.detalhesPorTurma[turmaKey]?.nomeDisciplina ??
            turmaKey;
          const titulo =
            currSeverity === 2
              ? `Risco de reprovação por faltas: ${nomeTurma}`
              : `Atenção nas faltas: ${nomeTurma}`;
          const resumo =
            currState.faltasRestantes >= 0
              ? `${currState.faltasComputadas}/${currState.limiteMaximoFaltas} faltas (${currState.faltasRestantes} restantes)`
              : `${currState.faltasComputadas}/${currState.limiteMaximoFaltas} faltas`;
          const tierName = currSeverity === 2 ? 'RISCO' : 'ATENCAO';
          const dedupKey = `risco_falta:${turmaKey}:${tierName}:${currState.faltasComputadas}`;
          notifications.push({
            dedupKey,
            notificationId: stableNotificationId(dedupKey),
            tipo: 'RISCO_FALTAS',
            titulo,
            resumo,
            target: 'TURMAS',
            triggerAtEpochMillis: nowEpochMillis,
          });
        }
      }
      updatedFreqMap[turmaKey] = currState;
    }

    // 5) Saldo RU + extrato
    const saldo = snapshot.saldoExtratoRu;
    const nextRuSaldoInitialized = previous.ruSaldoInitialized || saldo !== null;
    const nextRuTxList = saldo
      ? saldo.transacoesUltimos7Dias.map(transactionSignature)
      : previous.ruTransacoesAssinaturas;
    let nextAlertedLowSaldo = previous.lastAlertedLowRuSaldoCentavos;
    let nextAlertedLowMeals = previous.lastAlertedLowRuMeals;

    if (saldo && previous.ruSaldoInitialized) {
      const rest = saldo.refeicoesRestantes;
      const isGratuito = rest?.gratuito === true;
      const mealsRemaining = rest?.almocosOuJantaresRestantes ?? null;
      const threshold = Math.max(1, config.limiteRefeicoesRuBaixo);

      if (!isGratuito && mealsRemaining !== null && mealsRemaining <= threshold) {
        const shouldAlert =
          isDiscenteAtivo &&
          config.saldoRuBaixoAlerta &&
          (previous.lastAlertedLowRuSaldoCentavos === null ||
            saldo.saldoAtualCentavos < previous.lastAlertedLowRuSaldoCentavos ||
            mealsRemaining < (previous.lastAlertedLowRuMeals ?? Number.MAX_SAFE_INTEGER));

        if (shouldAlert) {
          const saldoFormatado = formatBrlFromCentavos(saldo.saldoAtualCentavos);
          const mealWord = mealsRemaining === 1 ? 'refeição' : 'refeições';
          const dedupKey = `ru_saldo_baixo:${saldo.saldoAtualCentavos}:${mealsRemaining}:${threshold}`;
          notifications.push({
            dedupKey,
            notificationId: stableNotificationId(dedupKey),
            tipo: 'RU_SALDO_BAIXO',
            titulo: `Saldo do RU baixo (${mealsRemaining} ${mealWord})`,
            resumo: `Saldo atual: ${saldoFormatado}`,
            target: 'DISCENTE',
            triggerAtEpochMillis: nowEpochMillis,
          });
          nextAlertedLowSaldo = saldo.saldoAtualCentavos;
          nextAlertedLowMeals = mealsRemaining;
        }
      } else {
        nextAlertedLowSaldo = null;
        nextAlertedLowMeals = null;
      }

      if (isDiscenteAtivo && config.extratoRuMovimentacaoAlerta) {
        const newTransactions = findAddedTransactions(
          previous.ruTransacoesAssinaturas,
          saldo.transacoesUltimos7Dias,
        );
        if (newTransactions.length > 0) {
          let resumo: string;
          if (newTransactions.length === 1) {
            const tx = newTransactions[0];
            const sign = tx.valorCentavos >= 0 ? '+' : '-';
            resumo = `${tx.descricao}: ${sign}${formatBrlFromCentavos(Math.abs(tx.valorCentavos))}`;
          } else {
            resumo = `${newTransactions.length} movimentações · ${newTransactions[0].descricao}`;
          }
          const dedupKey = `ru_extrato:${stableHash(newTransactions.map(transactionSignature).join('|'))}`;
          notifications.push({
            dedupKey,
            notificationId: stableNotificationId(dedupKey),
            tipo: 'RU_EXTRATO_MOVIMENTACAO',
            titulo:
              newTransactions.length === 1
                ? 'Nova movimentação no extrato do RU'
                : 'Novas movimentações no extrato do RU',
            resumo,
            target: 'DISCENTE',
            triggerAtEpochMillis: nowEpochMillis,
          });
        }
      }
    }

    // 6) Boletim geral
    const currentBoletimMap = snapshot.boletim
      ? buildBoletimSignatureMap(snapshot.boletim)
      : previous.boletimDisciplinas;
    const nextBoletimInitialized = previous.boletimInitialized || snapshot.boletim !== null;

    if (
      snapshot.boletim &&
      previous.boletimInitialized &&
      isDiscenteAtivo &&
      config.boletimAlteradoAlerta
    ) {
      const changedSubjects: string[] = [];
      for (const sem of snapshot.boletim) {
        for (const disc of sem.disciplinas) {
          const key = `${sem.semestre}|${disc.codigo}`;
          const currSig = boletimSubjectSignature(disc);
          const prevSig = previous.boletimDisciplinas[key];
          if (currSig !== prevSig) {
            const status =
              (disc.resultadoMencao && disc.resultadoMencao !== '-' ? disc.resultadoMencao : null) ??
              (disc.nota1 && disc.nota1 !== '-' ? disc.nota1 : null) ??
              disc.situacao;
            changedSubjects.push(status ? `${disc.disciplina}: ${status}` : disc.disciplina);
          }
        }
      }
      if (changedSubjects.length > 0) {
        const resumo =
          changedSubjects.length === 1
            ? changedSubjects[0]
            : `${changedSubjects.length} alterações: ${changedSubjects[0]}`;
        const dedupKey = `boletim:${stableHash([...changedSubjects].sort().join('|'))}`;
        notifications.push({
          dedupKey,
          notificationId: stableNotificationId(dedupKey),
          tipo: 'BOLETIM_ATUALIZADO',
          titulo: 'Boletim atualizado no SIGAA',
          resumo,
          detalhesExpandido: changedSubjects.join('\n'),
          target: 'DISCENTE',
          triggerAtEpochMillis: nowEpochMillis,
        });
      }
    }

    // 7) Indices
    const currIndices = snapshot.indices ? toIndicesBaseline(snapshot.indices) : null;
    const prevIndices = previous.indices;
    const nextIndices = currIndices ?? prevIndices;

    if (currIndices && prevIndices && isDiscenteAtivo && config.indicesAlteradosAlerta) {
      const indexChanges = detectIndicesChanges(prevIndices, currIndices);
      if (indexChanges.length > 0) {
        const dedupKey = `indices:${stableHash(indexChanges.join('|'))}`;
        notifications.push({
          dedupKey,
          notificationId: stableNotificationId(dedupKey),
          tipo: 'INDICES_ATUALIZADOS',
          titulo: 'Índices acadêmicos atualizados',
          resumo: indexChanges.join(' · '),
          detalhesExpandido: indexChanges.join('\n'),
          target: 'DISCENTE',
          triggerAtEpochMillis: nowEpochMillis,
        });
      }
    }

    return {
      notifications,
      updatedBaseline: {
        seededAtEpochMillis: previous.seededAtEpochMillis,
        feedInitialized: nextFeedInitialized,
        feedAssinaturas: nextFeedSignatures,
        avisosPorTurma: updatedNoticesMap,
        materiaisPorTurma: updatedMaterialsMap,
        notasPorTurma: updatedNotasMap,
        frequenciaPorTurma: updatedFreqMap,
        ruSaldoInitialized: nextRuSaldoInitialized,
        ruTransacoesAssinaturas: nextRuTxList,
        lastAlertedLowRuSaldoCentavos: nextAlertedLowSaldo,
        lastAlertedLowRuMeals: nextAlertedLowMeals,
        boletimInitialized: nextBoletimInitialized,
        boletimDisciplinas: currentBoletimMap,
        indices: nextIndices,
      },
    };
  },
};

interface TimePoint {
  readonly hour: number;
  readonly minute: number;
}

function formatTimePoint(t: TimePoint): string {
  return `${String(t.hour).padStart(2, '0')}:${String(t.minute).padStart(2, '0')}`;
}

function timePointToMinutes(t: TimePoint): number {
  return t.hour * 60 + t.minute;
}

function buildClassRemindersForTurma(
  turma: TurmaResumo,
  leadMinutes: number,
  todayIso: string,
  horizonDays: number,
  nowEpochMillis: number,
  minTriggerEpochMillis: number,
  endEpochMillis: number,
): ScheduledSigaaReminder[] {
  if (turma.horarioSlots.length === 0) return [];
  const result: ScheduledSigaaReminder[] = [];

  for (let dayOffset = 0; dayOffset <= horizonDays; dayOffset += 1) {
    const targetIso = addDaysIso(todayIso, dayOffset);
    const { dayOfWeekIso } = parseIsoDate(targetIso);
    const sigaaDay = toSigaaDayOfWeek(dayOfWeekIso);
    if (!sigaaDay) continue;

    const daySlots = turma.horarioSlots.filter((s) => s.diaSemana === sigaaDay);
    if (daySlots.length === 0) continue;

    const blocks = mergeContiguousClassBlocks(daySlots);
    for (const block of blocks) {
      const classStartEpoch = brasiliaLocalToEpochMillis(
        targetIso,
        block.start.hour,
        block.start.minute,
      );
      const classBlockEndEpoch = brasiliaLocalToEpochMillis(
        targetIso,
        block.end.hour,
        block.end.minute,
      );
      const classMaxCatchupEpoch = Math.min(
        classBlockEndEpoch,
        classStartEpoch + CLASS_POST_START_CATCHUP_MILLIS,
      );
      const triggerEpoch = classStartEpoch - leadMinutes * 60 * 1000;

      if (classMaxCatchupEpoch <= nowEpochMillis) continue;
      if (triggerEpoch < minTriggerEpochMillis || triggerEpoch > endEpochMillis) continue;

      const startStr = formatTimePoint(block.start);
      const endStr = formatTimePoint(block.end);
      const localPart = turma.local.trim();
      const resumo = localPart ? `${startStr} - ${endStr} · Local: ${localPart}` : `${startStr} - ${endStr}`;
      const titulo =
        nowEpochMillis >= classStartEpoch
          ? `Aula iniciou às ${startStr}: ${turma.nomeDisciplina}`
          : `Aula às ${startStr}: ${turma.nomeDisciplina}`;
      const dedupKey = `aula:${turma.idTurma}:${targetIso}:${startStr}:${leadMinutes}`;

      result.push({
        notificacao: {
          dedupKey,
          notificationId: stableNotificationId(dedupKey),
          tipo: 'AULA_PROXIMA',
          titulo,
          resumo,
          target: 'TURMAS',
          triggerAtEpochMillis: triggerEpoch,
        },
        triggerAtEpochMillis: triggerEpoch,
        eventAtEpochMillis: classStartEpoch,
        eventEndAtEpochMillis: classMaxCatchupEpoch,
      });
    }
  }
  return result;
}

function buildExamReminders(
  turma: TurmaResumo,
  avaliacao: AvaliacaoTurma,
  referenceIsoDate: string,
  nowEpochMillis: number,
  minTriggerEpochMillis: number,
  endEpochMillis: number,
): ScheduledSigaaReminder[] {
  const examIso = parseLastDate(avaliacao.dataTexto, referenceIsoDate);
  if (!examIso) return [];
  const examTime = avaliacao.horaTexto ? parseFirstTime(avaliacao.horaTexto) : null;
  const eventTime = examTime ?? { hour: 8, minute: 0 };
  const eventEpoch = brasiliaLocalToEpochMillis(examIso, eventTime.hour, eventTime.minute);
  const sameDayCatchupEndEpoch = examTime
    ? eventEpoch
    : Math.max(
        eventEpoch,
        brasiliaLocalToEpochMillis(examIso, 7, 0) + TRIGGER_GRACE_WINDOW_MILLIS,
      );
  if (sameDayCatchupEndEpoch <= nowEpochMillis) return [];

  const reminders: ScheduledSigaaReminder[] = [];
  const timeText = examTime ? formatTimePoint(examTime) : null;
  const localSuffix = turma.local.trim() ? ` · ${turma.local.trim()}` : '';
  const baseResumo = `${avaliacao.descricao.trim()}${timeText ? ` (${timeText})` : ''}${localSuffix}`;

  const eveIso = addDaysIso(examIso, -1);
  const eveEpoch = brasiliaLocalToEpochMillis(eveIso, 20, 0);
  if (eveEpoch >= minTriggerEpochMillis && eveEpoch <= endEpochMillis) {
    const dedupKey = `aval_vespera:${turma.idTurma}:${examIso}:${stableHash(avaliacao.descricao)}`;
    reminders.push({
      notificacao: {
        dedupKey,
        notificationId: stableNotificationId(dedupKey),
        tipo: 'AVALIACAO_OU_TAREFA',
        titulo: `Avaliação amanhã: ${turma.nomeDisciplina}`,
        resumo: baseResumo,
        target: 'TURMAS',
        triggerAtEpochMillis: eveEpoch,
      },
      triggerAtEpochMillis: eveEpoch,
      eventAtEpochMillis: eventEpoch,
      eventEndAtEpochMillis: eventEpoch,
    });
  }

  const sameDayEpoch = examTime
    ? eventEpoch - 60 * 60 * 1000
    : brasiliaLocalToEpochMillis(examIso, 7, 0);
  if (sameDayEpoch >= minTriggerEpochMillis && sameDayEpoch <= endEpochMillis) {
    const title = timeText
      ? `Avaliação às ${timeText}: ${turma.nomeDisciplina}`
      : `Avaliação hoje: ${turma.nomeDisciplina}`;
    const dedupKey = `aval_dia:${turma.idTurma}:${examIso}:${timeText ?? '0700'}:${stableHash(avaliacao.descricao)}`;
    reminders.push({
      notificacao: {
        dedupKey,
        notificationId: stableNotificationId(dedupKey),
        tipo: 'AVALIACAO_OU_TAREFA',
        titulo: title,
        resumo: baseResumo,
        target: 'TURMAS',
        triggerAtEpochMillis: sameDayEpoch,
      },
      triggerAtEpochMillis: sameDayEpoch,
      eventAtEpochMillis: eventEpoch,
      eventEndAtEpochMillis: sameDayCatchupEndEpoch,
    });
  }

  return reminders;
}

function buildTaskReminders(
  turma: TurmaResumo,
  tarefa: TarefaTurma,
  referenceIsoDate: string,
  nowEpochMillis: number,
  minTriggerEpochMillis: number,
  endEpochMillis: number,
): ScheduledSigaaReminder[] {
  const deadline = parseTaskDeadline(tarefa.periodoEntrega, referenceIsoDate);
  if (!deadline) return [];
  const { dateIso: deadlineIso, time: deadlineTime } = deadline;
  const effectiveTime = deadlineTime ?? { hour: 23, minute: 59 };
  const eventEpoch = brasiliaLocalToEpochMillis(deadlineIso, effectiveTime.hour, effectiveTime.minute);
  if (eventEpoch <= nowEpochMillis) return [];

  const reminders: ScheduledSigaaReminder[] = [];
  const timeText = deadlineTime ? formatTimePoint(deadlineTime) : null;
  const resumo = timeText
    ? `${tarefa.titulo.trim()} · Prazo até ${timeText}`
    : tarefa.periodoEntrega.trim()
      ? `${tarefa.titulo.trim()} · ${tarefa.periodoEntrega.trim()}`
      : tarefa.titulo.trim();

  const eveIso = addDaysIso(deadlineIso, -1);
  const eveEpoch = brasiliaLocalToEpochMillis(eveIso, 20, 0);
  if (eveEpoch >= minTriggerEpochMillis && eveEpoch <= endEpochMillis) {
    const dedupKey = `tarefa_vespera:${turma.idTurma}:${deadlineIso}:${stableHash(tarefa.titulo)}`;
    reminders.push({
      notificacao: {
        dedupKey,
        notificationId: stableNotificationId(dedupKey),
        tipo: 'AVALIACAO_OU_TAREFA',
        titulo: `Entrega amanhã: ${turma.nomeDisciplina}`,
        resumo,
        target: 'TURMAS',
        triggerAtEpochMillis: eveEpoch,
      },
      triggerAtEpochMillis: eveEpoch,
      eventAtEpochMillis: eventEpoch,
      eventEndAtEpochMillis: eventEpoch,
    });
  }

  const sameDayEpoch = deadlineTime
    ? eventEpoch - 60 * 60 * 1000
    : brasiliaLocalToEpochMillis(deadlineIso, 7, 0);
  if (sameDayEpoch >= minTriggerEpochMillis && sameDayEpoch <= endEpochMillis) {
    const dedupKey = `tarefa_dia:${turma.idTurma}:${deadlineIso}:${timeText ?? '0700'}:${stableHash(tarefa.titulo)}`;
    reminders.push({
      notificacao: {
        dedupKey,
        notificationId: stableNotificationId(dedupKey),
        tipo: 'AVALIACAO_OU_TAREFA',
        titulo: timeText
          ? `Entrega até ${timeText}: ${turma.nomeDisciplina}`
          : `Entrega hoje: ${turma.nomeDisciplina}`,
        resumo,
        target: 'TURMAS',
        triggerAtEpochMillis: sameDayEpoch,
      },
      triggerAtEpochMillis: sameDayEpoch,
      eventAtEpochMillis: eventEpoch,
      eventEndAtEpochMillis: eventEpoch,
    });
  }

  return reminders;
}

export function mergeContiguousClassBlocks(
  slots: readonly HorarioSlot[],
): Array<{ start: TimePoint; end: TimePoint }> {
  const parsed = slots
    .map((s) => {
      const start = parseFirstTime(s.inicio);
      const end = parseFirstTime(s.fim);
      return start && end ? { start, end } : null;
    })
    .filter((b): b is { start: TimePoint; end: TimePoint } => b !== null)
    .sort((a, b) => timePointToMinutes(a.start) - timePointToMinutes(b.start));

  if (parsed.length === 0) return [];

  const merged: Array<{ start: TimePoint; end: TimePoint }> = [];
  let current = parsed[0];
  for (let i = 1; i < parsed.length; i += 1) {
    const next = parsed[i];
    const gap = timePointToMinutes(next.start) - timePointToMinutes(current.end);
    if (gap <= 15) {
      current = {
        start: current.start,
        end:
          timePointToMinutes(next.end) > timePointToMinutes(current.end)
            ? next.end
            : current.end,
      };
    } else {
      merged.push(current);
      current = next;
    }
  }
  merged.push(current);
  return merged;
}

function parseTaskDeadline(
  periodoEntrega: string,
  referenceIsoDate: string,
): { dateIso: string; time: TimePoint | null } | null {
  const matches = Array.from(periodoEntrega.matchAll(DATE_TOKEN_REGEX));
  const lastMatch = matches[matches.length - 1];
  if (!lastMatch) return null;
  const dateIso = parseDateToken(lastMatch[1], referenceIsoDate);
  if (!dateIso) return null;
  const trailing = periodoEntrega.slice(lastMatch.index ?? 0);
  const time = parseFirstTime(trailing);
  return { dateIso, time };
}

function parseLastDate(text: string, referenceIsoDate: string): string | null {
  const matches = Array.from(text.matchAll(DATE_TOKEN_REGEX));
  const last = matches[matches.length - 1]?.[1];
  if (last) {
    const parsed = parseDateToken(last, referenceIsoDate);
    if (parsed) return parsed;
  }
  const extracted = RuMenuFreshness.extractDatesFromText(text, referenceIsoDate);
  return extracted[extracted.length - 1] ?? null;
}

function parseDateToken(token: string, referenceIsoDate: string): string | null {
  const trimmed = token.trim();
  if (trimmed.length === 8 && trimmed.split('/').length === 3) {
    const [dd, mm, yy] = trimmed.split('/').map((x) => Number.parseInt(x, 10));
    const fullYear = 2000 + yy;
    if (isValidCalendarDate(fullYear, mm, dd)) {
      return formatIsoDate(fullYear, mm, dd);
    }
  }
  return RuMenuFreshness.extractDatesFromText(trimmed, referenceIsoDate)[0] ?? null;
}

function parseFirstTime(text: string): TimePoint | null {
  const m = TIME_REGEX.exec(text);
  if (!m) return null;
  const hour = Number.parseInt(m[1], 10);
  const minute = Number.parseInt(m[2], 10);
  if (hour < 0 || hour > 23 || minute < 0 || minute > 59) return null;
  return { hour, minute };
}

function toSigaaDayOfWeek(dayOfWeekIso: number): DiaSemanaSigaa | null {
  switch (dayOfWeekIso) {
    case 1:
      return 'SEGUNDA';
    case 2:
      return 'TERCA';
    case 3:
      return 'QUARTA';
    case 4:
      return 'QUINTA';
    case 5:
      return 'SEXTA';
    case 6:
      return 'SABADO';
    default:
      return null;
  }
}

function resolveValidLeadMinutes(rawMinutes: number): number {
  return rawMinutes === 15 || rawMinutes === 30 || rawMinutes === 60 ? rawMinutes : 30;
}

function isTurmaMuted(turma: TurmaResumo, config: SigaaNotificacaoConfig): boolean {
  return (
    config.turmasSilenciadas.includes(turma.frontEndIdTurma) ||
    config.turmasSilenciadas.includes(turma.idTurma)
  );
}

function resolveMutedIds(
  turmas: readonly TurmaResumo[],
  config: SigaaNotificacaoConfig,
): Set<string> {
  if (config.turmasSilenciadas.length === 0) return new Set();
  const set = new Set<string>(config.turmasSilenciadas);
  for (const turma of turmas) {
    if (isTurmaMuted(turma, config)) {
      set.add(turma.frontEndIdTurma);
      set.add(turma.idTurma);
    }
  }
  return set;
}

function buildTurmaLookup(turmas: readonly TurmaResumo[]): Map<string, TurmaResumo> {
  const map = new Map<string, TurmaResumo>();
  for (const t of turmas) {
    map.set(t.frontEndIdTurma, t);
    map.set(t.idTurma, t);
  }
  return map;
}

function buildNoticesSignatureMap(
  detalhesPorTurma: Record<string, TurmaDetalheAva>,
): Record<string, string[]> {
  const out: Record<string, string[]> = {};
  for (const [k, d] of Object.entries(detalhesPorTurma)) {
    const set = new Set<string>();
    for (const r of d.historicoNoticiasResumo) {
      set.add(noticeSignature(r.titulo, r.dataHoraTexto));
    }
    if (d.ultimaNoticia) {
      set.add(noticeSignature(d.ultimaNoticia.titulo, d.ultimaNoticia.dataHoraTexto));
    }
    out[k] = Array.from(set);
  }
  return out;
}

function buildMaterialsSignatureMap(
  detalhesPorTurma: Record<string, TurmaDetalheAva>,
): Record<string, string[]> {
  const out: Record<string, string[]> = {};
  for (const [k, d] of Object.entries(detalhesPorTurma)) {
    const set = new Set<string>();
    for (const topico of d.topicosAula) {
      for (const m of topico.materiais) {
        set.add(`${m.materialId}|${m.nome.trim()}`);
      }
    }
    out[k] = Array.from(set);
  }
  return out;
}

function collectNewNoticeTitles(
  detalhe: TurmaDetalheAva,
  previousSignatures: ReadonlySet<string>,
): string[] {
  const seen = new Set<string>();
  const added: string[] = [];
  if (detalhe.ultimaNoticia) {
    const sig = noticeSignature(
      detalhe.ultimaNoticia.titulo,
      detalhe.ultimaNoticia.dataHoraTexto,
    );
    if (!previousSignatures.has(sig) && !seen.has(sig) && detalhe.ultimaNoticia.titulo.trim()) {
      seen.add(sig);
      added.push(detalhe.ultimaNoticia.titulo.trim());
    }
  }
  for (const resumo of detalhe.historicoNoticiasResumo) {
    const sig = noticeSignature(resumo.titulo, resumo.dataHoraTexto);
    if (!previousSignatures.has(sig) && !seen.has(sig) && resumo.titulo.trim()) {
      seen.add(sig);
      added.push(resumo.titulo.trim());
    }
  }
  return added;
}

function collectNewMaterialNames(
  detalhe: TurmaDetalheAva,
  previousSignatures: ReadonlySet<string>,
): string[] {
  const seen = new Set<string>();
  const added: string[] = [];
  for (const topico of detalhe.topicosAula) {
    for (const m of topico.materiais) {
      const name = m.nome.trim();
      const sig = `${m.materialId}|${name}`;
      if (!previousSignatures.has(sig) && name && !seen.has(name)) {
        seen.add(name);
        added.push(name);
      }
    }
  }
  return added;
}

function noticeSignature(title: string, dateText: string): string {
  return `${normalizeComparable(title)}|${normalizeComparable(dateText)}`;
}

function feedSignature(item: AtualizacaoTurmaFeed): string {
  return `${normalizeComparable(item.dataTexto)}|${item.idTurma.trim()}|${normalizeComparable(item.tituloNoticia)}`;
}

function transactionSignature(tx: TransacaoRu): string {
  return `${normalizeComparable(tx.dataHoraTexto)}|${normalizeComparable(tx.descricao)}|${tx.valorCentavos}`;
}

function findAddedTransactions(
  previous: readonly string[],
  current: readonly TransacaoRu[],
): TransacaoRu[] {
  const remaining = new Map<string, number>();
  for (const sig of previous) {
    remaining.set(sig, (remaining.get(sig) ?? 0) + 1);
  }
  const added: TransacaoRu[] = [];
  for (const tx of current) {
    const sig = transactionSignature(tx);
    const prevCount = remaining.get(sig) ?? 0;
    if (prevCount > 0) {
      remaining.set(sig, prevCount - 1);
    } else {
      added.push(tx);
    }
  }
  return added;
}

function buildBoletimSignatureMap(boletim: readonly BoletimSemestre[]): Record<string, string> {
  const out: Record<string, string> = {};
  for (const sem of boletim) {
    for (const disc of sem.disciplinas) {
      out[`${sem.semestre}|${disc.codigo}`] = boletimSubjectSignature(disc);
    }
  }
  return out;
}

function boletimSubjectSignature(disc: NotaDisciplinaBoletim): string {
  return `${(disc.nota1 ?? '').trim()}|${(disc.resultadoMencao ?? '').trim()}|${disc.situacao.trim()}`;
}

function detectIndicesChanges(
  prev: IndicesBaselineState,
  curr: IndicesBaselineState,
): string[] {
  const changes: string[] = [];
  if (curr.ira !== null && (prev.ira === null || Math.abs(curr.ira - prev.ira) >= 0.0001)) {
    changes.push(
      prev.ira !== null
        ? `IRA: ${formatDecimal(prev.ira)} -> ${formatDecimal(curr.ira)}`
        : `IRA: ${formatDecimal(curr.ira)}`,
    );
  }
  if (curr.mp !== null && (prev.mp === null || Math.abs(curr.mp - prev.mp) >= 0.0001)) {
    changes.push(
      prev.mp !== null
        ? `MP: ${formatDecimal(prev.mp)} -> ${formatDecimal(curr.mp)}`
        : `MP: ${formatDecimal(curr.mp)}`,
    );
  }
  if (
    curr.percentualIntegralizadoExato !== null &&
    (prev.percentualIntegralizadoExato === null ||
      Math.abs(curr.percentualIntegralizadoExato - prev.percentualIntegralizadoExato) >= 0.01)
  ) {
    const pctText =
      curr.percentualIntegralizadoTexto && curr.percentualIntegralizadoTexto.trim().length > 0
        ? curr.percentualIntegralizadoTexto
        : `${curr.percentualIntegralizadoExato.toFixed(1).replace('.', ',')}%`;
    changes.push(`Integralização: ${pctText}`);
  }
  return changes;
}

function riskSeverityOf(state: FrequenciaBaselineState): 0 | 1 | 2 {
  if (state.emRiscoReprovacaoPorFalta) return 2;
  if (state.limiteMaximoFaltas > 0 && state.faltasComputadas >= state.limiteMaximoFaltas) return 2;
  if (
    state.limiteMaximoFaltas > 0 &&
    state.faltasComputadas > 0 &&
    state.faltasRestantes >= 1 &&
    state.faltasRestantes <= 2
  ) {
    return 1;
  }
  return 0;
}

function toNotasBaseline(n: NotasParciaisTurma): NotasParciaisBaselineState {
  const unidades: Record<string, string> = {};
  for (const u of n.unidades) {
    unidades[u.nome.trim()] = (u.valor ?? '').trim();
  }
  return {
    unidades,
    mediaFinal: n.mediaFinal?.trim() ?? null,
    situacao: n.situacao?.trim() ?? null,
  };
}

function toFreqBaseline(f: MapaFrequenciaTurma): FrequenciaBaselineState {
  return {
    faltasComputadas: f.faltasComputadas,
    limiteMaximoFaltas: f.limiteMaximoFaltas,
    faltasRestantes: f.faltasRestantes,
    emRiscoReprovacaoPorFalta: f.emRiscoReprovacaoPorFalta,
  };
}

function toIndicesBaseline(i: IndicesIntegralizacao): IndicesBaselineState {
  return {
    ira: i.ira,
    mp: i.mp,
    percentualIntegralizadoExato: i.percentualIntegralizadoExato,
    percentualIntegralizadoTexto: i.percentualIntegralizadoTexto?.trim() ?? null,
  };
}

function normalizeComparable(raw: string): string {
  return raw.trim().replace(/\s+/g, ' ').toLowerCase();
}

function formatDecimal(value: number): string {
  return value.toFixed(4).replace('.', ',').replace(/,?0+$/, '');
}

function formatBrlFromCentavos(centavos: number): string {
  return `R$ ${(centavos / 100).toFixed(2).replace('.', ',')}`;
}

function stableHash(input: string): number {
  let h = 0;
  for (let i = 0; i < input.length; i += 1) {
    h = (Math.imul(31, h) + input.charCodeAt(i)) | 0;
  }
  return h;
}

function stableNotificationId(dedupKey: string): number {
  return 2000 + ((stableHash(dedupKey) & 0x7fffffff) % 800_000);
}
