import {
  CoreResults,
  type CoreResult,
} from '../model/models';
import type { CarteirinhaPdfExtractedInput } from '../parser/discenteDocumentosParser';
import {
  createRgbImageBuffer,
  type RgbImageBuffer,
} from '../parser/ruAllergenClassifier';
import {
  RuMenuPdfParser,
  type FilledPdfRect,
  type PdfLineSegment,
  type PdfLocatedImage,
  type PdfMenuPageData,
  type PdfTextSpan,
} from '../parser/ruMenuPdfParser';

type Matrix6 = [number, number, number, number, number, number];

interface PdfJsLikeModule {
  OPS: Record<string, number>;
  ImageKind?: {
    GRAYSCALE_1BPP?: number;
    RGB_24BPP?: number;
    RGBA_32BPP?: number;
  };
  GlobalWorkerOptions?: {
    workerSrc: string;
  };
  getDocument(params: {
    data: Uint8Array;
    disableFontFace?: boolean;
    useSystemFonts?: boolean;
    isEvalSupported?: boolean;
  }): {
    promise: Promise<PdfJsDocumentProxy>;
  };
}

interface PdfJsDocumentProxy {
  numPages: number;
  getPage(pageNumber: number): Promise<PdfJsPageProxy>;
  destroy(): Promise<void>;
}

interface PdfJsObjStore {
  get(objId: string, callback?: (data: unknown) => void): unknown;
}

interface PdfJsPageProxy {
  view: number[]; // [x0, y0, x1, y1]
  objs: PdfJsObjStore;
  commonObjs: PdfJsObjStore;
  getTextContent(): Promise<{ items: unknown[] }>;
  getOperatorList(): Promise<{ fnArray: number[]; argsArray: unknown[][] }>;
}

interface PdfJsRawImage {
  width?: number;
  height?: number;
  kind?: number;
  data?: Uint8Array | Uint8ClampedArray;
  bitmap?: ImageBitmap;
}

function multiplyMatrix(m1: Matrix6, m2: Matrix6): Matrix6 {
  return [
    m1[0] * m2[0] + m1[2] * m2[1],
    m1[1] * m2[0] + m1[3] * m2[1],
    m1[0] * m2[2] + m1[2] * m2[3],
    m1[1] * m2[2] + m1[3] * m2[3],
    m1[0] * m2[4] + m1[2] * m2[5] + m1[4],
    m1[1] * m2[4] + m1[3] * m2[5] + m1[5],
  ];
}

function transformPoint(m: Matrix6, x: number, y: number): [number, number] {
  return [m[0] * x + m[2] * y + m[4], m[1] * x + m[3] * y + m[5]];
}

async function resolvePdfObject(page: PdfJsPageProxy, objId: string): Promise<unknown> {
  const store = objId.startsWith('g_') ? page.commonObjs : page.objs;
  return new Promise((resolve) => {
    let settled = false;
    const timer = setTimeout(() => {
      if (!settled) {
        settled = true;
        resolve(null);
      }
    }, 1500);

    try {
      store.get(objId, (data: unknown) => {
        if (!settled) {
          settled = true;
          clearTimeout(timer);
          resolve(data);
        }
      });
    } catch {
      if (!settled) {
        settled = true;
        clearTimeout(timer);
        resolve(null);
      }
    }
  });
}

