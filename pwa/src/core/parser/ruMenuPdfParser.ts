import {
  ALERGENICO_LIST,
  CAMPUS_RU_BY_ID,
  CoreResults,
  TIPO_REFEICAO_LIST,
  type Alergenico,
  type CampusRu,
  type CardapioDiaRu,
  type CardapioSemanalRu,
  type CoreResult,
  type ItemCardapioRu,
  type TipoRefeicao,
} from '../model/models';
import { CasAndJsfParser } from './casAndJsfParser';
import { RuAllergenClassifier, type RgbImageBuffer } from './ruAllergenClassifier';

export interface PdfTextSpan {
  readonly text: string;
  readonly x0: number;
  readonly top: number;
  readonly x1: number;
  readonly bottom: number;
}

export interface PositionedGlyph {
  readonly text: string;
  readonly x0: number;
  readonly top: number;
  readonly x1: number;
  readonly bottom: number;
  readonly widthOfSpace: number;
}

export interface FilledPdfRect {
  readonly x0: number;
  readonly top: number;
  readonly x1: number;
  readonly bottom: number;
}

export interface PdfLineSegment {
  readonly x0: number;
  readonly top: number;
  readonly x1: number;
  readonly bottom: number;
}

export interface PdfLocatedImage {
  readonly name: string;
  readonly x0: number;
  readonly top: number;
  readonly x1: number;
  readonly bottom: number;
  readonly rgb: RgbImageBuffer;
}

export interface PdfMenuPageData {
  readonly pageIndex: number;
  readonly pageWidth: number;
  readonly pageHeight: number;
  readonly texts: readonly PdfTextSpan[];
  readonly lines: readonly PdfLineSegment[];
  readonly images: readonly PdfLocatedImage[];
}

interface CellBbox {
  readonly x0: number;
  readonly top: number;
  readonly x1: number;
  readonly bottom: number;
}

interface PdfGrid {
  readonly colBounds: readonly number[];
  readonly rowBounds: readonly number[];
}

interface DayColumnMeta {
  data?: string;
  diaSemana?: string;
}

interface ClassifiedIcon {
  readonly cx: number;
  readonly cy: number;
  readonly alergenicos: readonly Alergenico[];
}

const DATE_REGEX = /(\d{2}\/\d{2}\/\d{4})/;

