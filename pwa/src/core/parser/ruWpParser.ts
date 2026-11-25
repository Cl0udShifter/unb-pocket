import {
  CAMPUS_RU_LIST,
  CoreResults,
  GRUPO_ACESSO_RU_BY_ID,
  type CampusRu,
  type CoreResult,
  type GrupoAcessoRu,
  type NoticiaRu,
  type PrecoGrupoRu,
  type TabelaPrecosRu,
} from '../model/models';
import { formatIsoDate, getBrasiliaParts, isValidCalendarDate } from './brasiliaTime';
import { CasAndJsfParser, parseHtmlDocument, parseHtmlFragmentText } from './casAndJsfParser';
import { RuCalculator } from './ruCalculator';

export interface CampusPdfLink {
  readonly campus: CampusRu;
  readonly semanaLabel: string;
  readonly pdfUrl: string;
}

export interface RuWpMenuPage {
  readonly modifiedGmt: string;
  readonly campusLinks: Partial<Record<CampusRu, CampusPdfLink>>;
}

export interface RuWpMediaItem {
  readonly sourceUrl: string;
  readonly title: string;
  readonly dateGmt: string;
  readonly fallbackFullImageUrl: string | null;
}

interface WpPageDto {
  id?: number;
  modified_gmt?: string;
  content?: { rendered?: string };
}

interface WpMediaDto {
  date_gmt?: string;
  title?: { rendered?: string };
  source_url?: string;
  media_details?: {
    sizes?: {
      full?: { source_url?: string };
    };
  };
}

interface WpPostDto {
  id?: number;
  date?: string;
  link?: string;
  title?: { rendered?: string };
  excerpt?: { rendered?: string };
  content?: { rendered?: string };
  _embedded?: {
    'wp:featuredmedia'?: Array<{ source_url?: string }>;
  };
}

const DATE_RANGE_REGEX = /(\d{2})\/(\d{2})\/(\d{4})\s*[aàAÀ-]+\s*(\d{2})\/(\d{2})\/(\d{4})/;

function parseBrDateToIso(dd: string, mm: string, yyyy: string): string | null {
  const day = Number.parseInt(dd, 10);
  const month = Number.parseInt(mm, 10);
  const year = Number.parseInt(yyyy, 10);
  if (!isValidCalendarDate(year, month, day)) return null;
  return formatIsoDate(year, month, day);
}

function cleanText(node: Node | null | undefined): string {
  return (node?.textContent ?? '').replace(/\s+/g, ' ').trim();
}

