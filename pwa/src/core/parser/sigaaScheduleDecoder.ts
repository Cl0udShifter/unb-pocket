import {
  DIA_SEMANA_SIGAA_LIST,
  TURNO_SIGAA_BY_CHAR,
  type HorarioSlot,
  type TurnoSigaa,
} from '../model/models';

const BLOCK_REGEX = /([2-7]+)([MTN])([1-6]+)/g;

const TIME_TABLE: Readonly<Record<string, readonly [string, string]>> = {
  'MANHA:1': ['08:00', '08:55'],
  'MANHA:2': ['08:55', '09:50'],
  'MANHA:3': ['10:00', '10:55'],
  'MANHA:4': ['10:55', '11:50'],
  'MANHA:5': ['12:00', '12:55'],
  'TARDE:1': ['12:55', '13:50'],
  'TARDE:2': ['14:00', '14:55'],
  'TARDE:3': ['14:55', '15:50'],
  'TARDE:4': ['16:00', '16:55'],
  'TARDE:5': ['16:55', '17:50'],
  'TARDE:6': ['18:00', '18:55'],
  'NOITE:1': ['19:00', '19:50'],
  'NOITE:2': ['19:50', '20:40'],
  'NOITE:3': ['20:50', '21:40'],
  'NOITE:4': ['21:40', '22:30'],
};

export const SigaaScheduleDecoder = {
  decode(rawSchedule: string): HorarioSlot[] {
    const sanitized = rawSchedule.split('(')[0].trim();
    if (!sanitized) return [];

    const result: HorarioSlot[] = [];
    BLOCK_REGEX.lastIndex = 0;
    let match: RegExpExecArray | null;
    while ((match = BLOCK_REGEX.exec(sanitized)) !== null) {
      const daysPart = match[1];
      const shiftChar = match[2];
      const timesPart = match[3];
      const shift: TurnoSigaa | undefined = TURNO_SIGAA_BY_CHAR[shiftChar];
      if (!shift) continue;

      for (const dayChar of daysPart) {
        const dayNum = Number.parseInt(dayChar, 10);
        const dayMeta = DIA_SEMANA_SIGAA_LIST.find((d) => d.digito === dayNum);
        if (!dayMeta) continue;

        for (const timeChar of timesPart) {
          const slotNum = Number.parseInt(timeChar, 10);
          const range = TIME_TABLE[`${shift}:${slotNum}`];
          if (!range) continue;
          result.push({
            diaSemana: dayMeta.id,
            turno: shift,
            tempoNumero: slotNum,
            inicio: range[0],
            fim: range[1],
            codigoOriginal: match[0],
          });
        }
      }
    }
    return result;
  },
};
