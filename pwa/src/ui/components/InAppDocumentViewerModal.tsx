import { Download, ExternalLink, Minus, Plus, Share2, X } from 'lucide-react';
import React, { useEffect, useRef, useState } from 'react';
import type { UnbPocketCore } from '../../core/model/models';
import { ErrorBanner, SkeletonBox } from './CommonComponents';
import { ModalDialogSheet } from './ModalDialogSheet';
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

type DetectedDocKind = 'pdf' | 'html' | 'image' | 'text' | 'external';

function inferMimeFromNameAndBytes(
  fileName: string,
  declaredMime: string,
  bytes: Uint8Array,
): { kind: DetectedDocKind; mime: string } {
  // 1. Magic-byte signatures always take precedence over generic SIGAA headers
  if (
    bytes.length >= 4 &&
    bytes[0] === 0x25 &&
    bytes[1] === 0x50 &&
    bytes[2] === 0x44 &&
    bytes[3] === 0x46
  ) {
    return { kind: 'pdf', mime: 'application/pdf' };
  }
  if (
    bytes.length >= 8 &&
    bytes[0] === 0x89 &&
    bytes[1] === 0x50 &&
    bytes[2] === 0x4e &&
    bytes[3] === 0x47
  ) {
    return { kind: 'image', mime: 'image/png' };
  }
  if (bytes.length >= 3 && bytes[0] === 0xff && bytes[1] === 0xd8 && bytes[2] === 0xff) {
    return { kind: 'image', mime: 'image/jpeg' };
  }
  if (
    bytes.length >= 4 &&
    bytes[0] === 0x47 &&
    bytes[1] === 0x49 &&
    bytes[2] === 0x46 &&
    bytes[3] === 0x38
  ) {
    return { kind: 'image', mime: 'image/gif' };
  }

  const lowerMime = declaredMime.split(';')[0].trim().toLowerCase();
  const lowerName = fileName.trim().toLowerCase();
  const ext = lowerName.includes('.') ? lowerName.split('.').pop() ?? '' : '';

  if (lowerMime.includes('pdf') || ext === 'pdf') {
    return { kind: 'pdf', mime: 'application/pdf' };
  }
  if (lowerMime.startsWith('image/') || ['png', 'jpg', 'jpeg', 'gif', 'webp', 'svg'].includes(ext)) {
    return { kind: 'image', mime: lowerMime.startsWith('image/') ? lowerMime : `image/${ext === 'jpg' ? 'jpeg' : ext}` };
  }
  if (lowerMime.includes('html') || ext === 'html' || ext === 'htm') {
    return { kind: 'html', mime: 'text/html' };
  }
  if (
    lowerMime.startsWith('text/') ||
    lowerMime === 'application/json' ||
    ['txt', 'csv', 'md', 'json', 'xml', 'py', 'c', 'cpp', 'h', 'java', 'kt', 'js', 'ts', 'sql', 'log'].includes(
      ext,
    )
  ) {
    return { kind: 'text', mime: lowerMime || 'text/plain' };
  }

  // Sniff leading ASCII/UTF-8 text if small enough and free of NUL bytes
  if (bytes.length > 0 && bytes.length <= 512 * 1024) {
    const sampleLen = Math.min(bytes.length, 512);
    let hasNullByte = false;
    for (let i = 0; i < sampleLen; i += 1) {
      if (bytes[i] === 0) {
        hasNullByte = true;
        break;
      }
    }
    if (!hasNullByte) {
      const sampleText = new TextDecoder('utf-8', { fatal: false }).decode(bytes.subarray(0, sampleLen)).trimStart().toLowerCase();
      if (sampleText.startsWith('<!doctype html') || sampleText.startsWith('<html')) {
        return { kind: 'html', mime: 'text/html' };
      }
    }
  }

  return { kind: 'external', mime: lowerMime || 'application/octet-stream' };
}