export const RuWpParser = {
  parseMenuPage(
    jsonBody: string,
    referenceIsoDate: string = getBrasiliaParts(Date.now()).isoDate,
  ): CoreResult<RuWpMenuPage> {
    try {
      const page = JSON.parse(jsonBody) as WpPageDto;
      const rendered = page?.content?.rendered ?? '';
      const modifiedGmt = page?.modified_gmt ?? '';
      const doc = parseHtmlDocument(rendered);

      const candidatesByCampus = new Map<CampusRu, CampusPdfLink[]>();
      let currentCampus: CampusRu | null = null;

      const elements = Array.from(doc.querySelectorAll('h2, h3, a[href*=".pdf"], a[href*=".PDF"]'));
      for (const el of elements) {
        const tag = el.tagName.toLowerCase();
        if (tag === 'h2' || tag === 'h3') {
          const normalizedHeading = CasAndJsfParser.normalizeLabel(cleanText(el));
          if (normalizedHeading.includes('ultimas noticias')) {
            currentCampus = null;
            break;
          }
          if (normalizedHeading.includes('cardapio')) {
            const matchCampus = CAMPUS_RU_LIST.find((c) =>
              normalizedHeading.includes(CasAndJsfParser.normalizeLabel(c.headingKeyword)),
            );
            currentCampus = matchCampus?.id ?? null;
          } else {
            currentCampus = null;
          }
        } else if (tag === 'a' && currentCampus !== null) {
          const href = (el.getAttribute('href') ?? '').trim();
          if (href.length > 0) {
            const list = candidatesByCampus.get(currentCampus) ?? [];
            list.push({
              campus: currentCampus,
              semanaLabel: cleanText(el),
              pdfUrl: href,
            });
            candidatesByCampus.set(currentCampus, list);
          }
        }
      }

      const campusLinks: Partial<Record<CampusRu, CampusPdfLink>> = {};
      for (const [campus, candidates] of candidatesByCampus.entries()) {
        const chosen = selectActiveWeekLink(candidates, referenceIsoDate);
        if (chosen) {
          campusLinks[campus] = chosen;
        }
      }

      if (Object.keys(campusLinks).length === 0) {
        return CoreResults.failure({
          kind: 'ParseError',
          source: 'wp/v2/pages/12853',
          reason: 'Nenhum link PDF de campus encontrado em content.rendered.',
        });
      }

      return CoreResults.success({ modifiedGmt, campusLinks });
    } catch (err) {
      return CoreResults.failure({
        kind: 'ParseError',
        source: 'wp/v2/pages/12853',
        reason: err instanceof Error ? err.message : 'JSON invalido',
      });
    }
  },

  parseMediaList(jsonBody: string): CoreResult<RuWpMediaItem[]> {
    try {
      const dtos = JSON.parse(jsonBody) as WpMediaDto[];
      if (!Array.isArray(dtos)) {
        return CoreResults.failure({
          kind: 'ParseError',
          source: 'wp/v2/media',
          reason: 'Esperado array JSON',
        });
      }
      return CoreResults.success(
        dtos.map((dto) => ({
          sourceUrl: dto.source_url ?? '',
          title: parseHtmlFragmentText(dto.title?.rendered ?? ''),
          dateGmt: dto.date_gmt ?? '',
          fallbackFullImageUrl: dto.media_details?.sizes?.full?.source_url ?? null,
        })),
      );
    } catch (err) {
      return CoreResults.failure({
        kind: 'ParseError',
        source: 'wp/v2/media',
        reason: err instanceof Error ? err.message : 'JSON invalido',
      });
    }
  },

  parseNoticias(jsonBody: string): CoreResult<NoticiaRu[]> {
    try {
      const dtos = JSON.parse(jsonBody) as WpPostDto[];
      if (!Array.isArray(dtos)) {
        return CoreResults.failure({
          kind: 'ParseError',
          source: 'wp/v2/posts',
          reason: 'Esperado array JSON',
        });
      }
      return CoreResults.success(
        dtos.map((dto) => {
          const contentHtml = dto.content?.rendered ?? '';
          const contentDoc = parseHtmlDocument(contentHtml);
          const pdfLinks = Array.from(
            new Set(
              Array.from(contentDoc.querySelectorAll('a[href*=".pdf"], a[href*=".PDF"]'))
                .map((a) => (a.getAttribute('href') ?? '').trim())
                .filter((u) => u.length > 0),
            ),
          );
          return {
            id: dto.id ?? 0,
            dateIso: dto.date ?? '',
            title: parseHtmlFragmentText(dto.title?.rendered ?? ''),
            excerptText: parseHtmlFragmentText(dto.excerpt?.rendered ?? ''),
            contentHtml,
            link: dto.link ?? '',
            featuredImageUrl: dto._embedded?.['wp:featuredmedia']?.[0]?.source_url ?? null,
            anexoPdfUrls: pdfLinks,
          };
        }),
      );
    } catch (err) {
      return CoreResults.failure({
        kind: 'ParseError',
        source: 'wp/v2/posts',
        reason: err instanceof Error ? err.message : 'JSON invalido',
      });
    }
  },

  parsePricesPage(jsonBody: string): TabelaPrecosRu {
    const fallback = RuCalculator.defaultPrecosResolucao0037;
    try {
      const pages = JSON.parse(jsonBody) as WpPageDto[];
      const page = Array.isArray(pages) ? pages[0] : undefined;
      if (!page) return fallback;

      const doc = parseHtmlDocument(page.content?.rendered ?? '');
      const parsedGroups: PrecoGrupoRu[] = [];

      for (const row of Array.from(doc.querySelectorAll('table tr'))) {
        const cols = Array.from(row.querySelectorAll('td'));
        if (cols.length < 4) continue;
        const groupText = cleanText(cols[0]);
        const norm = CasAndJsfParser.normalizeLabel(groupText);
        const grupo: GrupoAcessoRu = norm.includes('executivo')
          ? 'EXECUTIVO'
          : RuCalculator.detectGrupo(groupText);
        if (grupo === 'DESCONHECIDO') continue;

        const fallbackGroup = fallback.grupos.find((g) => g.grupo === grupo);
        const cafe =
          parseCellPrice(cleanText(cols[cols.length - 3])) ?? fallbackGroup?.cafeCentavos ?? null;
        const almoco =
          parseCellPrice(cleanText(cols[cols.length - 2])) ?? fallbackGroup?.almocoCentavos ?? null;
        const jantar =
          parseCellPrice(cleanText(cols[cols.length - 1])) ?? fallbackGroup?.jantarCentavos ?? null;
        const publico = cols.length >= 5 ? cleanText(cols[1]) : GRUPO_ACESSO_RU_BY_ID[grupo].descricao;

        if (grupo === 'EXECUTIVO') {
          parsedGroups.push({
            grupo,
            publicoAlvo: publico,
            cafeCentavos: null,
            almocoCentavos: null,
            jantarCentavos: null,
            porKgCentavos: almoco ?? fallbackGroup?.porKgCentavos ?? null,
          });
        } else {
          parsedGroups.push({
            grupo,
            publicoAlvo: publico,
            cafeCentavos: cafe,
            almocoCentavos: almoco,
            jantarCentavos: jantar,
            porKgCentavos: null,
          });
        }
      }

      const modifiedGmt = page.modified_gmt && page.modified_gmt.length > 0
        ? page.modified_gmt
        : fallback.modifiedGmt;

      if (parsedGroups.length === 0) {
        return { ...fallback, modifiedGmt };
      }
      return {
        ...fallback,
        modifiedGmt,
        grupos: parsedGroups,
      };
    } catch {
      return fallback;
    }
  },
};

function selectActiveWeekLink(
  candidates: readonly CampusPdfLink[],
  referenceIsoDate: string,
): CampusPdfLink | null {
  if (candidates.length === 0) return null;
  const coveringToday = candidates.find((link) => {
    const m = DATE_RANGE_REGEX.exec(link.semanaLabel);
    if (!m) return false;
    const start = parseBrDateToIso(m[1], m[2], m[3]);
    const end = parseBrDateToIso(m[4], m[5], m[6]);
    return start !== null && end !== null && referenceIsoDate >= start && referenceIsoDate <= end;
  });
  return coveringToday ?? candidates[candidates.length - 1];
}

function parseCellPrice(text: string | null | undefined): number | null {
  if (!text) return null;
  const norm = CasAndJsfParser.normalizeLabel(text);
  if (norm.includes('gratuit')) return 0;
  return RuCalculator.parseBrlToCentavos(text);
}
