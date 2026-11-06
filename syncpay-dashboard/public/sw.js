const CACHE_NAME = 'meshpay-dashboard-shell-v1';

async function cacheShell() {
  const cache = await caches.open(CACHE_NAME);
  const page = await fetch('/', { cache: 'no-store' });
  if (!page.ok) throw new Error('Dashboard shell could not be fetched');
  await cache.put('/', page.clone());

  const html = await page.text();
  const assets = [...html.matchAll(/(?:src|href)="([^"]+\.(?:js|css))"/g)]
    .map((match) => new URL(match[1], self.location.origin).href)
    .filter((asset) => new URL(asset).origin === self.location.origin);

  await Promise.all(assets.map(async (asset) => {
    try {
      const response = await fetch(asset, { cache: 'no-store' });
      if (response.ok) await cache.put(asset, response);
    } catch {
      // A failed optional asset fetch must not expose or cache API data.
    }
  }));
}

self.addEventListener('install', (event) => {
  event.waitUntil(cacheShell().then(() => self.skipWaiting()));
});

self.addEventListener('activate', (event) => {
  event.waitUntil((async () => {
    const names = await caches.keys();
    await Promise.all(names
      .filter((name) => name.startsWith('meshpay-dashboard-shell-') && name !== CACHE_NAME)
      .map((name) => caches.delete(name)));
    await self.clients.claim();
  })());
});

self.addEventListener('fetch', (event) => {
  const request = event.request;
  const url = new URL(request.url);
  if (request.method !== 'GET' || url.origin !== self.location.origin ||
      url.pathname.startsWith('/api/') || url.pathname.startsWith('/payment-health/') ||
      url.pathname.startsWith('/saga-health/')) return;

  if (request.mode === 'navigate') {
    event.respondWith(fetch(request).then(async (response) => {
      if (response.ok) await (await caches.open(CACHE_NAME)).put('/', response.clone());
      return response;
    }).catch(async () => (await caches.match('/')) || Response.error()));
    return;
  }

  event.respondWith(caches.match(request).then((cached) => cached || fetch(request).then(async (response) => {
    if (response.ok) await (await caches.open(CACHE_NAME)).put(request, response.clone());
    return response;
  })));
});
