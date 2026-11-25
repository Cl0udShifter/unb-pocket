import jsQR from 'jsqr';
import {
  CoreResults,
  type BoletimSemestre,
  type CoreResult,
  type NotaDisciplinaBoletim,
} from '../model/models';
import { CasAndJsfParser, parseHtmlDocument } from './casAndJsfParser';
import type { RgbImageBuffer } from './ruAllergenClassifier';

export interface CarteirinhaPdfExtractedInput {
  readonly textLines: readonly string[];
  readonly img2Foto: RgbImageBuffer | null;
  readonly img4Qr: RgbImageBuffer | null;
}

export interface CarteirinhaParsed {
  readonly nomeCompleto: string;
  readonly matricula: string;
  readonly cpfTexto: string | null;
  readonly curso: string;
  readonly nivel: string;
  readonly validadeTexto: string;
  readonly qrPayload16Digitos: string;
  readonly qrImage: RgbImageBuffer;
  readonly foto3x4: RgbImageBuffer | null;
}

const CPF_REGEX = /\d{3}\.\d{3}\.\d{3}-\d{2}/;
const MATRICULA_REGEX = /\b\d{9}\b/;
const QR_16_DIGITS_REGEX = /\d{16}/;
const VERIFICATION_REGEX = /(?:código de verificação|codigo de verificacao)\s*:?\s*([a-zA-Z0-9]{6,24})/i;

function cleanText(node: Node | null | undefined): string {
  return (node?.textContent ?? '').replace(/\s+/g, ' ').trim();
}

export const DiscenteDocumentosParser = {
  parseBoletimGeral(html: string): CoreResult<BoletimSemestre[]> {
    const doc = parseHtmlDocument(html);
    const tables = Array.from(doc.querySelectorAll('table.tabelaRelatorio'));
    if (tables.length === 0) {
      const normalized = CasAndJsfParser.normalizeLabel(cleanText(doc.body));
      if (
        normalized.includes('relatorio de notas') ||
        normalized.includes('nao possui') ||
        normalized.includes('nenhuma')
      ) {
        return CoreResults.success([]);
      }
      return CoreResults.failure({
        kind: 'ParseError',
        source: 'relatorioNotasAluno',
        reason: 'Tabelas de boletim (table.tabelaRelatorio) nao encontradas.',
      });
    }

    const semestres: BoletimSemestre[] = [];
    for (const table of tables) {
      const semestre = cleanText(table.querySelector('caption'));
      const disciplinas: NotaDisciplinaBoletim[] = [];

      for (const row of Array.from(table.querySelectorAll('tbody tr'))) {
        const cols = Array.from(row.querySelectorAll('td'));
        if (cols.length < 6) continue;
        const codigo = cleanText(cols[0]);
        const disciplina = cleanText(cols[1]);
        if (!codigo && !disciplina) continue;

        const rawNota1 = cleanText(cols[2]);
        const nota1 = rawNota1 && rawNota1 !== '-' && rawNota1 !== '--' ? rawNota1 : null;
        const rawMencao = cleanText(cols[3]);
        const resultadoMencao =
          rawMencao && rawMencao !== '-' && rawMencao !== '--' ? rawMencao : null;
        const parsedFaltas = Number.parseInt(cleanText(cols[4]), 10);
        const faltas = Number.isFinite(parsedFaltas) ? parsedFaltas : null;
        const situacao = cleanText(cols[5]);

        disciplinas.push({
          codigo,
          disciplina,
          nota1,
          resultadoMencao,
          faltas,
          situacao,
        });
      }

      if (semestre || disciplinas.length > 0) {
        semestres.push({ semestre, disciplinas });
      }
    }

    return CoreResults.success(semestres);
  },

  extractVerificationCode(htmlOrText: string): string | null {
    return VERIFICATION_REGEX.exec(htmlOrText)?.[1] ?? null;
  },

  decodeQrPayloadFromRgb(image: RgbImageBuffer): string | null {
    const pixelCount = image.width * image.height;
    const rgba = new Uint8ClampedArray(pixelCount * 4);
    for (let i = 0; i < pixelCount; i += 1) {
      const src = i * 3;
      const dst = i * 4;
      rgba[dst] = image.rgbBytes[src];
      rgba[dst + 1] = image.rgbBytes[src + 1];
      rgba[dst + 2] = image.rgbBytes[src + 2];
      rgba[dst + 3] = 255;
    }

    const decoded = jsQR(rgba, image.width, image.height, {
      inversionAttempts: 'attemptBoth',
    });
    return decoded?.data?.trim() || null;
  },

  parseCarteirinha(input: CarteirinhaPdfExtractedInput): CoreResult<CarteirinhaParsed> {
    const qrImg = input.img4Qr;
    if (!qrImg) {
      return CoreResults.failure({
        kind: 'PdfExtractionError',
        documentType: 'Carteirinha Estudantil',
        reason: 'Imagem do QR Code (img4 250x250) nao encontrada no PDF.',
      });
    }

    const decodedRaw = this.decodeQrPayloadFromRgb(qrImg);
    const qrPayload = decodedRaw ? QR_16_DIGITS_REGEX.exec(decodedRaw)?.[0] ?? decodedRaw : null;
    if (!qrPayload) {
      return CoreResults.failure({
        kind: 'PdfExtractionError',
        documentType: 'Carteirinha Estudantil',
        reason: 'Nao foi possivel decodificar o QR Code de 16 digitos em img4.',
      });
    }

    const lines = input.textLines.map((l) => l.trim()).filter((l) => l.length > 0);
    let cpf: string | null = null;
    let matricula = '';
    for (const line of lines) {
      if (!cpf) {
        const m = CPF_REGEX.exec(line);
        if (m) cpf = m[0];
      }
      if (!matricula) {
        const m = MATRICULA_REGEX.exec(line);
        if (m) matricula = m[0];
      }
    }

    const validade =
      lines.find((line) => CasAndJsfParser.normalizeLabel(line).includes('validade')) ?? '';
    const nivel =
      lines.find((line) => {
        const norm = CasAndJsfParser.normalizeLabel(line);
        return norm === 'graduacao' || norm === 'pos-graduacao' || norm.includes('graduacao');
      }) ?? '';

    const nome =
      lines.find((line) => {
        const norm = CasAndJsfParser.normalizeLabel(line);
        return (
          line !== validade &&
          line !== nivel &&
          !CPF_REGEX.test(line) &&
          !MATRICULA_REGEX.test(line) &&
          !norm.includes('universidade de brasilia') &&
          !norm.includes('identidade estudantil')
        );
      }) ?? '';

    const remainingForCurso = lines.filter((line) => {
      const norm = CasAndJsfParser.normalizeLabel(line);
      return (
        line !== nome &&
        line !== validade &&
        line !== nivel &&
        !CPF_REGEX.test(line) &&
        !MATRICULA_REGEX.test(line) &&
        !norm.includes('universidade de brasilia')
      );
    });
    const curso = remainingForCurso[remainingForCurso.length - 1] ?? '';

    return CoreResults.success({
      nomeCompleto: nome,
      matricula,
      cpfTexto: cpf,
      curso,
      nivel,
      validadeTexto: validade,
      qrPayload16Digitos: qrPayload,
      qrImage: qrImg,
      foto3x4: input.img2Foto,
    });
  },
};
