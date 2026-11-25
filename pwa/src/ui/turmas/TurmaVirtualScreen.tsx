import {
  Award,
  Bell,
  BookOpen,
  CalendarCheck,
  CheckCircle2,
  ChevronDown,
  ChevronUp,
  Download,
  FileText,
  RefreshCw,
  X,
} from 'lucide-react';
import React from 'react';
import type {
  AnexoArquivoSigaa,
  ArquivoBaixado,
  AvaliacaoTurma,
  MapaFrequenciaTurma,
  MaterialAulaAva,
  NotasParciaisTurma,
  NoticiaTurmaAva,
  TarefaTurma,
  TopicoAulaAva,
  TurmaDetalheAva,
} from '../../core/model/models';
import { Formatters } from '../common/formatters';
import {
  BackHeaderButton,
  EditorialCard,
  EditorialHeader,
  EmptyStateCard,
  ErrorBanner,
  HairlineDivider,
  HeaderActionIconButton,
  InfoCalloutRow,
  InnerTile,
  OfflineOriginStrip,
  SegmentedPillBar,
  SkeletonBox,
  SkeletonInlineBar,
  StatusTag,
} from '../components/CommonComponents';
import type { ViewableDocumentRequest } from '../components/InAppDocumentViewerModal';
import { SafeHtmlContent } from '../components/SafeHtmlContent';
import type {
  TurmaDetailTab,
  TurmaVirtualUiState,
} from '../viewmodel/appStores';

const DETAIL_TABS: readonly { id: TurmaDetailTab; label: string }[] = [
  { id: 'TOPICOS', label: 'Tópicos' },
  { id: 'NOTICIAS', label: 'Notícias' },
  { id: 'FREQUENCIA', label: 'Faltas' },
  { id: 'NOTAS_AVALIACOES', label: 'Notas' },
];

function formatBytesLabel(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  const kb = bytes / 1024;
  if (kb < 1024) return `${Math.round(kb)} KB`;
  return `${(kb / 1024).toFixed(1).replace('.', ',')} MB`;
}

interface TurmaVirtualScreenProps {
  readonly uiState: TurmaVirtualUiState;
  readonly onBack: () => void;
  readonly onSelectTab: (tab: TurmaDetailTab) => void;
  readonly onRefreshTab: () => void;
  readonly onToggleNoticia: (idNoticia: string) => void;
  readonly onDownloadMaterial: (materialId: string) => void;
  readonly onDownloadAnexo: (anexoId: string) => void;
  readonly onOpenDownloadedFile: (doc: ViewableDocumentRequest) => void;
  readonly onConsumeDownloadedFile: () => void;
  readonly onOpenLogin: () => void;
  readonly onDismissError: () => void;
}