function convertRawPdfImageToRgb(
  raw: PdfJsRawImage | null | undefined,
  imageKind?: PdfJsLikeModule['ImageKind'],
): RgbImageBuffer | null {
  if (!raw) return null;
  const w = raw.width ?? raw.bitmap?.width ?? 0;
  const h = raw.height ?? raw.bitmap?.height ?? 0;
  if (w <= 0 || h <= 0) return null;

  if (raw.data && raw.data.length > 0) {
    const data = raw.data;
    const rgbKind = imageKind?.RGB_24BPP ?? 2;
    const rgbaKind = imageKind?.RGBA_32BPP ?? 3;
    const grayKind = imageKind?.GRAYSCALE_1BPP ?? 1;

    if (raw.kind === rgbKind || data.length === w * h * 3) {
      return createRgbImageBuffer(w, h, new Uint8Array(data.buffer, data.byteOffset, w * h * 3));
    }

    if (raw.kind === rgbaKind || data.length === w * h * 4) {
      const rgb = new Uint8Array(w * h * 3);
      for (let i = 0; i < w * h; i += 1) {
        const src = i * 4;
        const alpha = data[src + 3] / 255;
        const dst = i * 3;
        // Composite onto white background like PDF page canvas
        rgb[dst] = Math.round(data[src] * alpha + 255 * (1 - alpha));
        rgb[dst + 1] = Math.round(data[src + 1] * alpha + 255 * (1 - alpha));
        rgb[dst + 2] = Math.round(data[src + 2] * alpha + 255 * (1 - alpha));
      }
      return createRgbImageBuffer(w, h, rgb);
    }

    if (raw.kind === grayKind) {
      const rgb = new Uint8Array(w * h * 3);
      const rowBytes = Math.ceil(w / 8);
      for (let y = 0; y < h; y += 1) {
        for (let x = 0; x < w; x += 1) {
          const byteIdx = y * rowBytes + (x >> 3);
          const bit = (data[byteIdx] >> (7 - (x & 7))) & 1;
          const val = bit ? 255 : 0;
          const dst = (y * w + x) * 3;
          rgb[dst] = val;
          rgb[dst + 1] = val;
          rgb[dst + 2] = val;
        }
      }
      return createRgbImageBuffer(w, h, rgb);
    }
  }

  if (raw.bitmap && typeof OffscreenCanvas !== 'undefined') {
    const canvas = new OffscreenCanvas(w, h);
    const ctx = canvas.getContext('2d');
    if (ctx) {
      ctx.fillStyle = '#FFFFFF';
      ctx.fillRect(0, 0, w, h);
      ctx.drawImage(raw.bitmap, 0, 0, w, h);
      const imgData = ctx.getImageData(0, 0, w, h).data;
      const rgb = new Uint8Array(w * h * 3);
      for (let i = 0; i < w * h; i += 1) {
        rgb[i * 3] = imgData[i * 4];
        rgb[i * 3 + 1] = imgData[i * 4 + 1];
        rgb[i * 3 + 2] = imgData[i * 4 + 2];
      }
      return createRgbImageBuffer(w, h, rgb);
    }
  }

  return null;
}

function extractTextSpansFromItems(
  items: readonly unknown[],
  pageHeight: number,
): PdfTextSpan[] {
  const spans: PdfTextSpan[] = [];
  for (const rawItem of items) {
    const item = rawItem as {
      str?: string;
      transform?: number[];
      width?: number;
      height?: number;
    };
    if (!item || typeof item.str !== 'string' || item.str.trim().length === 0) {
      continue;
    }
    const tr = item.transform ?? [1, 0, 0, 1, 0, 0];
    const tx = tr[4] ?? 0;
    const ty = tr[5] ?? 0;
    const totalW = Math.max(0.5, item.width ?? 0);
    const fontH = Math.max(1, item.height || Math.hypot(tr[2] ?? 0, tr[3] ?? 0) || Math.abs(tr[0] ?? 10));
    const top = pageHeight - (ty + fontH);
    const bottom = pageHeight - ty;

    // Split wide multi-space runs inside a single text item so neighboring columns never merge
    const parts = item.str.split(/(\s{2,})/);
    if (parts.length <= 1) {
      spans.push({
        text: item.str.trim(),
        x0: tx,
        top: Math.min(top, bottom),
        x1: tx + totalW,
        bottom: Math.max(top, bottom),
      });
    } else {
      const totalChars = Math.max(1, item.str.length);
      let cursorChars = 0;
      for (const part of parts) {
        const partLen = part.length;
        const trimmed = part.trim();
        if (trimmed.length > 0) {
          const partX0 = tx + (cursorChars / totalChars) * totalW;
          const partX1 = tx + ((cursorChars + partLen) / totalChars) * totalW;
          spans.push({
            text: trimmed,
            x0: partX0,
            top: Math.min(top, bottom),
            x1: partX1,
            bottom: Math.max(top, bottom),
          });
        }
        cursorChars += partLen;
      }
    }
  }
  return spans;
}

