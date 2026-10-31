import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

/** Build config for the standalone design catalog (see scripts/build-catalog.mjs). */
export default defineConfig({
  plugins: [react()],
  base: './',
  resolve: { alias: { '@': path.resolve(__dirname, './src') } },
  define: { 'import.meta.env.VITE_CATALOG_STANDALONE': JSON.stringify('true') },
  build: {
    outDir: 'dist-catalog',
    emptyOutDir: true,
    assetsInlineLimit: 100_000_000,
    cssCodeSplit: false,
    chunkSizeWarningLimit: 4000,
    rollupOptions: {
      input: path.resolve(__dirname, 'catalog.html'),
      output: { inlineDynamicImports: true }
    }
  }
});