export const TurmaVirtualScreen: React.FC<TurmaVirtualScreenProps> = ({
  uiState,
  onBack,
  onSelectTab,
  onRefreshTab,
  onToggleNoticia,
  onDownloadMaterial,
  onDownloadAnexo,
  onOpenDownloadedFile,
  onConsumeDownloadedFile,
  onOpenLogin,
  onDismissError,
}) => {
  const detalhe = uiState.detalhe?.value ?? null;
  const title = detalhe?.nomeDisciplina ?? uiState.summary?.nomeDisciplina ?? 'Turma Virtual';
  const code = detalhe?.codigoDisciplina || uiState.summary?.codigoDisciplina || '';
  const periodo = detalhe?.periodoTurmaTexto || uiState.summary?.semestre || '';
  const subtitle = [
    code.trim() || null,
    uiState.summary?.numeroTurma?.trim() ? `Turma ${uiState.summary.numeroTurma}` : null,
    periodo.trim() || null,
  ]
    .filter(Boolean)
    .join(' · ');

  const activeOrigin =
    uiState.selectedDetailTab === 'TOPICOS' || uiState.selectedDetailTab === 'NOTICIAS'
      ? uiState.detalhe
      : uiState.selectedDetailTab === 'FREQUENCIA'
        ? uiState.frequencia
        : uiState.notasParciais;

  return (
    <div className="screen-root">
      <EditorialHeader
        title={title}
        navigationIcon={<BackHeaderButton onClick={onBack} label="Voltar para turmas" />}
        subtitle={
          subtitle ? (
            <p className="ty-label" style={{ color: 'var(--muted)' }}>
              {subtitle}
            </p>
          ) : undefined
        }
        actions={
          <HeaderActionIconButton
            icon={RefreshCw}
            label="Atualizar aba"
            isLoading={uiState.isLoadingDetail}
            onClick={onRefreshTab}
          />
        }
      />

      <div className="screen-scroll screen-scroll--subpage">
        {uiState.downloadedFileEvent && (
          <DownloadSuccessBanner
            downloaded={uiState.downloadedFileEvent}
            onOpen={() => {
              const d = uiState.downloadedFileEvent!;
              onOpenDownloadedFile({
                title: d.nomeArquivo,
                fileName: d.nomeArquivo,
                localFilePath: d.localFilePath,
                mimeType: d.mimeType,
              });
            }}
            onDismiss={onConsumeDownloadedFile}
          />
        )}

        {uiState.detailError && (
          <ErrorBanner
            banner={uiState.detailError}
            onRetry={uiState.detailError.canRetry ? onRefreshTab : null}
            onLoginClick={uiState.detailError.requiresLogin ? onOpenLogin : null}
            onDismiss={onDismissError}
          />
        )}

        <SegmentedPillBar
          options={DETAIL_TABS.map((t) => t.label)}
          selectedIndex={DETAIL_TABS.findIndex((t) => t.id === uiState.selectedDetailTab)}
          onSelect={(idx) => onSelectTab(DETAIL_TABS[idx].id)}
        />

        {uiState.selectedDetailTab === 'TOPICOS' && (
          <>
            {!detalhe && uiState.isLoadingDetail ? (
              <>
                <SkeletonBox style={{ width: '100%', height: '68px', borderRadius: '16px' }} />
                <SkeletonBox style={{ width: '100%', height: '148px', borderRadius: '16px' }} />
                <SkeletonBox style={{ width: '100%', height: '148px', borderRadius: '16px' }} />
              </>
            ) : !detalhe ? (
              <EmptyStateCard
                icon={BookOpen}
                title="Conteúdo da turma não carregado"
                body="Toque abaixo para buscar tópicos de aula e arquivos do SIGAA."
                actionLabel="Carregar tópicos"
                onAction={onRefreshTab}
              />
            ) : (
              <>
                <CourseProgressCard detalhe={detalhe} />
                {detalhe.topicosAula.length === 0 ? (
                  <EmptyStateCard
                    icon={BookOpen}
                    title="Nenhum tópico cadastrado"
                    body="O docente ainda não publicou tópicos de aula ou materiais nesta turma."
                  />
                ) : (
                  detalhe.topicosAula.map((topico, idx) => (
                    <TopicoCard
                      key={`${idx}_${topico.index}_${topico.titulo}`}
                      topico={topico}
                      downloadingItemId={uiState.downloadingItemId}
                      onDownloadMaterial={onDownloadMaterial}
                    />
                  ))
                )}
              </>
            )}
          </>
        )}

        {uiState.selectedDetailTab === 'NOTICIAS' && (
          <>
            {!detalhe && uiState.isLoadingDetail ? (
              <>
                <SkeletonBox style={{ width: '100%', height: '160px', borderRadius: '16px' }} />
                <SkeletonBox style={{ width: '100%', height: '84px', borderRadius: '16px' }} />
              </>
            ) : !detalhe ||
              (!detalhe.ultimaNoticia && detalhe.historicoNoticiasResumo.length === 0) ? (
              <EmptyStateCard
                icon={Bell}
                title="Sem notícias na turma"
                body="Nenhum comunicado foi publicado pelos docentes nesta turma até o momento."
                actionLabel="Atualizar"
                onAction={onRefreshTab}
              />
            ) : (
              <>
                {detalhe.ultimaNoticia && (
                  <NoticiaFullCard
                    badgeLabel="ÚLTIMA NOTÍCIA"
                    noticia={detalhe.ultimaNoticia}
                    downloadingItemId={uiState.downloadingItemId}
                    onDownloadAnexo={onDownloadAnexo}
                  />
                )}

                {detalhe.historicoNoticiasResumo.length > 0 && (
                  <>
                    <p
                      className="ty-overline"
                      style={{ color: 'var(--muted)', paddingTop: '4px' }}
                    >
                      HISTÓRICO DE COMUNICADOS
                    </p>
                    {detalhe.historicoNoticiasResumo.map((resumo, idx) => {
                      const expanded = uiState.expandedNoticiaId === resumo.idNoticia;
                      const fullNoticia = uiState.fullNoticiaById[resumo.idNoticia];
                      const isLoadingThis = uiState.loadingNoticiaIds.has(resumo.idNoticia);

                      return (
                        <EditorialCard
                          key={`${idx}_${resumo.idNoticia}`}
                          onClick={() => onToggleNoticia(resumo.idNoticia)}
                          style={{ gap: '6px' }}
                        >
                          <div
                            style={{
                              display: 'flex',
                              alignItems: 'center',
                              justifyContent: 'space-between',
                            }}
                          >
                            <span className="ty-label ty-tabular" style={{ color: 'var(--muted)' }}>
                              {resumo.dataHoraTexto}
                            </span>
                            {isLoadingThis ? (
                              <span
                                style={{ display: 'inline-flex', alignItems: 'center', gap: '6px' }}
                              >
                                <span className="ty-label" style={{ color: 'var(--muted)' }}>
                                  Carregando...
                                </span>
                                <SkeletonInlineBar width={22} height={6} color="var(--muted)" />
                              </span>
                            ) : expanded && fullNoticia ? (
                              <ChevronUp size={18} strokeWidth={1.75} style={{ color: 'var(--muted)' }} />
                            ) : (
                              <ChevronDown size={18} strokeWidth={1.75} style={{ color: 'var(--muted)' }} />
                            )}
                          </div>

                          <p className="ty-title" style={{ color: 'var(--ink)' }}>
                            {resumo.titulo}
                          </p>

                          {expanded && fullNoticia && (
                            <div
                              style={{ display: 'flex', flexDirection: 'column', gap: '10px', marginTop: '6px' }}
                              onClick={(e) => e.stopPropagation()}
                            >
                              <HairlineDivider />
                              <NoticiaBodyContent
                                noticia={fullNoticia}
                                downloadingItemId={uiState.downloadingItemId}
                                onDownloadAnexo={onDownloadAnexo}
                              />
                            </div>
                          )}
                        </EditorialCard>
                      );
                    })}
                  </>
                )}
              </>
            )}
          </>
        )}

        {uiState.selectedDetailTab === 'FREQUENCIA' && (
          <>
            {!uiState.frequencia && uiState.isLoadingDetail ? (
              <>
                <SkeletonBox style={{ width: '100%', height: '132px', borderRadius: '16px' }} />
                <SkeletonBox style={{ width: '100%', height: '72px', borderRadius: '16px' }} />
              </>
            ) : !uiState.frequencia ? (
              <EmptyStateCard
                icon={CalendarCheck}
                title="Frequência ainda não carregada"
                body="Toque abaixo para consultar o mapa de presença no SIGAA."
                actionLabel="Carregar frequência"
                onAction={onRefreshTab}
              />
            ) : (
              <>
                <FrequenciaSummaryCard freq={uiState.frequencia.value} />
                {uiState.frequencia.value.registrosDiarios.length === 0 ? (
                  <InfoCalloutRow text="Nenhum lançamento diário de chamada disponível ainda." />
                ) : (
                  <>
                    <p className="ty-overline" style={{ color: 'var(--muted)' }}>
                      CHAMADAS REGISTRADAS
                    </p>
                    {uiState.frequencia.value.registrosDiarios.map((reg, idx) => (
                      <EditorialCard key={`${idx}_${reg.dataTexto}`}>
                        <div
                          style={{
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'space-between',
                            gap: '8px',
                          }}
                        >
                          <div>
                            <p className="ty-title ty-tabular" style={{ color: 'var(--ink)' }}>
                              {reg.dataTexto}
                            </p>
                            <p className="ty-label" style={{ color: 'var(--muted)' }}>
                              {reg.situacaoTexto.trim() || 'Não registrado'}
                            </p>
                          </div>
                          {reg.presente ? (
                            <StatusTag text="Presente" tone="INFO" />
                          ) : reg.faltasNoDia > 0 ? (
                            <StatusTag text={`${reg.faltasNoDia} falta(s)`} tone="DANGER" />
                          ) : null}
                        </div>
                      </EditorialCard>
                    ))}
                  </>
                )}
              </>
            )}
          </>
        )}

        {uiState.selectedDetailTab === 'NOTAS_AVALIACOES' && (
          <>
            {!uiState.notasParciais && uiState.isLoadingDetail ? (
              <SkeletonBox style={{ width: '100%', height: '140px', borderRadius: '16px' }} />
            ) : (
              <NotasParciaisSectionCard notas={uiState.notasParciais?.value ?? null} />
            )}

            <p className="ty-overline" style={{ color: 'var(--muted)' }}>
              AVALIAÇÕES ({uiState.avaliacoes?.value.length ?? 0})
            </p>
            {(uiState.avaliacoes?.value ?? []).length === 0 ? (
              <InfoCalloutRow text="Nenhuma avaliação marcada no SIGAA." />
            ) : (
              (uiState.avaliacoes?.value ?? []).map((aval, idx) => (
                <AvaliacaoCard key={`${idx}_${aval.dataTexto}_${aval.descricao}`} aval={aval} />
              ))
            )}

            <p className="ty-overline" style={{ color: 'var(--muted)' }}>
              TAREFAS ({uiState.tarefas?.value.length ?? 0})
            </p>
            {(uiState.tarefas?.value ?? []).length === 0 ? (
              <InfoCalloutRow text="Nenhuma tarefa cadastrada no AVA." />
            ) : (
              (uiState.tarefas?.value ?? []).map((tarefa, idx) => (
                <TarefaCard key={`${idx}_${tarefa.titulo}`} tarefa={tarefa} />
              ))
            )}
          </>
        )}

        {activeOrigin && (
          <OfflineOriginStrip
            origin={activeOrigin.origin}
            syncedAtEpochMillis={activeOrigin.syncedAtEpochMillis}
          />
        )}
      </div>
    </div>
  );
};

