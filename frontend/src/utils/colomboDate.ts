const ZONE = 'Asia/Colombo';

/** Today's date in Sri Lanka as `2026-11-22`, whatever the browser's time zone. Reports use these days. */
export function colomboToday(): string {
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: ZONE,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit'
  }).format(new Date());
}

/** The time in Sri Lanka as `13:05` (24-hour), for comparing with the working-day hours. */
export function colomboTimeNow(now: Date = new Date()): string {
  return new Intl.DateTimeFormat('en-GB', {
    timeZone: ZONE,
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23'
  }).format(now);
}

/** A moment shown as Sri Lanka clock time, `13:05`. */
export function colomboClock(iso?: string | null): string {
  if (!iso) return '';
  return colomboTimeNow(new Date(iso));
}

/** A server time of day (`12:00` or `12:00:00`) as `12:00`. */
export const hhmm = (time?: string | null) => (time ?? '').slice(0, 5);

/** A `2026-11-30` day as `Monday 30 November`. */
export function dayLabel(day?: string | null): string {
  if (!day) return '';
  return new Intl.DateTimeFormat('en-GB', {
    timeZone: 'UTC',
    weekday: 'long',
    day: 'numeric',
    month: 'long'
  }).format(new Date(`${day}T00:00:00Z`));
}
