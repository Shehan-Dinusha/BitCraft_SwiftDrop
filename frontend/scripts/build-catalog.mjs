#!/usr/bin/env node
/**
 * Builds the design catalog as ONE self-contained HTML file you can open from disk or send to someone.
 *
 *   npm run build:catalog                       -> dist-catalog/swiftdrop-catalog.html
 *   npm run build:catalog -- --out "C:/path/SwiftDrop-Catalog.html"   (also copies it there)
 *
 * All scripts, styles and icons are inlined. Only the web fonts (Plus Jakarta Sans, JetBrains Mono)
 * load from Google Fonts; offline, the browser falls back to system fonts and everything still works.
 */
import { copyFileSync, mkdirSync, readFileSync, statSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { build } from 'vite';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const outDir = path.join(root, 'dist-catalog');
const outIndex = process.argv.indexOf('--out');
const copyTo = outIndex > -1 ? process.argv[outIndex + 1] : null;

await build({ root, configFile: path.join(root, 'vite.catalog.config.ts'), logLevel: 'warn' });

let html = readFileSync(path.join(outDir, 'catalog.html'), 'utf8');

const read = (href) =>
  readFileSync(path.join(outDir, href.replace(/^\.\//, '').replace(/^\//, '')), 'utf8');
// Use replacer functions so "$&" and similar sequences inside the code are never interpreted.
const safeScript = (code) => code.replace(/<\/script/gi, '<\\/script');

html = html.replace(/<link[^>]*rel="stylesheet"[^>]*href="([^"]+\.css)"[^>]*>/g, (match, href) => {
  if (/^https?:/.test(href)) return match; // keep the Google Fonts link
  return `<style>\n${read(href)}\n</style>`;
});
html = html.replace(
  /<script[^>]*type="module"[^>]*src="([^"]+\.js)"[^>]*><\/script>/g,
  (_match, href) => {
    return `<script type="module">\n${safeScript(read(href))}\n</script>`;
  }
);
html = html.replace(/<link[^>]*rel="modulepreload"[^>]*>/g, '');

if (/src="\.\/assets|href="\.\/assets/.test(html)) {
  throw new Error('Some assets were not inlined. Check the build output in dist-catalog/.');
}

const target = path.join(outDir, 'swiftdrop-catalog.html');
writeFileSync(target, html);
const kb = Math.round(statSync(target).size / 1024);
console.log(`Wrote ${target} (${kb} KB)`);

if (copyTo) {
  mkdirSync(path.dirname(copyTo), { recursive: true });
  copyFileSync(target, copyTo);
  console.log(`Copied to ${copyTo}`);
}