const CourseProgressCard: React.FC<{ readonly detalhe: TurmaDetalheAva }> = ({ detalhe }) => {
  const pct = Math.min(100, Math.max(0, detalhe.percentualAndamento));
  return (
    <EditorialCard style={{ gap: '10px' }}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <span className="ty-overline" style={{ color: 'var(--muted)' }}>
          ANDAMENTO DO CURSO
        </span>
        <span className="ty-label ty-tabular" style={{ color: 'var(--ink)' }}>
          {detalhe.aulasMinistradas}/{detalhe.aulasTotal} aulas · {pct}%
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
            width: `${pct}%`,
            height: '100%',
            backgroundColor: 'var(--pill-active-bg)',
            borderRadius: '4px',
          }}
        />
      </div>
    </EditorialCard>
  );
};

const TopicoCard: React.FC<{
  readonly topico: TopicoAulaAva;
  readonly downloadingItemId: string | null;
  readonly onDownloadMaterial: (materialId: string) => void;
}> = ({ topico, downloadingItemId, onDownloadMaterial }) => (
  <EditorialCard style={{ gap: '8px' }}>
    {topico.periodoTexto && topico.periodoTexto.trim().length > 0 && (
      <div>
        <StatusTag text={topico.periodoTexto} />
      </div>
    )}
    <p className="ty-title-strong" style={{ color: 'var(--ink)' }}>
      {topico.titulo}
    </p>
    {topico.conteudoTexto.trim().length > 0 && (
      <p className="ty-body" style={{ color: 'var(--muted)', whiteSpace: 'pre-line' }}>
        {topico.conteudoTexto}
      </p>
    )}
    {topico.materiais.length > 0 && (
      <>
        <HairlineDivider style={{ margin: '4px 0' }} />
        <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
          {topico.materiais.map((mat) => (
            <MaterialRow
              key={mat.materialId}
              material={mat}
              isDownloading={downloadingItemId === mat.materialId}
              onClick={() => onDownloadMaterial(mat.materialId)}
            />
          ))}
        </div>
      </>
    )}
  </EditorialCard>
);