export const RuMenuPdfParser = {
  parsePages(params: {
    campus: CampusRu;
    semanaLabel: string;
    pdfUrl: string;
    fallbackImageUrl: string | null;
    localPdfPath: string | null;
    modifiedGmt: string;
    pages: readonly PdfMenuPageData[];
  }): CoreResult<CardapioSemanalRu> {
    const slug = CAMPUS_RU_BY_ID[params.campus].slug;
    if (params.pages.length === 0) {
      return CoreResults.failure({
        kind: 'ParseError',
        source: `ru-pdf:${slug}`,
        reason: 'PDF sem paginas extraidas.',
        recoverableFallbackAvailable: true,
      });
    }

    const daysMap = new Map<
      string,
      {
        dataTexto: string;
        diaSemana: string;
        refeicoes: Partial<Record<TipoRefeicao, ItemCardapioRu[]>>;
      }
    >();

    for (const page of params.pages) {
      const mealMeta = TIPO_REFEICAO_LIST.find((m) => m.pageIndex === page.pageIndex);
      if (!mealMeta) continue;
      const meal = mealMeta.id;

      const legendRefs = buildLegendReferences(page.images);
      const grid = reconstructGrid(page.lines);
      if (!grid) continue;

      const rowCount = grid.rowBounds.length - 1;
      const colCount = grid.colBounds.length - 1;
      const cellTexts: string[][] = Array.from({ length: rowCount }, (_, r) =>
        Array.from({ length: colCount }, (__, c) =>
          extractCellText(page.texts, cellBbox(grid, r, c)),
        ),
      );

      const { dayCols, headerRowsEnd } = detectDayColumns(cellTexts);
      const dayColIndices = Array.from(dayCols.keys());
      if (dayColIndices.length === 0) continue;

      const minDayCol = Math.min(...dayColIndices);
      const categoryCol = detectCategoryColumn(grid, cellTexts, minDayCol);

      const classifiedIcons: ClassifiedIcon[] = page.images
        .filter((im) => im.top >= 85 && im.top <= 520)
        .map((im) => ({
          cx: (im.x0 + im.x1) / 2,
          cy: (im.top + im.bottom) / 2,
          alergenicos: RuAllergenClassifier.classifyStrip(im.rgb, legendRefs),
        }));

      let lastCategory: string | null = null;
      for (let r = headerRowsEnd; r < cellTexts.length; r += 1) {
        const rowTexts = cellTexts[r];
        const rawCat = rowTexts[categoryCol]?.trim();
        const category: string | null = rawCat && rawCat.length > 0 ? rawCat : lastCategory;
        if (!category) continue;
        lastCategory = category;

        let sharedDish = '';
        for (const c of dayColIndices) {
          if (c < rowTexts.length && rowTexts[c].trim().length > 0) {
            sharedDish = rowTexts[c];
            break;
          }
        }

        const mergedBbox = isHorizontallyMergedDayRow(page.lines, grid, r, dayColIndices)
          ? findSingleMergedDayBbox(grid, r, dayColIndices)
          : null;
        const mergedDish = mergedBbox
          ? extractCellText(page.texts, mergedBbox) || null
          : null;

        for (const [colIdx, meta] of dayCols.entries()) {
          const dataStr = meta.data ?? `COL_${colIdx}`;
          let dayObj = daysMap.get(dataStr);
          if (!dayObj) {
            dayObj = {
              dataTexto: dataStr,
              diaSemana: meta.diaSemana ?? '',
              refeicoes: {},
            };
            daysMap.set(dataStr, dayObj);
          }

          let mealList = dayObj.refeicoes[meal];
          if (!mealList) {
            mealList = [];
            dayObj.refeicoes[meal] = mealList;
          }

          const rawDish =
            mergedDish ??
            (rowTexts[colIdx]?.trim().length ? rowTexts[colIdx] : sharedDish);
          const dish = rawDish.replace(/\s+/g, ' ').trim();
          if (!dish) continue;

          const box = cellBbox(grid, r, colIdx);
          let allergens = collectAllergens(classifiedIcons, box);
          if (allergens.length === 0 && mergedBbox) {
            allergens = collectAllergens(classifiedIcons, mergedBbox);
          }

          mealList.push({
            categoria: category.replace(/\s+/g, ' ').trim(),
            prato: dish,
            alergenicos: allergens,
          });
        }
      }
    }

    const parsedDays: CardapioDiaRu[] = Array.from(daysMap.values()).filter(
      (d) => Object.keys(d.refeicoes).length > 0,
    );

    if (parsedDays.length === 0) {
      return CoreResults.failure({
        kind: 'ParseError',
        source: `ru-pdf:${slug}`,
        reason: 'Nao foi possivel extrair a tabela semanal do PDF do RU.',
        recoverableFallbackAvailable: true,
      });
    }

    return CoreResults.success({
      campus: params.campus,
      semanaLabel: params.semanaLabel,
      pdfUrl: params.pdfUrl,
      fallbackImageUrl: params.fallbackImageUrl,
      localPdfPath: params.localPdfPath,
      modifiedGmt: params.modifiedGmt,
      parserStatus: 'PARSED_STRUCTURED',
      dias: parsedDays,
    });
  },

  splitLineGlyphsIntoCellSpans(glyphs: readonly PositionedGlyph[]): PdfTextSpan[] {
    if (glyphs.length === 0) return [];
    const spans: PdfTextSpan[] = [];
    let current: PositionedGlyph[] = [];

    const flushCurrent = () => {
      if (current.length === 0) return;
      const joined = current
        .map((g) => g.text)
        .join('')
        .trim();
      if (joined.length > 0) {
        spans.push({
          text: joined,
          x0: Math.min(...current.map((g) => g.x0)),
          top: Math.min(...current.map((g) => g.top)),
          x1: Math.max(...current.map((g) => g.x1)),
          bottom: Math.max(...current.map((g) => g.bottom)),
        });
      }
      current = [];
    };

    let prevGlyph: PositionedGlyph | null = null;
    for (const glyph of glyphs) {
      const isWhitespace = glyph.text.trim().length === 0;
      const safeSpace =
        Number.isFinite(glyph.widthOfSpace) && glyph.widthOfSpace > 0
          ? glyph.widthOfSpace
          : 2.5;
      const gapThreshold = Math.max(safeSpace * 1.4, 3.5);
      const hasColumnGap = prevGlyph !== null && glyph.x0 - prevGlyph.x1 > gapThreshold;

      if (isWhitespace || hasColumnGap) {
        flushCurrent();
      }
      if (!isWhitespace) {
        current.push(glyph);
      }
      prevGlyph = glyph;
    }
    flushCurrent();
    return spans;
  },

  classifyFilledRectSegments(
    rect: FilledPdfRect,
    thinBorderLines: PdfLineSegment[],
    fallbackFillLines: PdfLineSegment[],
  ): void {
    const width = Math.abs(rect.x1 - rect.x0);
    const height = Math.abs(rect.bottom - rect.top);
    if (height <= 3.5 && width >= 15) {
      const midY = (rect.top + rect.bottom) / 2;
      thinBorderLines.push({ x0: rect.x0, top: midY, x1: rect.x1, bottom: midY });
    } else if (width <= 3.5 && height >= 15) {
      const midX = (rect.x0 + rect.x1) / 2;
      thinBorderLines.push({ x0: midX, top: rect.top, x1: midX, bottom: rect.bottom });
    } else if (width >= 15 || height >= 15) {
      fallbackFillLines.push({ x0: rect.x0, top: rect.top, x1: rect.x1, bottom: rect.top });
      fallbackFillLines.push({ x0: rect.x0, top: rect.bottom, x1: rect.x1, bottom: rect.bottom });
      fallbackFillLines.push({ x0: rect.x0, top: rect.top, x1: rect.x0, bottom: rect.bottom });
      fallbackFillLines.push({ x0: rect.x1, top: rect.top, x1: rect.x1, bottom: rect.bottom });
    }
  },

  selectGridLineSegments(
    thinOrStroked: readonly PdfLineSegment[],
    fallbackFillLines: readonly PdfLineSegment[],
  ): PdfLineSegment[] {
    return thinOrStroked.length >= 8
      ? [...thinOrStroked]
      : [...thinOrStroked, ...fallbackFillLines];
  },
};

