// Minimal app-shell service worker for the PWA (mainly for the iPhone/"Add to Home Screen" path,
// where there's no native shell to fall back on if the network drops mid-use). Network-first for
// same-origin requests so the app is always fresh when online, falling back to whatever was
// cached from the last successful load when offline. Deliberately leaves cross-origin requests
// (the pinned CDN scripts, Google Fonts, Google sign-in) untouched — they're either already
// version-pinned in their URL or need to stay live, so caching them here would only risk serving
// something stale.
const CACHE_NAME = "masarifi-shell-v1";
const SHELL_FILES = [
  "./",
  "./index.html",
  "./manifest.json",
  "./icons/icon-192.png",
  "./icons/icon-512.png",
  "./icons/apple-touch-icon.png",
];

self.addEventListener("install", (e) => {
  e.waitUntil(caches.open(CACHE_NAME).then((cache) => cache.addAll(SHELL_FILES)));
  self.skipWaiting();
});

self.addEventListener("activate", (e) => {
  e.waitUntil(
    caches.keys().then((keys) => Promise.all(keys.filter((k) => k !== CACHE_NAME).map((k) => caches.delete(k))))
  );
  self.clients.claim();
});

self.addEventListener("fetch", (e) => {
  const url = new URL(e.request.url);
  if (e.request.method !== "GET" || url.origin !== self.location.origin) return;

  e.respondWith(
    fetch(e.request)
      .then((res) => {
        const copy = res.clone();
        caches.open(CACHE_NAME).then((cache) => cache.put(e.request, copy));
        return res;
      })
      .catch(() => caches.match(e.request).then((cached) => cached || caches.match("./index.html")))
  );
});