export async function extractCarteirinhaWithPdfJs(
  pdfjs: PdfJsLikeModule,
  pdfBytes: Uint8Array,
): Promise<CoreResult<CarteirinhaPdfExtractedInput>> {
  let doc: PdfJsDocumentProxy | null = null;
  try {
    doc = await pdfjs.getDocument({
      data: new Uint8Array(pdfBytes),
      disableFontFace: true,
      useSystemFonts: false,
      isEvalSupported: false,
    }).promise;

    if (doc.numPages === 0) {
      return CoreResults.failure({
        kind: 'PdfExtractionError',
        documentType: 'Carteirinha Estudantil',
        reason: 'PDF sem paginas.',
      });
    }

    const page = await doc.getPage(1);
    const textContent = await page.getTextContent();
    const pageHeight = Math.abs((page.view[3] ?? 310) - (page.view[1] ?? 0));

    // Group text items by vertical line bucket
    const rawItems = textContent.items
      .map((it) => it as { str?: string; transform?: number[]; hasEOL?: boolean })
      .filter((it) => typeof it.str === 'string');

    const lines: string[] = [];
    let currentLine = '';
    let lastY: number | null = null;

    for (const item of rawItems) {
      const y = item.transform?.[5] ?? 0;
      if (lastY !== null && Math.abs(y - lastY) > 3.5) {
        if (currentLine.trim()) lines.push(currentLine.trim());
        currentLine = item.str ?? '';
      } else {
        currentLine += (currentLine && !currentLine.endsWith(' ') ? ' ' : '') + (item.str ?? '');
      }
      lastY = y;
      if (item.hasEOL) {
        if (currentLine.trim()) lines.push(currentLine.trim());
        currentLine = '';
        lastY = null;
      }
    }
    if (currentLine.trim()) lines.push(currentLine.trim());

    const opList = await page.getOperatorList();
    const OPS = pdfjs.OPS;

    let img2Foto: RgbImageBuffer | null = null;
    let img4Qr: RgbImageBuffer | null = null;
    const seenIds = new Set<string>();

    for (let i = 0; i < opList.fnArray.length; i += 1) {
      const fn = opList.fnArray[i];
      const args = opList.argsArray[i];
      if (fn === OPS.paintImageXObject || fn === OPS.paintImageXObjectRepeat) {
        const objId = String(args?.[0] ?? '');
        if (!objId || seenIds.has(objId)) continue;
        seenIds.add(objId);
        const rawImg = (await resolvePdfObject(page, objId)) as PdfJsRawImage | null;
        const rgb = convertRawPdfImageToRgb(rawImg, pdfjs.ImageKind);
        if (rgb) {
          if ((rgb.width === 250 && rgb.height === 250) || objId.includes('img4')) {
            img4Qr = rgb;
          } else if ((rgb.width === 500 && rgb.height === 625) || objId.includes('img2')) {
            img2Foto = rgb;
          } else if (!img4Qr && Math.abs(rgb.width - rgb.height) <= 4 && rgb.width >= 120 && rgb.width <= 420) {
            img4Qr = rgb;
          }
        }
      } else if (fn === OPS.paintInlineImageXObject) {
        const rawImg = args?.[0] as PdfJsRawImage | undefined;
        const rgb = convertRawPdfImageToRgb(rawImg, pdfjs.ImageKind);
        if (rgb && rgb.width === 250 && rgb.height === 250 && !img4Qr) {
          img4Qr = rgb;
        }
      }
    }

    // Suppress unused pageHeight warning
    void pageHeight;

    return CoreResults.success({
      textLines: lines.map((l) => l.replace(/\s+/g, ' ').trim()).filter((l) => l.length > 0),
      img2Foto,
      img4Qr,
    });
  } catch (err) {
    return CoreResults.failure({
      kind: 'PdfExtractionError',
      documentType: 'Carteirinha Estudantil',
      reason: err instanceof Error ? err.message : 'Erro ao ler PDF da carteirinha.',
    });
  } finally {
    if (doc) {
      await doc.destroy().catch(() => undefined);
    }
  }
}

export async function extractTextLinesWithPdfJs(
  pdfjs: PdfJsLikeModule,
  pdfBytes: Uint8Array,
  documentType: string,
): Promise<CoreResult<string[]>> {
  let doc: PdfJsDocumentProxy | null = null;
  try {
    doc = await pdfjs.getDocument({
      data: new Uint8Array(pdfBytes),
      disableFontFace: true,
      useSystemFonts: false,
      isEvalSupported: false,
    }).promise;

    const allLines: string[] = [];
    for (let p = 1; p <= doc.numPages; p += 1) {
      const page = await doc.getPage(p);
      const tc = await page.getTextContent();
      const pageText = tc.items
        .map((it) => (it as { str?: string }).str ?? '')
        .join(' ')
        .replace(/\s+/g, ' ')
        .trim();
      if (pageText) {
        allLines.push(pageText);
      }
    }
    return CoreResults.success(allLines);
  } catch (err) {
    return CoreResults.failure({
      kind: 'PdfExtractionError',
      documentType,
      reason: err instanceof Error ? err.message : 'Erro ao extrair texto do PDF.',
    });
  } finally {
    if (doc) {
      await doc.destroy().catch(() => undefined);
    }
  }
}

