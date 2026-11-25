import type { CoreResult } from '../model/models';
import type { CarteirinhaPdfExtractedInput } from '../parser/discenteDocumentosParser';
import type { RgbImageBuffer } from '../parser/ruAllergenClassifier';
import type { PdfMenuPageData } from '../parser/ruMenuPdfParser';

export interface PdfExtractor {
  extractCarteirinha(pdfBytes: Uint8Array): Promise<CoreResult<CarteirinhaPdfExtractedInput>>;
  extractTextLines(pdfBytes: Uint8Array, documentType: string): Promise<CoreResult<string[]>>;
  extractRuMenuPages(pdfBytes: Uint8Array): Promise<CoreResult<PdfMenuPageData[]>>;
}

/**
 * Adaptador que executa extracao de PDFs em um Web Worker dedicado no navegador
 * (mantendo 60fps na UI do iPhone), com fallback automatico em memoria para Vitest/Node.
 */
export class WorkerPdfExtractor implements PdfExtractor {
  private worker: Worker | null = null;
  private nextRequestId = 1;
  private readonly pending = new Map<number, (res: unknown) => void>();

  private getOrCreateWorker(): Worker | null {
    if (this.worker) return this.worker;
    if (typeof Worker === 'undefined') return null;

    try {
      const w = new Worker(new URL('./pdfExtractor.worker.ts', import.meta.url), {
        type: 'module',
      });
      w.onmessage = (ev: MessageEvent<{ id: number; result: unknown }>) => {
        const resolver = this.pending.get(ev.data.id);
        if (resolver) {
          this.pending.delete(ev.data.id);
          resolver(ev.data.result);
        }
      };
      w.onerror = () => {
        this.worker = null;
        for (const [id, resolver] of this.pending.entries()) {
          this.pending.delete(id);
          resolver(null);
        }
      };
      this.worker = w;
      return w;
    } catch {
      return null;
    }
  }

  private async callWorker<T>(
    op: 'extractCarteirinha' | 'extractTextLines' | 'extractRuMenuPages',
    pdfBytes: Uint8Array,
    documentType?: string,
  ): Promise<CoreResult<T> | null> {
    const w = this.getOrCreateWorker();
    if (!w) return null;
    const id = this.nextRequestId++;
    const copy = new Uint8Array(pdfBytes);
    return new Promise<CoreResult<T> | null>((resolve) => {
      const timer = setTimeout(() => {
        if (this.pending.has(id)) {
          this.pending.delete(id);
          resolve(null);
        }
      }, 15_000);
      this.pending.set(id, (res) => {
        clearTimeout(timer);
        const typed = res as CoreResult<T> | null;
        if (!typed || (!typed.ok && typed.error.kind === 'PdfExtractionError')) {
          resolve(null);
          return;
        }
        resolve(typed);
      });
      try {
        w.postMessage({ id, op, pdfBytes: copy, documentType }, [copy.buffer]);
      } catch {
        clearTimeout(timer);
        this.pending.delete(id);
        resolve(null);
      }
    });
  }

  async extractCarteirinha(
    pdfBytes: Uint8Array,
  ): Promise<CoreResult<CarteirinhaPdfExtractedInput>> {
    const viaWorker = await this.callWorker<CarteirinhaPdfExtractedInput>(
      'extractCarteirinha',
      pdfBytes,
    );
    if (viaWorker) return viaWorker;

    const [{ extractCarteirinhaWithPdfJs }, pdfjs] = await Promise.all([
      import('./pdfExtractorCore'),
      import('pdfjs-dist/legacy/build/pdf.mjs'),
    ]);
    return extractCarteirinhaWithPdfJs(pdfjs as never, pdfBytes);
  }

