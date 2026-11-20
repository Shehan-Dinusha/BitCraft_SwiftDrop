import { defineConfig, loadEnv, type ProxyOptions } from 'vite';
import react from '@vitejs/plugin-react';
import { VitePWA } from 'vite-plugin-pwa';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

const LOOPBACK_HOSTS = new Set(['localhost', '127.0.0.1', '[::1]']);

/**
 * Local webhook development only (`npm run dev:webhook`, which loads `.env.webhook-dev`).
 *
 * Sends `/api/v1/webhooks/**` straight to the webhook service on a loopback port and plays the
 * gateway's role: it drops the browser's Authorization header and any client copies of the identity
 * headers, then sets a fixed development identity. Never active in `vite build` or `vite preview`,
 * and refuses any non-loopback target so the shortcut cannot point at a shared environment.
 */
function webhookDevProxy(env: Record<string, string>): ProxyOptions {
  const target = env.WEBHOOK_DEV_TARGET || 'http://localhost:8087';
  if (!LOOPBACK_HOSTS.has(new URL(target).hostname)) {
    throw new Error(`WEBHOOK_DEV_TARGET must be a loopback URL, got ${target}`);
  }
  const identity: Record<string, string | undefined> = {
    'X-User-Id': env.WEBHOOK_DEV_USER_ID,
    'X-User-Role': env.WEBHOOK_DEV_ROLE || 'MERCHANT',
    'X-Merchant-Id': env.WEBHOOK_DEV_MERCHANT_ID
  };
  return {
    target,
    changeOrigin: true,
    configure: (proxy) => {
      proxy.on('proxyReq', (proxyReq) => {
        for (const header of ['authorization', 'x-user-id', 'x-user-role', 'x-merchant-id', 'x-district-id']) {
          proxyReq.removeHeader(header);
        }
        for (const [name, value] of Object.entries(identity)) {
          if (value) proxyReq.setHeader(name, value);
        }
      });
    }
  };
}

/**
 * Installable PWA, aimed at couriers and customers on phones.
 *
 * The service worker precaches only the app's own build output, so the shell opens instantly on
 * a weak signal. It never caches API responses (they hold addresses, phone numbers and orders, and
 * a shared phone must not show the previous user's data offline) and never answers /api, /ws or the
 * gateway's docs with the app shell. Cross-origin requests (Google Fonts) are left to the network:
 * the page CSP's connect-src would block the worker from fetching them anyway.
 *
 * registerType 'prompt': a new version waits until the user taps "Reload" (src/features/pwa), so a
 * courier is never reloaded in the middle of a delivery.
 */
function pwa() {
  return VitePWA({
    registerType: 'prompt',
    injectRegister: false,
    manifest: {
      id: '/',
      name: 'SwiftDrop',
      short_name: 'SwiftDrop',
      description: 'Book, track and deliver parcels across Sri Lanka.',
      lang: 'en',
      start_url: '/',
      scope: '/',
      display: 'standalone',
      theme_color: '#f8fafc',
      background_color: '#f8fafc',
      icons: [
        { src: '/icon-192.png', sizes: '192x192', type: 'image/png', purpose: 'any' },
        { src: '/icon-512.png', sizes: '512x512', type: 'image/png', purpose: 'any' },
        { src: '/maskable-192.png', sizes: '192x192', type: 'image/png', purpose: 'maskable' },
        { src: '/maskable-512.png', sizes: '512x512', type: 'image/png', purpose: 'maskable' }
      ],
      shortcuts: [
        {
          name: 'Track a parcel',
          url: '/track',
          icons: [{ src: '/icon-192.png', sizes: '192x192', type: 'image/png' }]
        },
        {
          name: 'My deliveries',
          short_name: 'Deliveries',
          url: '/courier/deliveries',
          icons: [{ src: '/icon-192.png', sizes: '192x192', type: 'image/png' }]
        }
      ]
    },
    workbox: {
      globPatterns: ['**/*.{js,css,html,ico,png,svg,woff2}'],
      navigateFallback: '/index.html',
      navigateFallbackDenylist: [/^\/api\//, /^\/ws\//, /^\/swagger-ui/, /^\/v3\/api-docs/, /^\/webjars\//, /^\/healthz$/],
      cleanupOutdatedCaches: true,
      // The main bundle is ~1.3 MB; keep it precached.
      maximumFileSizeToCacheInBytes: 3 * 1024 * 1024
    }
  });
}

export default defineConfig(({ command, mode, isPreview }) => {
  const env = loadEnv(mode, __dirname, '');
  const webhookDirect = command === 'serve' && !isPreview && env.WEBHOOK_DEV_DIRECT === 'true';

  // Insertion order matters: Vite uses the first matching prefix, so the webhook entry precedes '/api'.
  const proxy: Record<string, string | ProxyOptions> = {
    ...(webhookDirect ? { '/api/v1/webhooks': webhookDevProxy(env) } : {}),
    '/api': 'http://localhost:8080',
    '/ws': { target: 'ws://localhost:8080', ws: true }
  };

  return {
    plugins: [react(), pwa()],
    resolve: { alias: { '@': path.resolve(__dirname, './src') } },
    server: { proxy }
  };
});