export async function shareOrDownloadCachedFile(
  core: UnbPocketCore,
  doc: ViewableDocumentRequest,
  preferShare: boolean,
): Promise<boolean> {
  const blob = await core.readCachedBlob(doc.localFilePath);
  if (!blob) return false;

  const bytes = new Uint8Array(await blob.arrayBuffer());
  const detected = inferMimeFromNameAndBytes(doc.fileName, doc.mimeType || blob.type, bytes);
  const ext =
    detected.kind === 'pdf'
      ? '.pdf'
      : detected.kind === 'html'
        ? '.html'
        : '';
  const safeFileName = doc.fileName.includes('.') ? doc.fileName : `${doc.fileName}${ext}`;
  const typedBlob = new Blob([bytes], { type: detected.mime });

  if (preferShare && typeof navigator !== 'undefined' && typeof navigator.share === 'function') {
    try {
      const file = new File([typedBlob], safeFileName, { type: detected.mime });
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

  const objectUrl = URL.createObjectURL(typedBlob);
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
  const [pdfBytes, setPdfBytes] = useState<Uint8Array | null>(null);
  const [docKind, setDocKind] = useState<DetectedDocKind | null>(null);
  const [resolvedMime, setResolvedMime] = useState<string>('');
  const [htmlText, setHtmlText] = useState<string | null>(null);
  const [plainText, setPlainText] = useState<string | null>(null);
  const [imageUrl, setImageUrl] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [zoom, setZoom] = useState(1);
  const [pageCount, setPageCount] = useState(0);
  const pdfCanvasHostRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    if (!doc) {
      setPdfBytes(null);
      setDocKind(null);
      setResolvedMime('');
      setHtmlText(null);
      setPlainText(null);
      setImageUrl(null);
      setErrorMsg(null);
      setZoom(1);
      setPageCount(0);
      return;
    }

    let cancelled = false;
    let activeImgUrl: string | null = null;
    setLoading(true);
    setErrorMsg(null);
    setPdfBytes(null);
    setDocKind(null);
    setHtmlText(null);
    setPlainText(null);
    setImageUrl(null);
    setPageCount(0);

    void core.readCachedBlob(doc.localFilePath).then(async (loaded) => {
      if (cancelled) return;
      if (!loaded) {
        setErrorMsg('O arquivo não está mais disponível no armazenamento local.');
        setLoading(false);
        return;
      }

      const rawBytes = new Uint8Array(await loaded.arrayBuffer());
      if (cancelled) return;

      const detected = inferMimeFromNameAndBytes(doc.fileName, doc.mimeType, rawBytes);
      setDocKind(detected.kind);
      setResolvedMime(detected.mime);

      if (detected.kind === 'pdf') {
        setPdfBytes(rawBytes);
        return;
      }

      if (detected.kind === 'html') {
        const text = new TextDecoder('utf-8', { fatal: false }).decode(rawBytes);
        const parsed = new DOMParser().parseFromString(text, 'text/html');
        setHtmlText(parsed.body ? parsed.body.innerHTML : text);
        setLoading(false);
        return;
      }

      if (detected.kind === 'text') {
        const text = new TextDecoder('utf-8', { fatal: false }).decode(rawBytes);
        setPlainText(text);
        setLoading(false);
        return;
      }

      if (detected.kind === 'image') {
        const imgBlob = new Blob([rawBytes], { type: detected.mime });
        activeImgUrl = URL.createObjectURL(imgBlob);
        setImageUrl(activeImgUrl);
        setLoading(false);
        return;
      }

      setLoading(false);
    });

    return () => {
      cancelled = true;
      if (activeImgUrl) {
        URL.revokeObjectURL(activeImgUrl);
      }
    };
  }, [doc, core]);

  useEffect(() => {
    if (!doc || docKind !== 'pdf' || !pdfBytes) return;
    const host = pdfCanvasHostRef.current;
    if (!host) return;

    let cancelled = false;
    setLoading(true);
    setErrorMsg(null);

    void (async () => {
      try {
        const [pdfjs, pdfjsWorker] = await Promise.all([
          import('pdfjs-dist'),
          import('pdfjs-dist/build/pdf.worker.min.mjs'),
        ]);
        (globalThis as unknown as { pdfjsWorker?: unknown }).pdfjsWorker = pdfjsWorker;

        const loadingTask = pdfjs.getDocument({
          data: new Uint8Array(pdfBytes),
          isEvalSupported: false,
        });
        const pdfDoc = await loadingTask.promise;
        if (cancelled) {
          await pdfDoc.destroy();
          return;
        }

        setPageCount(pdfDoc.numPages);
        host.replaceChildren();

        const containerWidth = Math.max(280, Math.min((host.clientWidth || window.innerWidth) - 24, 780));
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
  }, [doc, docKind, pdfBytes, zoom]);

  if (!doc) return null;

  const isPdf = docKind === 'pdf';
  const isHtml = docKind === 'html';
  const isImage = docKind === 'image';
  const isText = docKind === 'text';
  const isExternal = docKind === 'external';
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
          padding: isHtml ? '12px' : '16px 12px calc(24px + var(--safe-bottom))',
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

        {isText && plainText !== null && (
          <pre
            className="document-html-paper ty-mono"
            style={{
              whiteSpace: 'pre-wrap',
              wordBreak: 'break-word',
              margin: '0 auto',
            }}
          >
            {plainText}
          </pre>
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

        {isExternal && !loading && (
          <div className="editorial-card" style={{ maxWidth: '460px', margin: '24px auto' }}>
            <p className="ty-title" style={{ color: 'var(--ink)' }}>
              {doc.fileName}
            </p>
            <p className="ty-body" style={{ color: 'var(--muted)', marginTop: '6px' }}>
              {canShare
                ? `Arquivos deste tipo (${resolvedMime}) não possuem pré-visualização interna no navegador. Toque abaixo para abrir na Visualização Rápida ou em outro app do iPhone.`
                : `Arquivos deste tipo (${resolvedMime}) não possuem pré-visualização interna no navegador e podem ser salvos no dispositivo.`}
            </p>
            <div style={{ display: 'flex', gap: '8px', marginTop: '16px' }}>
              <button
                type="button"
                className="btn-primary"
                style={{ flex: 1 }}
                onClick={() => void shareOrDownloadCachedFile(core, doc, canShare)}
              >
                {canShare ? (
                  <>
                    <ExternalLink size={17} strokeWidth={1.75} />
                    <span>Abrir em outro app / Visualização Rápida</span>
                  </>
                ) : (
                  <>
                    <Download size={17} strokeWidth={1.75} />
                    <span>Salvar arquivo</span>
                  </>
                )}
              </button>
            </div>
          </div>
        )}
      </div>
    </ModalDialogSheet>
  );
};
