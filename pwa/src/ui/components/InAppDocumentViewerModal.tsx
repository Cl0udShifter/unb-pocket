import { Download, Minus, Plus, Share2, X } from 'lucide-react';
import React, { useEffect, useRef, useState } from 'react';
import type { UnbPocketCore } from '../../core/model/models';
import { ModalDialogSheet } from './ModalDialogSheet';
import { ErrorBanner, SkeletonBox } from './CommonComponents';
import { SafeHtmlContent } from './SafeHtmlContent';

export interface ViewableDocumentRequest {
  readonly title: string;
  readonly fileName: string;
  readonly localFilePath: string;
  readonly mimeType: string;
  readonly verificationCode?: string | null;
}

interface InAppDocumentViewerModalProps {
  readonly doc: ViewableDocumentRequest | null;
  readonly core: UnbPocketCore;
  readonly onClose: () => void;
}

export async function shareOrDownloadCachedFile(
  core: UnbPocketCore,
  doc: ViewableDocumentRequest,
  preferShare: boolean,
): Promise<boolean> {
  const blob = await core.readCachedBlob(doc.localFilePath);
  if (!blob) return false;

  const cleanMime = doc.mimeType.split(';')[0].trim() || blob.type || 'application/octet-stream';
  const ext = cleanMime.includes('pdf')
    ? '.pdf'
    : cleanMime.includes('html')
      ? '.html'
      : '';
  const safeFileName = doc.fileName.includes('.') ? doc.fileName : `${doc.fileName}${ext}`;

  if (preferShare && typeof navigator !== 'undefined' && typeof navigator.share === 'function') {
    try {
      const file = new File([blob], safeFileName, { type: cleanMime });
      if (!navigator.canShare || navigator.canShare({ files: [file] })) {
        await navigator.share({
          title: doc.title,
          files: [file],
        });
        return true;
      }
    } catch (err) {
      if (err instanceof DOMException && err.name === 'AbortError') {
        return true;
      }
    }
  }

  const objectUrl = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = objectUrl;
  a.download = safeFileName;
  a.rel = 'noopener';
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(objectUrl), 15_000);
  return true;
}

