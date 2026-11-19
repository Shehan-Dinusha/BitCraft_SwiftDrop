import { defineConfig, loadEnv, type ProxyOptions } from 'vite';
import react from '@vitejs/plugin-react';
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
    plugins: [react()],
    resolve: { alias: { '@': path.resolve(__dirname, './src') } },
    server: { proxy }
  };
});