const MaterialRow: React.FC<{
  readonly material: MaterialAulaAva;
  readonly isDownloading: boolean;
  readonly onClick: () => void;
}> = ({ material, isDownloading, onClick }) => {
  const extClean = material.tipoExtensao.trim();
  const showExt =
    extClean.length > 0 && !['indicator', 'file'].includes(extClean.toLowerCase());

  return (
    <InnerTile onClick={!isDownloading ? onClick : undefined}>
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: '10px',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: '10px', flex: 1, minWidth: 0 }}>
          <FileText size={18} strokeWidth={1.75} style={{ color: 'var(--ink)', flexShrink: 0 }} />
          <div style={{ minWidth: 0 }}>
            <p className="ty-title" style={{ color: 'var(--ink)' }}>
              {material.nome}
            </p>
            {isDownloading ? (
              <p className="ty-label" style={{ color: 'var(--muted)' }}>
                Baixando arquivo...
              </p>
            ) : showExt ? (
              <p className="ty-mono" style={{ color: 'var(--muted)' }}>
                {extClean.toUpperCase()}
              </p>
            ) : null}
          </div>
        </div>
        {isDownloading ? (
          <SkeletonInlineBar width={22} height={6} color="var(--muted)" />
        ) : (
          <Download size={19} strokeWidth={1.75} style={{ color: 'var(--ink)', flexShrink: 0 }} />
        )}
      </div>
    </InnerTile>
  );
};