  async extractTextLines(
    pdfBytes: Uint8Array,
    documentType: string,
  ): Promise<CoreResult<string[]>> {
    const viaWorker = await this.callWorker<string[]>(
      'extractTextLines',
      pdfBytes,
      documentType,
    );
    if (viaWorker) return viaWorker;

    const [{ extractTextLinesWithPdfJs }, pdfjs] = await Promise.all([
      import('./pdfExtractorCore'),
      import('pdfjs-dist/legacy/build/pdf.mjs'),
    ]);
    return extractTextLinesWithPdfJs(pdfjs as never, pdfBytes, documentType);
  }

  async extractRuMenuPages(pdfBytes: Uint8Array): Promise<CoreResult<PdfMenuPageData[]>> {
    const viaWorker = await this.callWorker<PdfMenuPageData[]>(
      'extractRuMenuPages',
      pdfBytes,
    );
    if (viaWorker) return viaWorker;

    const [{ extractRuMenuPagesWithPdfJs }, pdfjs] = await Promise.all([
      import('./pdfExtractorCore'),
      import('pdfjs-dist/legacy/build/pdf.mjs'),
    ]);
    return extractRuMenuPagesWithPdfJs(pdfjs as never, pdfBytes);
  }
}

export async function rgbImageBufferToPngBytes(image: RgbImageBuffer): Promise<Uint8Array> {
  if (typeof OffscreenCanvas !== 'undefined') {
    const canvas = new OffscreenCanvas(image.width, image.height);
    const ctx = canvas.getContext('2d');
    if (ctx) {
      const rgba = new Uint8ClampedArray(image.width * image.height * 4);
      for (let i = 0; i < image.width * image.height; i += 1) {
        rgba[i * 4] = image.rgbBytes[i * 3];
        rgba[i * 4 + 1] = image.rgbBytes[i * 3 + 1];
        rgba[i * 4 + 2] = image.rgbBytes[i * 3 + 2];
        rgba[i * 4 + 3] = 255;
      }
      ctx.putImageData(new ImageData(rgba, image.width, image.height), 0, 0);
      const blob = await canvas.convertToBlob({ type: 'image/png' });
      return new Uint8Array(await blob.arrayBuffer());
    }
  }

  const isJsdom =
    typeof navigator !== 'undefined' && /jsdom/i.test(navigator.userAgent ?? '');

  if (!isJsdom && typeof document !== 'undefined') {
    const canvas = document.createElement('canvas');
    canvas.width = image.width;
    canvas.height = image.height;
    const ctx = canvas.getContext('2d');
    if (ctx && typeof canvas.toDataURL === 'function') {
      const rgba = new Uint8ClampedArray(image.width * image.height * 4);
      for (let i = 0; i < image.width * image.height; i += 1) {
        rgba[i * 4] = image.rgbBytes[i * 3];
        rgba[i * 4 + 1] = image.rgbBytes[i * 3 + 1];
        rgba[i * 4 + 2] = image.rgbBytes[i * 3 + 2];
        rgba[i * 4 + 3] = 255;
      }
      ctx.putImageData(new ImageData(rgba, image.width, image.height), 0, 0);
      const dataUrl = canvas.toDataURL('image/png');
      if (dataUrl.startsWith('data:image/png;base64,')) {
        const b64 = dataUrl.slice('data:image/png;base64,'.length);
        const bin = atob(b64);
        const out = new Uint8Array(bin.length);
        for (let i = 0; i < bin.length; i += 1) out[i] = bin.charCodeAt(i);
        return out;
      }
    }
  }

  return encodeUncompressedPng(image);
}

/**
 * Codificador PNG minimo sem dependencias para ambientes de teste (JSDOM/Node)
 * onde `<canvas>` nao possui backend grafico nativo.
 */
