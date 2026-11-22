/** Today's date in Sri Lanka as `2026-11-22`, whatever the browser's time zone. Reports use these days. */
export function colomboToday(): string {
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Colombo',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit'
  }).format(new Date());
}