const NoticiaFullCard: React.FC<{
  readonly badgeLabel: string;
  readonly noticia: NoticiaTurmaAva;
  readonly downloadingItemId: string | null;
  readonly onDownloadAnexo: (id: string) => void;
}> = ({ badgeLabel, noticia, downloadingItemId, onDownloadAnexo }) => (
  <EditorialCard style={{ gap: '8px' }}>
    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
      <StatusTag text={badgeLabel} tone="INVERTED" />
      {noticia.dataHoraTexto && (
        <span className="ty-label ty-tabular" style={{ color: 'var(--muted)' }}>
          {noticia.dataHoraTexto}
        </span>
      )}
    </div>
    <p className="ty-title-strong" style={{ color: 'var(--ink)' }}>
      {noticia.titulo}
    </p>
    <NoticiaBodyContent
      noticia={noticia}
      downloadingItemId={downloadingItemId}
      onDownloadAnexo={onDownloadAnexo}
    />
  </EditorialCard>
);

const NoticiaBodyContent: React.FC<{
  readonly noticia: NoticiaTurmaAva;
  readonly downloadingItemId: string | null;
  readonly onDownloadAnexo: (id: string) => void;
}> = ({ noticia, downloadingItemId, onDownloadAnexo }) => {
  const hasHtml = noticia.conteudoHtml.trim().length > 0;
  const plainBody = noticia.conteudoTexto.trim() || Formatters.htmlToPlainText(noticia.conteudoHtml);

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
      {noticia.autor && (
        <p className="ty-label" style={{ color: 'var(--muted)' }}>
          Por {noticia.autor}
        </p>
      )}
      {hasHtml ? (
        <SafeHtmlContent html={noticia.conteudoHtml} className="ty-body" />
      ) : plainBody ? (
        <p className="ty-body" style={{ color: 'var(--ink)', whiteSpace: 'pre-line' }}>
          {plainBody}
        </p>
      ) : null}

      {noticia.anexos.length > 0 && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', marginTop: '4px' }}>
          {noticia.anexos.map((anexo) => (
            <AnexoRow
              key={anexo.id}
              anexo={anexo}
              isDownloading={downloadingItemId === anexo.id}
              onClick={() => onDownloadAnexo(anexo.id)}
            />
          ))}
        </div>
      )}
    </div>
  );
};

