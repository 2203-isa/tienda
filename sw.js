/* Service worker de H.H | M.F. Store: guarda la tienda en el dispositivo para que la app abra al instante
   y también sin conexión. Cada vez que hay Internet, descarga en segundo plano la versión más nueva. */
const CACHE = 'hhmf-6d333ed6f6';
const SHELL = ['./', 'index.html', 'contabilidad.html', 'manifest.webmanifest', 'icon-192.png', 'icon-512.png', 'maskable-512.png', 'apple-touch-icon.png', 'favicon-48.png'];
self.addEventListener('install', e => { e.waitUntil(caches.open(CACHE).then(c => c.addAll(SHELL)).then(() => self.skipWaiting())); });
self.addEventListener('activate', e => {
  e.waitUntil(caches.keys().then(keys => Promise.all(keys.filter(k => k.startsWith('hhmf-') && k !== CACHE).map(k => caches.delete(k)))).then(() => self.clients.claim()));
});
self.addEventListener('fetch', e => {
  const req = e.request;
  if (req.method !== 'GET') return;
  const url = new URL(req.url);
  if (url.origin !== self.location.origin) return;     // WhatsApp, CEP y los datos de la contabilidad van directo a Internet (nunca se guardan aquí)
  const page = url.pathname.endsWith('/contabilidad.html') ? 'contabilidad.html' : 'index.html';
  const key = req.mode === 'navigate' ? page : req;
  e.respondWith(caches.open(CACHE).then(async cache => {
    const saved = await cache.match(key, { ignoreSearch: true });
    const fresh = fetch(req).then(r => { if (r && r.ok && r.type === 'basic') cache.put(key, r.clone()); return r; }).catch(() => null);
    if (saved) { e.waitUntil(fresh); return saved; }       // primero lo guardado: abre al instante
    const r = await fresh; if (r) return r;
    return (await cache.match(req.mode === 'navigate' ? page : 'index.html')) || Response.error();
  }));
});