function cellBbox(grid: PdfGrid, row: number, col: number): CellBbox {
  return {
    x0: grid.colBounds[col],
    top: grid.rowBounds[row],
    x1: grid.colBounds[col + 1],
    bottom: grid.rowBounds[row + 1],
  };
}

function buildLegendReferences(images: readonly PdfLocatedImage[]): Map<Alergenico, Float32Array> {
  const legendImages = images
    .filter((im) => im.top > 530)
    .sort((a, b) => a.x0 - b.x0);
  const map = new Map<Alergenico, Float32Array>();
  const count = Math.min(ALERGENICO_LIST.length, legendImages.length);
  for (let i = 0; i < count; i += 1) {
    map.set(ALERGENICO_LIST[i].id, RuAllergenClassifier.to32x32Vector(legendImages[i].rgb));
  }
  return map;
}

function reconstructGrid(lines: readonly PdfLineSegment[]): PdfGrid | null {
  const xCoords: number[] = [];
  const yCoords: number[] = [];

  for (const seg of lines) {
    const dx = Math.abs(seg.x1 - seg.x0);
    const dy = Math.abs(seg.bottom - seg.top);
    if (dx <= 2.5 && dy >= 15) {
      xCoords.push((seg.x0 + seg.x1) / 2);
    } else if (dy <= 2.5 && dx >= 15) {
      yCoords.push((seg.top + seg.bottom) / 2);
    } else if (dx >= 15 && dy >= 15) {
      xCoords.push(Math.min(seg.x0, seg.x1), Math.max(seg.x0, seg.x1));
      yCoords.push(Math.min(seg.top, seg.bottom), Math.max(seg.top, seg.bottom));
    }
  }

  const colBounds = clusterCoordinates(xCoords, 2.5);
  const rowBounds = clusterCoordinates(yCoords, 2.5);
  if (colBounds.length < 3 || rowBounds.length < 6) return null;
  return { colBounds, rowBounds };
}

