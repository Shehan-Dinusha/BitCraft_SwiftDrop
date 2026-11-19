/** "nimal@example.com" -> "n***@example.com", for "we sent a code to ..." copy. Null if not an email. */
export function maskEmail(value: string): string | null {
  const at = value.indexOf('@');
  if (at < 1) return null;
  return `${value.slice(0, 1)}***${value.slice(at)}`;
}