export const InAppDocumentViewerModal: React.FC<InAppDocumentViewerModalProps> = ({
  doc,
  core,
  onClose,
}) => {
  const [blob, setBlob] = useState<Blob | null>(null);
  const [htmlText, setHtmlText] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [zoom, setZoom] = useState(1);
  const [pageCount, setPageCount] = useState(0);
  const pdfCanvasHostRef = useRef<HTMLDivElement | null>(null);

  const isPdf = Boolean(
    doc &&
      (doc.mimeType.toLowerCase().includes('pdf') ||
        doc.fileName.toLowerCase().endsWith('.pdf')),
  );
  const isHtml = Boolean(
    doc &&
      !isPdf &&
      (doc.mimeType.toLowerCase().includes('html') ||
        doc.fileName.toLowerCase().endsWith('.html')),
  );
  const isImage = Boolean(doc && doc.mimeType.toLowerCase().startsWith('image/'));

  useEffect(() => {
    if (!doc) {
      setBlob(null);
      setHtmlText(null);
      setErrorMsg(null);
      setZoom(1);
      setPageCount(0);
      return;
    }

    let cancelled = false;
    setLoading(true);
    setErrorMsg(null);
    setHtmlText(null);
    setPageCount(0);

    void core.readCachedBlob(doc.localFilePath).then(async (loaded) => {
      if (cancelled) return;
      if (!loaded) {
        setErrorMsg('O arquivo não está mais disponível no armazenamento local.');
        setLoading(false);
        return;
      }
      setBlob(loaded);
      if (isHtml) {
        const text = await loaded.text();
        if (!cancelled) {
          const parsed = new DOMParser().parseFromString(text, 'text/html');
          setHtmlText(parsed.body ? parsed.body.innerHTML : text);
          setLoading(false);
        }
      } else if (!isPdf) {
        setLoading(false);
      }
    });

    return () => {
      cancelled = true;
    };
  }, [doc, core, isHtml, isPdf]);

  useEffect(() => {
    if (!doc || !blob || !isPdf) return;
    const host = pdfCanvasHostRef.current;
    if (!host) return;

    let cancelled = false;
    setLoading(true);

    void (async () => {
      try {
        const pdfjs = await import('pdfjs-dist');
        const workerUrlMod = await import('pdfjs-dist/build/pdf.worker.min.mjs?url');
        pdfjs.GlobalWorkerOptions.workerSrc = workerUrlMod.default;

        const bytes = new Uint8Array(await blob.arrayBuffer());
        const loadingTask = pdfjs.getDocument({ data: bytes });
        const pdfDoc = await loadingTask.promise;
        if (cancelled) {
          await pdfDoc.destroy();
          return;
        }

        setPageCount(pdfDoc.numPages);
        host.replaceChildren();

        const containerWidth = Math.max(280, Math.min(host.clientWidth - 24, 780));
        const dpr = Math.min(window.devicePixelRatio || 1, 2.5);

        for (let pageNum = 1; pageNum <= pdfDoc.numPages; pageNum += 1) {
          if (cancelled) break;
          const page = await pdfDoc.getPage(pageNum);
          const unscaled = page.getViewport({ scale: 1 });
          const fitScale = (containerWidth / unscaled.width) * zoom;
          const viewport = page.getViewport({ scale: fitScale });

          const canvas = document.createElement('canvas');
          canvas.width = Math.floor(viewport.width * dpr);
          canvas.height = Math.floor(viewport.height * dpr);
          canvas.style.width = `${Math.floor(viewport.width)}px`;
          canvas.style.height = `${Math.floor(viewport.height)}px`;
          canvas.style.backgroundColor = '#ffffff';
          canvas.style.borderRadius = '8px';
          canvas.style.border = '1px solid var(--border)';
          canvas.style.display = 'block';
          canvas.style.margin = '0 auto';

          const ctx = canvas.getContext('2d');
          if (ctx) {
            ctx.scale(dpr, dpr);
            await page.render({ canvasContext: ctx, viewport }).promise;
          }
          host.appendChild(canvas);
        }

        await pdfDoc.destroy();
        if (!cancelled) setLoading(false);
      } catch (err) {
        if (!cancelled) {
          setErrorMsg(
            err instanceof Error
              ? `Não foi possível renderizar o PDF: ${err.message}`
              : 'Falha ao renderizar o documento PDF.',
          );
          setLoading(false);
        }
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [doc, blob, isPdf, zoom]);

  const [imageUrl, setImageUrl] = useState<string | null>(null);
  useEffect(() => {
    if (!blob || !isImage) {
      setImageUrl(null);
      return;
    }
    const url = URL.createObjectURL(blob);
    setImageUrl(url);
    return () => URL.revokeObjectURL(url);
  }, [blob, isImage]);

  if (!doc) return null;

  const canShare = typeof navigator !== 'undefined' && typeof navigator.share === 'function';

  return (
    <ModalDialogSheet
      open={Boolean(doc)}
      onClose={onClose}
      title={doc.title}
      variant="fullscreen-doc"
      hideHeader
      allowLightDismiss={false}
    >
      {/* Top Bar com Retorno Explicito para iOS Standalone */}
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: '8px',
          padding: 'calc(var(--safe-top) + 10px) 16px 10px',
          backgroundColor: 'var(--surface)',
          borderBottom: '1px solid var(--border)',
          flexShrink: 0,
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: '10px', minWidth: 0, flex: 1 }}>
          <form method="dialog">
            <button
              type="submit"
              value="cancel"
              className="back-header-btn"
              aria-label="Fechar documento"
              style={{ width: '42px', height: '42px', minWidth: '42px', minHeight: '42px' }}
            >
              <X size={19} strokeWidth={1.75} />
            </button>
          </form>
          <div style={{ minWidth: 0, flex: 1 }}>
            <h1
              className="ty-title"
              style={{
                color: 'var(--ink)',
                whiteSpace: 'nowrap',
                overflow: 'hidden',
                textOverflow: 'ellipsis',
              }}
            >
              {doc.title}
            </h1>
            <p className="ty-label" style={{ color: 'var(--muted)' }}>
              {doc.verificationCode
                ? `Código: ${doc.verificationCode}`
                : pageCount > 0
                  ? `${pageCount} página(s) · Leitor integrado`
                  : doc.fileName}
            </p>
          </div>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: '6px', flexShrink: 0 }}>
          {isPdf && (
            <>
              <button
                type="button"
                className="icon-action-btn"
                aria-label="Reduzir zoom"
                onClick={() => setZoom((z) => Math.max(0.75, Math.round((z - 0.25) * 100) / 100))}
              >
                <Minus size={18} strokeWidth={1.75} />
              </button>
              <button
                type="button"
                className="icon-action-btn"
                aria-label="Aumentar zoom"
                onClick={() => setZoom((z) => Math.min(2.25, Math.round((z + 0.25) * 100) / 100))}
              >
                <Plus size={18} strokeWidth={1.75} />
              </button>
            </>
          )}
          {canShare && (
            <button
              type="button"
              className="icon-action-btn"
              aria-label="Compartilhar arquivo"
              onClick={() => void shareOrDownloadCachedFile(core, doc, true)}
            >
              <Share2 size={19} strokeWidth={1.75} />
            </button>
          )}
          <button
            type="button"
            className="icon-action-btn"
            aria-label="Salvar arquivo"
            onClick={() => void shareOrDownloadCachedFile(core, doc, false)}
          >
            <Download size={19} strokeWidth={1.75} />
          </button>
        </div>
      </div>

      {/* Corpo do Documento */}
      <div
        style={{
          flex: 1,
          minHeight: 0,
          overflow: 'auto',
          WebkitOverflowScrolling: 'touch',
          padding: isHtml ? '0' : '16px 12px calc(24px + var(--safe-bottom))',
          display: 'flex',
          flexDirection: 'column',
          gap: '12px',
          backgroundColor: 'var(--ground)',
        }}
      >
        {errorMsg && (
          <ErrorBanner
            banner={{
              title: 'Falha ao abrir documento',
              message: errorMsg,
            }}
          />
        )}

        {loading && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '12px', padding: '12px' }}>
            <SkeletonBox style={{ width: '100%', height: '380px' }} />
          </div>
        )}

        {isPdf && (
          <div
            ref={pdfCanvasHostRef}
            style={{
              display: 'flex',
              flexDirection: 'column',
              gap: '14px',
              alignItems: 'center',
              width: '100%',
            }}
          />
        )}

        {isHtml && htmlText !== null && (
          <SafeHtmlContent html={htmlText} className="document-html-paper" />
        )}

        {isImage && imageUrl && (
          <img
            src={imageUrl}
            alt={doc.title}
            style={{
              maxWidth: '100%',
              height: 'auto',
              margin: '0 auto',
              borderRadius: '12px',
            }}
          />
        )}

        {!isPdf && !isHtml && !isImage && !loading && blob && (
          <div className="editorial-card" style={{ maxWidth: '460px', margin: '24px auto' }}>
            <p className="ty-title" style={{ color: 'var(--ink)' }}>
              {doc.fileName}
            </p>
            <p className="ty-body" style={{ color: 'var(--muted)', marginTop: '6px' }}>
              Este formato ({doc.mimeType}) pode ser salvo ou enviado para outro aplicativo no seu
              dispositivo.
            </p>
            <div style={{ display: 'flex', gap: '8px', marginTop: '16px' }}>
              <button
                type="button"
                className="btn-primary"
                style={{ flex: 1 }}
                onClick={() => void shareOrDownloadCachedFile(core, doc, canShare)}
              >
                <Download size={17} strokeWidth={1.75} />
                <span>{canShare ? 'Compartilhar / Salvar' : 'Baixar arquivo'}</span>
              </button>
            </div>
          </div>
        )}
      </div>
    </ModalDialogSheet>
  );
};