function clusterCoordinates(values: readonly number[], tolerance: number): number[] {
  if (values.length === 0) return [];
  const sorted = [...values].sort((a, b) => a - b);
  const clusters: number[] = [];
  let currentSum = sorted[0];
  let count = 1;
  let anchor = sorted[0];

  for (let i = 1; i < sorted.length; i += 1) {
    const v = sorted[i];
    if (v - anchor <= tolerance) {
      currentSum += v;
      count += 1;
    } else {
      clusters.push(currentSum / count);
      currentSum = v;
      count = 1;
      anchor = v;
    }
  }
  clusters.push(currentSum / count);
  return clusters;
}

function extractCellText(spans: readonly PdfTextSpan[], bbox: CellBbox): string {
  const inside = spans
    .filter((span) => {
      const cx = (span.x0 + span.x1) / 2;
      const cy = (span.top + span.bottom) / 2;
      return cx >= bbox.x0 && cx <= bbox.x1 && cy >= bbox.top && cy <= bbox.bottom;
    })
    .sort((a, b) => {
      const rowBucketA = Math.trunc(a.top / 4);
      const rowBucketB = Math.trunc(b.top / 4);
      if (rowBucketA !== rowBucketB) return rowBucketA - rowBucketB;
      return a.x0 - b.x0;
    });

  let out = '';
  let prev: PdfTextSpan | null = null;
  for (const span of inside) {
    if (!prev) {
      out = span.text;
    } else {
      const sameLine = Math.abs(span.top - prev.top) <= 3.5;
      const gap = span.x0 - prev.x1;
      const sep = sameLine && gap >= -1.5 && gap <= 1.35 ? '' : ' ';
      out += `${sep}${span.text}`;
    }
    prev = span;
  }
  return out.replace(/\s+/g, ' ').trim();
}

function detectDayColumns(cellTexts: string[][]): {
  dayCols: Map<number, DayColumnMeta>;
  headerRowsEnd: number;
} {
  const map = new Map<number, DayColumnMeta>();
  const headerLimit = Math.min(8, cellTexts.length);
  let lastHeaderRow = -1;

  for (let r = 0; r < headerLimit; r += 1) {
    for (let c = 0; c < cellTexts[r].length; c += 1) {
      const rawText = cellTexts[r][c];
      if (!rawText) continue;
      // Repair kerning-split date digits such as "2 3/11 /2026" -> "23/11/2026"
      const text = rawText
        .replace(/(\d)\s+(?=\d|\/)/g, '$1')
        .replace(/\/\s+(?=\d)/g, '/');
      const dateMatch = DATE_REGEX.exec(text);
      if (dateMatch) {
        const entry = map.get(c) ?? {};
        entry.data = dateMatch[1];
        map.set(c, entry);
        lastHeaderRow = Math.max(lastHeaderRow, r);
      }
      const textWithoutDate = dateMatch ? text.replace(dateMatch[0], ' ') : text;
      const cleanDayText = textWithoutDate
        .replace(/[()]/g, '')
        .replace(/\s+/g, ' ')
        .trim();
      const norm = CasAndJsfParser.normalizeLabel(cleanDayText);
      if (norm.includes('feira') || norm.includes('sabado') || norm.includes('domingo')) {
        const entry = map.get(c) ?? {};
        entry.diaSemana = cleanDayText;
        map.set(c, entry);
        lastHeaderRow = Math.max(lastHeaderRow, r);
      } else if (norm.includes('composicao')) {
        lastHeaderRow = Math.max(lastHeaderRow, r);
      }
    }
  }

  const dayCols = new Map<number, DayColumnMeta>();
  for (const [col, meta] of map.entries()) {
    if (meta.data !== undefined || meta.diaSemana !== undefined) {
      dayCols.set(col, meta);
    }
  }

  const headerRowsEnd =
    lastHeaderRow >= 0
      ? Math.min(lastHeaderRow + 1, cellTexts.length)
      : Math.min(4, cellTexts.length);
  return { dayCols, headerRowsEnd };
}

