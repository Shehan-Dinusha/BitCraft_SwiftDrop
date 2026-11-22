/** First and last initial of a name or username, e.g. "Nimal Perera" -> "NP". */
export function initials(name: string): string {
  const parts = name.split(/[\s._-]+/).filter(Boolean);
  const last = parts.length > 1 ? parts[parts.length - 1] : undefined;
  return ((parts[0]?.[0] ?? '') + (last?.[0] ?? '')).toUpperCase() || '?';
}
