import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

export default defineConfig({
  plugins: [react()],
  resolve: { alias: { '@': path.resolve(__dirname, './src') } },
  server: {
    proxy: {
      '/api/v1/notifications': {
        target: 'http://localhost:8088',
        changeOrigin: true
      },
      '/api': 'http://localhost:8080',
      '/ws': { target: 'ws://localhost:8080', ws: true }
    }
  }
});
