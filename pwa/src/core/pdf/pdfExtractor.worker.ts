import * as pdfjs from 'pdfjs-dist';
import pdfjsWorkerUrl from 'pdfjs-dist/build/pdf.worker.min.mjs?url';
import {
  extractCarteirinhaWithPdfJs,
  extractRuMenuPagesWithPdfJs,
  extractTextLinesWithPdfJs,
} from './pdfExtractorCore';

pdfjs.GlobalWorkerOptions.workerSrc = pdfjsWorkerUrl;

export type PdfWorkerRequest =
  | { id: number; op: 'extractCarteirinha'; pdfBytes: Uint8Array }
  | { id: number; op: 'extractTextLines'; pdfBytes: Uint8Array; documentType: string }
  | { id: number; op: 'extractRuMenuPages'; pdfBytes: Uint8Array };

self.onmessage = async (event: MessageEvent<PdfWorkerRequest>) => {
  const msg = event.data;
  try {
    if (msg.op === 'extractCarteirinha') {
      const result = await extractCarteirinhaWithPdfJs(pdfjs as never, msg.pdfBytes);
      self.postMessage({ id: msg.id, result });
    } else if (msg.op === 'extractTextLines') {
      const result = await extractTextLinesWithPdfJs(
        pdfjs as never,
        msg.pdfBytes,
        msg.documentType,
      );
      self.postMessage({ id: msg.id, result });
    } else if (msg.op === 'extractRuMenuPages') {
      const result = await extractRuMenuPagesWithPdfJs(pdfjs as never, msg.pdfBytes);
      self.postMessage({ id: msg.id, result });
    }
  } catch (err) {
    self.postMessage({
      id: msg.id,
      result: {
        ok: false,
        error: {
          kind: 'PdfExtractionError',
          documentType: 'PDF',
          reason: err instanceof Error ? err.message : 'Falha inesperada no worker de PDF.',
        },
      },
    });
  }
};
