export const BRASILIA_TIME_ZONE = 'America/Sao_Paulo';

export interface BrasiliaDateParts {
  readonly year: number;
  readonly month: number; // 1..12
  readonly day: number; // 1..31
  readonly hour: number; // 0..23
  readonly minute: number; // 0..59
  readonly second: number; // 0..59
  readonly dayOfWeekIso: number; // 1 (Monday) .. 7 (Sunday)
  readonly isoDate: string; // YYYY-MM-DD
}

const partsFormatter = new Intl.DateTimeFormat('en-US', {
  timeZone: BRASILIA_TIME_ZONE,
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
  hourCycle: 'h23',
});

export function getBrasiliaParts(epochMillis: number): BrasiliaDateParts {
  const formatted = partsFormatter.formatToParts(new Date(epochMillis));
  let year = 1970;
  let month = 1;
  let day = 1;
  let hour = 0;
  let minute = 0;
  let second = 0;

  for (const part of formatted) {
    if (part.type === 'year') year = Number.parseInt(part.value, 10);
    else if (part.type === 'month') month = Number.parseInt(part.value, 10);
    else if (part.type === 'day') day = Number.parseInt(part.value, 10);
    else if (part.type === 'hour') hour = Number.parseInt(part.value, 10) % 24;
    else if (part.type === 'minute') minute = Number.parseInt(part.value, 10);
    else if (part.type === 'second') second = Number.parseInt(part.value, 10);
  }

  const utcDow = new Date(Date.UTC(year, month - 1, day)).getUTCDay();
  const dayOfWeekIso = utcDow === 0 ? 7 : utcDow;
  const isoDate = `${String(year).padStart(4, '0')}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`;

  return {
    year,
    month,
    day,
    hour,
    minute,
    second,
    dayOfWeekIso,
    isoDate,
  };
}

export function isValidCalendarDate(year: number, month: number, day: number): boolean {
  if (month < 1 || month > 12 || day < 1 || day > 31) return false;
  const d = new Date(Date.UTC(year, month - 1, day));
  return (
    d.getUTCFullYear() === year &&
    d.getUTCMonth() === month - 1 &&
    d.getUTCDate() === day
  );
}

export function formatIsoDate(year: number, month: number, day: number): string {
  return `${String(year).padStart(4, '0')}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
}

export function parseIsoDate(isoDate: string): { year: number; month: number; day: number; dayOfWeekIso: number } {
  const [y, m, d] = isoDate.split('-').map((v) => Number.parseInt(v, 10));
  const utcDow = new Date(Date.UTC(y, m - 1, d)).getUTCDay();
  return {
    year: y,
    month: m,
    day: d,
    dayOfWeekIso: utcDow === 0 ? 7 : utcDow,
  };
}

export function addDaysIso(isoDate: string, days: number): string {
  const { year, month, day } = parseIsoDate(isoDate);
  const dt = new Date(Date.UTC(year, month - 1, day + days));
  return formatIsoDate(dt.getUTCFullYear(), dt.getUTCMonth() + 1, dt.getUTCDate());
}

export function daysBetweenIso(isoA: string, isoB: string): number {
  const a = parseIsoDate(isoA);
  const b = parseIsoDate(isoB);
  const msA = Date.UTC(a.year, a.month - 1, a.day);
  const msB = Date.UTC(b.year, b.month - 1, b.day);
  return Math.round((msB - msA) / (24 * 60 * 60 * 1000));
}

export function formatBrDateFromIso(isoDate: string): string {
  const { year, month, day } = parseIsoDate(isoDate);
  return `${String(day).padStart(2, '0')}/${String(month).padStart(2, '0')}/${String(year).padStart(4, '0')}`;
}

export function brasiliaLocalToEpochMillis(
  isoDate: string,
  hour: number,
  minute: number,
  second: number = 0,
): number {
  const { year, month, day } = parseIsoDate(isoDate);
  // Initial guess: America/Sao_Paulo is UTC-3
  let guess = Date.UTC(year, month - 1, day, hour + 3, minute, second, 0);

  for (let pass = 0; pass < 2; pass += 1) {
    const actual = getBrasiliaParts(guess);
    const desiredWallMs = Date.UTC(year, month - 1, day, hour, minute, second, 0);
    const actualWallMs = Date.UTC(
      actual.year,
      actual.month - 1,
      actual.day,
      actual.hour,
      actual.minute,
      actual.second,
      0,
    );
    const diff = desiredWallMs - actualWallMs;
    if (diff === 0) break;
    guess += diff;
  }
  return guess;
}