export async function extractRuMenuPagesWithPdfJs(
  pdfjs: PdfJsLikeModule,
  pdfBytes: Uint8Array,
): Promise<CoreResult<PdfMenuPageData[]>> {
  let doc: PdfJsDocumentProxy | null = null;
  try {
    doc = await pdfjs.getDocument({
      data: new Uint8Array(pdfBytes),
      disableFontFace: true,
      useSystemFonts: false,
      isEvalSupported: false,
    }).promise;

    const OPS = pdfjs.OPS;
    const pages: PdfMenuPageData[] = [];

    for (let pageNum = 1; pageNum <= doc.numPages; pageNum += 1) {
      const page = await doc.getPage(pageNum);
      const pageWidth = Math.abs((page.view[2] ?? 842) - (page.view[0] ?? 0));
      const pageHeight = Math.abs((page.view[3] ?? 595) - (page.view[1] ?? 0));

      const textContent = await page.getTextContent();
      const texts = extractTextSpansFromItems(textContent.items, pageHeight);

      const opList = await page.getOperatorList();
      const thinBorderLines: PdfLineSegment[] = [];
      const fallbackFillLines: PdfLineSegment[] = [];
      const images: PdfLocatedImage[] = [];
      const decodedCache = new Map<string, RgbImageBuffer>();

      let ctm: Matrix6 = [1, 0, 0, 1, 0, 0];
      const ctmStack: Matrix6[] = [];
      let pendingSegments: PdfLineSegment[] = [];
      let pendingRects: FilledPdfRect[] = [];
      let curX = 0;
      let curY = 0;
      let startX = 0;
      let startY = 0;
      let imageCounter = 0;

      const addSegmentPdfCoords = (x0: number, y0: number, x1: number, y1: number) => {
        const [px0, py0] = transformPoint(ctm, x0, y0);
        const [px1, py1] = transformPoint(ctm, x1, y1);
        pendingSegments.push({
          x0: px0,
          top: pageHeight - py0,
          x1: px1,
          bottom: pageHeight - py1,
        });
      };

      const addRectPdfCoords = (rx: number, ry: number, rw: number, rh: number) => {
        const [p0x, p0y] = transformPoint(ctm, rx, ry);
        const [p1x, p1y] = transformPoint(ctm, rx + rw, ry + rh);
        const x0 = Math.min(p0x, p1x);
        const x1 = Math.max(p0x, p1x);
        const top = pageHeight - Math.max(p0y, p1y);
        const bottom = pageHeight - Math.min(p0y, p1y);
        pendingRects.push({ x0, top, x1, bottom });
        pendingSegments.push({ x0, top, x1, bottom: top });
        pendingSegments.push({ x0, top: bottom, x1, bottom });
        pendingSegments.push({ x0, top, x1: x0, bottom });
        pendingSegments.push({ x0: x1, top, x1, bottom });
      };

      const flushStroke = () => {
        thinBorderLines.push(...pendingSegments);
        pendingSegments = [];
        pendingRects = [];
      };

      const flushFill = () => {
        if (pendingRects.length > 0) {
          for (const rect of pendingRects) {
            RuMenuPdfParser.classifyFilledRectSegments(rect, thinBorderLines, fallbackFillLines);
          }
        } else {
          for (const seg of pendingSegments) {
            const dx = Math.abs(seg.x1 - seg.x0);
            const dy = Math.abs(seg.bottom - seg.top);
            if ((dx <= 3.5 && dy >= 15) || (dy <= 3.5 && dx >= 15)) {
              thinBorderLines.push(seg);
            } else if (dx >= 15 || dy >= 15) {
              fallbackFillLines.push(seg);
            }
          }
        }
        pendingSegments = [];
        pendingRects = [];
      };

      for (let i = 0; i < opList.fnArray.length; i += 1) {
        const fn = opList.fnArray[i];
        const args = opList.argsArray[i] ?? [];

        if (fn === OPS.save) {
          ctmStack.push([...ctm]);
        } else if (fn === OPS.restore) {
          const popped = ctmStack.pop();
          if (popped) ctm = popped;
        } else if (fn === OPS.transform) {
          const m = args as number[];
          if (m.length >= 6) {
            ctm = multiplyMatrix(ctm, [m[0], m[1], m[2], m[3], m[4], m[5]]);
          }
        } else if (fn === OPS.constructPath) {
          const subOps = (args[0] as ArrayLike<number>) ?? [];
          const subArgs = (args[1] as ArrayLike<number>) ?? [];
          let j = 0;
          for (let k = 0; k < subOps.length; k += 1) {
            const subOp = subOps[k] | 0;
            if (subOp === OPS.rectangle) {
              const rx = subArgs[j++];
              const ry = subArgs[j++];
              const rw = subArgs[j++];
              const rh = subArgs[j++];
              addRectPdfCoords(rx, ry, rw, rh);
              curX = rx;
              curY = ry;
              startX = rx;
              startY = ry;
            } else if (subOp === OPS.moveTo) {
              curX = subArgs[j++];
              curY = subArgs[j++];
              startX = curX;
              startY = curY;
            } else if (subOp === OPS.lineTo) {
              const nx = subArgs[j++];
              const ny = subArgs[j++];
              addSegmentPdfCoords(curX, curY, nx, ny);
              pendingRects = [];
              curX = nx;
              curY = ny;
            } else if (subOp === OPS.curveTo) {
              curX = subArgs[j + 4];
              curY = subArgs[j + 5];
              j += 6;
              pendingRects = [];
            } else if (subOp === OPS.curveTo2 || subOp === OPS.curveTo3) {
              curX = subArgs[j + 2];
              curY = subArgs[j + 3];
              j += 4;
              pendingRects = [];
            } else if (subOp === OPS.closePath) {
              addSegmentPdfCoords(curX, curY, startX, startY);
              curX = startX;
              curY = startY;
            }
          }
        } else if (fn === OPS.rectangle) {
          const m = args as number[];
          addRectPdfCoords(m[0], m[1], m[2], m[3]);
        } else if (fn === OPS.moveTo) {
          const m = args as number[];
          curX = m[0];
          curY = m[1];
          startX = curX;
          startY = curY;
        } else if (fn === OPS.lineTo) {
          const m = args as number[];
          addSegmentPdfCoords(curX, curY, m[0], m[1]);
          pendingRects = [];
          curX = m[0];
          curY = m[1];
        } else if (fn === OPS.stroke || fn === OPS.closeStroke) {
          flushStroke();
        } else if (fn === OPS.fill || fn === OPS.eoFill) {
          flushFill();
        } else if (
          fn === OPS.fillStroke ||
          fn === OPS.eoFillStroke ||
          fn === OPS.closeFillStroke ||
          fn === OPS.closeEOFillStroke
        ) {
          flushStroke();
        } else if (fn === OPS.endPath) {
          pendingSegments = [];
          pendingRects = [];
        } else if (fn === OPS.paintImageXObject || fn === OPS.paintInlineImageXObject) {
          let rgb: RgbImageBuffer | null = null;
          if (fn === OPS.paintImageXObject) {
            const objId = String(args[0] ?? '');
            rgb = decodedCache.get(objId) ?? null;
            if (!rgb && objId) {
              const rawImg = (await resolvePdfObject(page, objId)) as PdfJsRawImage | null;
              rgb = convertRawPdfImageToRgb(rawImg, pdfjs.ImageKind);
              if (rgb) decodedCache.set(objId, rgb);
            }
          } else {
            rgb = convertRawPdfImageToRgb(args[0] as PdfJsRawImage | undefined, pdfjs.ImageKind);
          }

          if (rgb) {
            const [p0x, p0y] = transformPoint(ctm, 0, 0);
            const [p1x, p1y] = transformPoint(ctm, 1, 1);
            const x0 = Math.min(p0x, p1x);
            const x1 = Math.max(p0x, p1x);
            const top = pageHeight - Math.max(p0y, p1y);
            const bottom = pageHeight - Math.min(p0y, p1y);
            if (x1 - x0 > 0 && bottom - top > 0) {
              images.push({
                name: `img_${imageCounter++}`,
                x0,
                top,
                x1,
                bottom,
                rgb,
              });
            }
          }
        }
      }

      pages.push({
        pageIndex: pageNum - 1,
        pageWidth,
        pageHeight,
        texts,
        lines: RuMenuPdfParser.selectGridLineSegments(thinBorderLines, fallbackFillLines),
        images,
      });
    }

    return CoreResults.success(pages);
  } catch (err) {
    return CoreResults.failure({
      kind: 'PdfExtractionError',
      documentType: 'Cardapio RU',
      reason: err instanceof Error ? err.message : 'Erro ao ler PDF do cardapio RU.',
    });
  } finally {
    if (doc) {
      await doc.destroy().catch(() => undefined);
    }
  }
}
