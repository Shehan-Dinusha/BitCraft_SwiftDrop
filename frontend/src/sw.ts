/// <reference lib="webworker" />
/**
 * SwiftDrop service worker (vite-plugin-pwa `injectManifest`).
 *
 * Keeps what the generated worker did before:
 * - precaches the app's own build output only, and removes caches of older versions;
 * - answers page navigations with /index.html, except API, WebSocket, docs and health paths;
 * - never caches API responses (they hold addresses, phone numbers and orders, and a shared phone
 *   must not show the previous user's data): there is no runtime caching at all;
 * - waits for the user: a new version activates only when the page posts SKIP_WAITING ("Reload").
 *
 * Adds lock-screen notifications (Web Push): a push shows a notification, a tap opens SwiftDrop.
 */
import { cleanupOutdatedCaches, createHandlerBoundToURL, precacheAndRoute } from 'workbox-precaching';
import { NavigationRoute, registerRoute } from 'workbox-routing';
import { parsePushPayload, safeTarget } from './features/pwa/pushPayload';

declare const self: ServiceWorkerGlobalScope;

precacheAndRoute(self.__WB_MANIFEST);
cleanupOutdatedCaches();

registerRoute(
  new NavigationRoute(createHandlerBoundToURL('/index.html'), {
    denylist: [/^\/api\//, /^\/ws\//, /^\/swagger-ui/, /^\/v3\/api-docs/, /^\/webjars\//, /^\/healthz$/]
  })
);

// The "Reload" button in src/features/pwa/PwaLayer.tsx (updateServiceWorker) sends this.
self.addEventListener('message', (event) => {
  const source = event.source;
  if (!source || source instanceof ServiceWorker || !('url' in source)) {
    return;
  }

  const sourceOrigin = new URL(source.url).origin;
  if (sourceOrigin !== self.location.origin) {
    return;
  }

  if ((event.data as { type?: string } | null)?.type === 'SKIP_WAITING') {
    void self.skipWaiting();
  }
});

self.addEventListener('push', (event) => {
  const notice = parsePushPayload(event.data?.text());
  // renotify is not in every TypeScript lib yet; it makes a replaced (same tag) update alert again.
  const options: NotificationOptions & { renotify?: boolean } = {
    body: notice.body,
    icon: '/icon-192.png',
    badge: '/icon-192.png',
    tag: notice.tag,
    renotify: Boolean(notice.tag),
    data: { url: notice.url }
  };
  event.waitUntil(self.registration.showNotification(notice.title, options));
});

self.addEventListener('notificationclick', (event) => {
  event.notification.close();
  const target = safeTarget((event.notification.data as { url?: string } | null)?.url, self.location.origin);
  event.waitUntil(
    (async () => {
      const windows = await self.clients.matchAll({ type: 'window', includeUncontrolled: true });
      const open = windows.find((client) => new URL(client.url).origin === self.location.origin);
      if (open) {
        const focused = await open.focus();
        await focused.navigate(target).catch(() => undefined);
        return;
      }
      await self.clients.openWindow(target);
    })()
  );
});