const AnexoRow: React.FC<{
  readonly anexo: AnexoArquivoSigaa;
  readonly isDownloading: boolean;
  readonly onClick: () => void;
}> = ({ anexo, isDownloading, onClick }) => (
  <InnerTile onClick={!isDownloading ? onClick : undefined}>
    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: '8px' }}>
      <div style={{ flex: 1, minWidth: 0 }}>
        <p className="ty-label" style={{ color: 'var(--ink)' }}>
          {anexo.nomeArquivo}
        </p>
        {isDownloading && (
          <p className="ty-label" style={{ color: 'var(--muted)' }}>
            Baixando anexo...
          </p>
        )}
      </div>
      {isDownloading ? (
        <SkeletonInlineBar width={20} height={6} color="var(--muted)" />
      ) : (
        <Download size={18} strokeWidth={1.75} style={{ color: 'var(--ink)' }} />
      )}
    </div>
  </InnerTile>
);

const DownloadSuccessBanner: React.FC<{
  readonly downloaded: ArquivoBaixado;
  readonly onOpen: () => void;
  readonly onDismiss: () => void;
}> = ({ downloaded, onOpen, onDismiss }) => (
  <div
    style={{
      width: '100%',
      borderRadius: 'var(--radius-card)',
      backgroundColor: 'var(--info-bg)',
      border: '1px solid var(--info-border)',
      padding: '12px 14px',
      display: 'flex',
      flexDirection: 'column',
      gap: '4px',
    }}
  >
    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
        <CheckCircle2 size={18} strokeWidth={1.75} style={{ color: 'var(--info-ink)' }} />
        <p className="ty-title" style={{ color: 'var(--info-ink)' }}>
          Download concluído
        </p>
      </div>
      <button
        type="button"
        onClick={onDismiss}
        aria-label="Fechar aviso de download"
        style={{ color: 'var(--info-ink)', width: '32px', height: '32px' }}
      >
        <X size={16} strokeWidth={1.75} />
      </button>
    </div>
    <p className="ty-title" style={{ color: 'var(--info-ink)' }}>
      {downloaded.nomeArquivo}
    </p>
    <p className="ty-label" style={{ color: 'var(--info-ink)' }}>
      Salvo localmente no app · {formatBytesLabel(downloaded.tamanhoBytes)}
    </p>
    <div style={{ marginTop: '6px' }}>
      <button
        type="button"
        onClick={onOpen}
        className="ty-label"
        style={{
          backgroundColor: 'var(--info-ink)',
          color: 'var(--info-bg)',
          borderRadius: 'var(--radius-tag)',
          padding: '8px 14px',
        }}
      >
        Abrir
      </button>
    </div>
  </div>
);

const FrequenciaSummaryCard: React.FC<{ readonly freq: MapaFrequenciaTurma }> = ({ freq }) => (
  <EditorialCard style={{ gap: '12px' }}>
    {freq.emRiscoReprovacaoPorFalta && (
      <div>
        <StatusTag
          text="Atenção: limite de faltas próximo ou atingido"
          tone="DANGER"
        />
      </div>
    )}
    <span className="ty-overline" style={{ color: 'var(--muted)' }}>
      RESUMO DE FREQUÊNCIA
    </span>

    <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '8px' }}>
      <InnerTile>
        <span className="ty-overline" style={{ color: 'var(--muted)' }}>
          PRESENÇA
        </span>
        <span className="ty-metric-serif" style={{ color: 'var(--ink)', marginTop: '4px' }}>
          {freq.percentualSobreRegistradas}%
        </span>
        <span className="ty-label ty-tabular" style={{ color: 'var(--muted)' }}>
          {freq.presencasRegistradas}/{freq.aulasComRegistro} aulas
        </span>
      </InnerTile>

      <InnerTile>
        <span className="ty-overline" style={{ color: 'var(--muted)' }}>
          FALTAS
        </span>
        <span
          className="ty-metric-serif"
          style={{
            color: freq.emRiscoReprovacaoPorFalta ? 'var(--danger-text)' : 'var(--ink)',
            marginTop: '4px',
          }}
        >
          {freq.faltasComputadas}/{freq.limiteMaximoFaltas}
        </span>
        <span className="ty-label ty-tabular" style={{ color: 'var(--muted)' }}>
          Restam {freq.faltasRestantes}
        </span>
      </InnerTile>
    </div>
  </EditorialCard>
);