function encodeUncompressedPng(image: RgbImageBuffer): Uint8Array {
  const { width, height, rgbBytes } = image;
  const rowLen = 1 + width * 3;
  const rawLen = rowLen * height;

  // Zlib header (0x78, 0x01) + uncompressed DEFLATE blocks (65535 max per block) + Adler32
  const maxBlock = 65535;
  const numBlocks = Math.ceil(rawLen / maxBlock) || 1;
  const idatPayload = new Uint8Array(2 + rawLen + numBlocks * 5 + 4);
  idatPayload[0] = 0x78;
  idatPayload[1] = 0x01;

  const rawScanlines = new Uint8Array(rawLen);
  let adlerA = 1;
  let adlerB = 0;
  for (let y = 0; y < height; y += 1) {
    const rowStart = y * rowLen;
    rawScanlines[rowStart] = 0; // Filter type 0 (None)
    adlerA = (adlerA + 0) % 65521;
    adlerB = (adlerB + adlerA) % 65521;
    const srcStart = y * width * 3;
    for (let x = 0; x < width * 3; x += 1) {
      const b = rgbBytes[srcStart + x];
      rawScanlines[rowStart + 1 + x] = b;
      adlerA = (adlerA + b) % 65521;
      adlerB = (adlerB + adlerA) % 65521;
    }
  }

  let dst = 2;
  let offset = 0;
  for (let b = 0; b < numBlocks; b += 1) {
    const isLast = b === numBlocks - 1;
    const len = Math.min(maxBlock, rawLen - offset);
    idatPayload[dst++] = isLast ? 0x01 : 0x00;
    idatPayload[dst++] = len & 0xff;
    idatPayload[dst++] = (len >> 8) & 0xff;
    const nlen = ~len & 0xffff;
    idatPayload[dst++] = nlen & 0xff;
    idatPayload[dst++] = (nlen >> 8) & 0xff;
    idatPayload.set(rawScanlines.subarray(offset, offset + len), dst);
    dst += len;
    offset += len;
  }

  const adler = ((adlerB << 16) | adlerA) >>> 0;
  idatPayload[dst++] = (adler >>> 24) & 0xff;
  idatPayload[dst++] = (adler >>> 16) & 0xff;
  idatPayload[dst++] = (adler >>> 8) & 0xff;
  idatPayload[dst++] = adler & 0xff;

  const ihdr = new Uint8Array(13);
  const view = new DataView(ihdr.buffer);
  view.setUint32(0, width, false);
  view.setUint32(4, height, false);
  ihdr[8] = 8; // bit depth 8
  ihdr[9] = 2; // color type RGB
  ihdr[10] = 0;
  ihdr[11] = 0;
  ihdr[12] = 0;

  const sig = new Uint8Array([137, 80, 78, 71, 13, 10, 26, 10]);
  const cIhdr = makePngChunk('IHDR', ihdr);
  const cIdat = makePngChunk('IDAT', idatPayload);
  const cIend = makePngChunk('IEND', new Uint8Array(0));

  const out = new Uint8Array(sig.length + cIhdr.length + cIdat.length + cIend.length);
  out.set(sig, 0);
  out.set(cIhdr, sig.length);
  out.set(cIdat, sig.length + cIhdr.length);
  out.set(cIend, sig.length + cIhdr.length + cIdat.length);
  return out;
}

function makePngChunk(type: string, data: Uint8Array): Uint8Array {
  const chunk = new Uint8Array(12 + data.length);
  const view = new DataView(chunk.buffer);
  view.setUint32(0, data.length, false);
  for (let i = 0; i < 4; i += 1) {
    chunk[4 + i] = type.charCodeAt(i);
  }
  chunk.set(data, 8);
  const crc = crc32(chunk.subarray(4, 8 + data.length));
  view.setUint32(8 + data.length, crc, false);
  return chunk;
}

function crc32(bytes: Uint8Array): number {
  let c = 0xffffffff;
  for (let i = 0; i < bytes.length; i += 1) {
    c ^= bytes[i];
    for (let k = 0; k < 8; k += 1) {
      c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    }
  }
  return (c ^ 0xffffffff) >>> 0;
}
