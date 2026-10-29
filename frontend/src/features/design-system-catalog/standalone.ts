/**
 * True in the single-file catalog build (`npm run build:catalog`). That build has no server and no
 * login: it uses hash routes (`file.html#/catalog/...`) so it can be opened straight from disk.
 */
export const isStandaloneCatalog = import.meta.env.VITE_CATALOG_STANDALONE === 'true';

/** URL of a role shell preview. In the standalone build the frame loads the same file with a hash route. */
export function previewUrl(role: string): string {
  const path = `/catalog/preview/${role}`;
  if (!isStandaloneCatalog) return path;
  return `${window.location.href.split('#')[0]}#${path}`;
}