const NotasParciaisSectionCard: React.FC<{ readonly notas: NotasParciaisTurma | null }> = ({
  notas,
}) => (
  <EditorialCard style={{ gap: '12px' }}>
    <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
      <Award size={18} strokeWidth={1.75} style={{ color: 'var(--muted)' }} />
      <span className="ty-overline" style={{ color: 'var(--muted)' }}>
        NOTAS PARCIAIS
      </span>
    </div>

    {!notas ? (
      <p className="ty-body" style={{ color: 'var(--muted)' }}>
        Toque em atualizar para buscar notas parciais desta turma.
      </p>
    ) : !notas.notasDisponiveis ? (
      <InfoCalloutRow
        text={
          notas.mensagemAviso?.trim() ||
          'Notas parciais ainda não lançadas pelo docente no SIGAA.'
        }
      />
    ) : (
      <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
        {notas.unidades.map((u, idx) => (
          <InnerTile key={`${idx}_${u.nome}`}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
              <span className="ty-title" style={{ color: 'var(--ink)' }}>
                {u.nome}
              </span>
              <span className="ty-body-strong ty-tabular" style={{ color: 'var(--ink)' }}>
                {u.valor?.trim() || '-'}
              </span>
            </div>
          </InnerTile>
        ))}

        {(notas.mediaFinal || notas.situacao) && (
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              marginTop: '4px',
            }}
          >
            <span className="ty-label" style={{ color: 'var(--muted)' }}>
              Média / Situação
            </span>
            <span className="ty-title-strong ty-tabular" style={{ color: 'var(--ink)' }}>
              {[notas.mediaFinal?.trim(), notas.situacao?.trim()].filter(Boolean).join(' · ')}
            </span>
          </div>
        )}
      </div>
    )}
  </EditorialCard>
);

const AvaliacaoCard: React.FC<{ readonly aval: AvaliacaoTurma }> = ({ aval }) => (
  <EditorialCard style={{ gap: '6px' }}>
    <div>
      <StatusTag
        text={[aval.dataTexto, aval.horaTexto].filter(Boolean).join(' · ')}
        tone="WARNING"
      />
    </div>
    <p className="ty-title" style={{ color: 'var(--ink)' }}>
      {aval.descricao}
    </p>
  </EditorialCard>
);

const TarefaCard: React.FC<{ readonly tarefa: TarefaTurma }> = ({ tarefa }) => {
  const tags = [
    tarefa.notaMaxima?.trim() ? `Máx. ${tarefa.notaMaxima}` : null,
    tarefa.emGrupo ? 'Em grupo' : null,
    tarefa.possuiArquivo ? 'Com arquivo' : null,
  ].filter((t): t is string => Boolean(t));

  return (
    <EditorialCard style={{ gap: '6px' }}>
      <p className="ty-title" style={{ color: 'var(--ink)' }}>
        {tarefa.titulo}
      </p>
      {tarefa.periodoEntrega.trim().length > 0 && (
        <p className="ty-label ty-tabular" style={{ color: 'var(--muted)' }}>
          Entrega: {tarefa.periodoEntrega}
        </p>
      )}
      {tags.length > 0 && (
        <div style={{ display: 'flex', gap: '6px', flexWrap: 'wrap', marginTop: '2px' }}>
          {tags.map((tag) => (
            <StatusTag key={tag} text={tag} />
          ))}
        </div>
      )}
    </EditorialCard>
  );
};
