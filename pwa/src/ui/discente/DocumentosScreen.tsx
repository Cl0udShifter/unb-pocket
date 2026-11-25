import { Download, ExternalLink, FileText, Share2 } from 'lucide-react';
import React, { useEffect } from 'react';
import {
  TIPO_DOCUMENTO_BY_ID,
  type DocumentoOficialEmitido,
  type TipoDocumentoOficial,
  type UnbPocketCore,
} from '../../core/model/models';
import { Formatters } from '../common/formatters';
import {
  BackHeaderButton,
  EditorialCard,
  EditorialHeader,
  ErrorBanner,
  InfoCalloutRow,
  SkeletonInlineBar,
  StatusTag,
} from '../components/CommonComponents';
import {
  shareOrDownloadCachedFile,
  type ViewableDocumentRequest,
} from '../components/InAppDocumentViewerModal';
import type { DiscenteUiState } from '../viewmodel/appStores';

const AVAILABLE_TYPES: readonly TipoDocumentoOficial[] = [
  'ATESTADO_MATRICULA',
  'DECLARACAO_VINCULO',
  'HISTORICO_ESCOLAR',
  'PASSE_LIVRE_ESTUDANTIL',
];

function docToViewRequest(doc: DocumentoOficialEmitido): ViewableDocumentRequest {
  const ext = doc.mimeType.includes('pdf') ? 'pdf' : 'html';
  const slug = doc.tipo.toLowerCase();
  return {
    title: doc.titulo,
    fileName: `${slug}.${ext}`,
    localFilePath: doc.localFilePath,
    mimeType: doc.mimeType,
    verificationCode: doc.codigoVerificacao,
  };
}

interface DocumentosScreenProps {
  readonly core: UnbPocketCore;
  readonly uiState: DiscenteUiState;
  readonly onBack: () => void;
  readonly onEmitirDocumento: (tipo: TipoDocumentoOficial) => void;
  readonly onOpenDocumentViewer: (doc: ViewableDocumentRequest) => void;
  readonly onConsumeEmitted: () => void;
  readonly onOpenLogin: () => void;
  readonly onDismissError: () => void;
}

export const DocumentosScreen: React.FC<DocumentosScreenProps> = ({
  core,
  uiState,
  onBack,
  onEmitirDocumento,
  onOpenDocumentViewer,
  onConsumeEmitted,
  onOpenLogin,
  onDismissError,
}) => {
  useEffect(() => {
    const emitted = uiState.lastEmittedDocumentoEvent;
    if (!emitted) return;
    onOpenDocumentViewer(docToViewRequest(emitted));
    onConsumeEmitted();
  }, [uiState.lastEmittedDocumentoEvent, onOpenDocumentViewer, onConsumeEmitted]);

  const visibleHistory = uiState.documentosEmitidos.filter(
    (d) => d.tipo !== 'CARTEIRINHA_ESTUDANTIL',
  );

  return (
    <div className="screen-root">
      <EditorialHeader
        title="Documentos Digitais"
        navigationIcon={<BackHeaderButton onClick={onBack} label="Voltar ao perfil" />}
        subtitle={
          <p className="ty-label" style={{ color: 'var(--muted)' }}>
            Emissão oficial via SIGAA (PDF e HTML)
          </p>
        }
      />

      <div className="screen-scroll screen-scroll--subpage">
        {uiState.documentosError && (
          <ErrorBanner
            banner={uiState.documentosError}
            onLoginClick={uiState.documentosError.requiresLogin ? onOpenLogin : null}
            onDismiss={onDismissError}
          />
        )}

        <InfoCalloutRow text="Os documentos são emitidos em modo somente leitura direto do SIGAA e abertos no leitor integrado com opção de compartilhar ou salvar no Arquivos do iOS." />

        <p className="ty-overline" style={{ color: 'var(--muted)' }}>
          EMITIR NOVO DOCUMENTO
        </p>

        {AVAILABLE_TYPES.map((tipo) => {
          const meta = TIPO_DOCUMENTO_BY_ID[tipo];
          const isEmittingThis = uiState.emittingDocumentoTipo === tipo;
          const anyEmitting = uiState.emittingDocumentoTipo !== null;

          return (
            <EditorialCard key={tipo} style={{ gap: '12px' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                <FileText size={22} strokeWidth={1.75} style={{ color: 'var(--ink)', flexShrink: 0 }} />
                <div>
                  <p className="ty-title" style={{ color: 'var(--ink)' }}>
                    {meta.displayName}
                  </p>
                  <p className="ty-label" style={{ color: 'var(--muted)' }}>
                    {meta.subtitle}
                  </p>
                </div>
              </div>

              <button
                type="button"
                className="btn-primary"
                disabled={anyEmitting}
                style={{ width: '100%' }}
                onClick={() => onEmitirDocumento(tipo)}
              >
                {isEmittingThis ? (
                  <>
                    <SkeletonInlineBar width={22} height={7} color="var(--muted)" />
                    <span className="ty-label">Emitindo documento...</span>
                  </>
                ) : (
                  <>
                    <Download size={18} strokeWidth={1.75} />
                    <span className="ty-label">Emitir e abrir</span>
                  </>
                )}
              </button>
            </EditorialCard>
          );
        })}

        {visibleHistory.length > 0 && (
          <>
            <p className="ty-overline" style={{ color: 'var(--muted)' }}>
              DOCUMENTOS SALVOS ({visibleHistory.length})
            </p>

            {visibleHistory.map((doc) => {
              const meta = TIPO_DOCUMENTO_BY_ID[doc.tipo];
              const viewReq = docToViewRequest(doc);
              return (
                <EditorialCard
                  key={`${doc.tipo}_${doc.emitidoEmEpochMillis}_${doc.localFilePath}`}
                  style={{ gap: '8px' }}
                >
                  <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                    <StatusTag text={meta.displayName} tone="INFO" />
                    <span className="ty-label ty-tabular" style={{ color: 'var(--muted)' }}>
                      {Formatters.formatSyncTimestamp(doc.emitidoEmEpochMillis)}
                    </span>
                  </div>

                  <p className="ty-title" style={{ color: 'var(--ink)' }}>
                    {doc.titulo}
                  </p>

                  {doc.codigoVerificacao && (
                    <p className="ty-mono" style={{ color: 'var(--muted)' }}>
                      Código: {doc.codigoVerificacao}
                    </p>
                  )}

                  <div style={{ display: 'flex', gap: '8px', marginTop: '4px' }}>
                    <button
                      type="button"
                      className="btn-tonal"
                      style={{ flex: 1 }}
                      onClick={() => onOpenDocumentViewer(viewReq)}
                    >
                      <ExternalLink size={16} strokeWidth={1.75} />
                      <span className="ty-label">Abrir</span>
                    </button>

                    <button
                      type="button"
                      className="btn-secondary"
                      style={{ flex: 1 }}
                      onClick={() => void shareOrDownloadCachedFile(core, viewReq, true)}
                    >
                      <Share2 size={16} strokeWidth={1.75} />
                      <span className="ty-label">Compartilhar</span>
                    </button>
                  </div>
                </EditorialCard>
              );
            })}
          </>
        )}
      </div>
    </div>
  );
};