function detectCategoryColumn(
  grid: PdfGrid,
  cellTexts: string[][],
  firstDayCol: number,
): number {
  if (firstDayCol <= 0) return 0;
  for (let r = 0; r < cellTexts.length; r += 1) {
    for (let c = 0; c < firstDayCol; c += 1) {
      if (CasAndJsfParser.normalizeLabel(cellTexts[r][c]).includes('composicao')) {
        return c;
      }
    }
  }
  let bestCol = firstDayCol - 1;
  let bestWidth = -1;
  for (let c = 0; c < firstDayCol; c += 1) {
    const w = grid.colBounds[c + 1] - grid.colBounds[c];
    if (w > bestWidth) {
      bestWidth = w;
      bestCol = c;
    }
  }
  return bestCol;
}

function isHorizontallyMergedDayRow(
  lines: readonly PdfLineSegment[],
  grid: PdfGrid,
  row: number,
  dayColIndices: readonly number[],
): boolean {
  if (dayColIndices.length <= 1) return false;
  const rowTop = grid.rowBounds[row];
  const rowBottom = grid.rowBounds[row + 1];
  const rowMidY = (rowTop + rowBottom) / 2;
  const sortedCols = [...dayColIndices].sort((a, b) => a - b);
  const internalColXs = sortedCols.slice(1).map((c) => grid.colBounds[c]);

  const hasInternalVerticalDivider = internalColXs.some((colX) =>
    lines.some((seg) => {
      const isVertical = Math.abs(seg.x1 - seg.x0) <= 2.5;
      const segX = (seg.x0 + seg.x1) / 2;
      const segTop = Math.min(seg.top, seg.bottom);
      const segBottom = Math.max(seg.top, seg.bottom);
      return isVertical && Math.abs(segX - colX) <= 3.0 && segTop <= rowMidY && segBottom >= rowMidY;
    }),
  );
  return !hasInternalVerticalDivider;
}

function findSingleMergedDayBbox(
  grid: PdfGrid,
  row: number,
  dayColIndices: readonly number[],
): CellBbox | null {
  if (dayColIndices.length === 0) return null;
  const minCol = Math.min(...dayColIndices);
  const maxCol = Math.max(...dayColIndices);
  return {
    x0: grid.colBounds[minCol],
    top: grid.rowBounds[row],
    x1: grid.colBounds[maxCol + 1],
    bottom: grid.rowBounds[row + 1],
  };
}

function collectAllergens(icons: readonly ClassifiedIcon[], bbox: CellBbox): Alergenico[] {
  const detected: Alergenico[] = [];
  for (const ic of icons) {
    if (ic.cx >= bbox.x0 && ic.cx <= bbox.x1 && ic.cy >= bbox.top && ic.cy <= bbox.bottom) {
      for (const alg of ic.alergenicos) {
        if (!detected.includes(alg)) {
          detected.push(alg);
        }
      }
    }
  }
  return detected;
}
