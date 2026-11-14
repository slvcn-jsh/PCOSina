const CACHE_NAME = "pcosina-web-shell-v2";
const SHELL_ASSETS = [
  "/",
  "/manifest.webmanifest",
  "/images/pcosina-logo.png",
  "/images/auth-snacks-background.png",
  "/images/login-heart-hands.png",
  "/images/login-ownership-watermark.png",
  "/images/avatar-doctor-dog.png",
  "/images/meal-breakfast.png",
  "/images/meal-lunch.png",
  "/images/meal-dinner.png",
  "/images/pcosina-nav-plan.png",
  "/images/pcosina-nav-grocery.png",
  "/images/pcosina-nav-home.png",
  "/images/pcosina-nav-progress.png",
  "/images/pcosina-nav-support.png",
  "/images/pcosina-ready-to-shop.png"
];

self.addEventListener("install", (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME).then((cache) => cache.addAll(SHELL_ASSETS))
  );
  self.skipWaiting();
});

self.addEventListener("activate", (event) => {
  event.waitUntil(
    caches
      .keys()
      .then((keys) =>
        Promise.all(keys.filter((key) => key !== CACHE_NAME).map((key) => caches.delete(key)))
      )
  );
  self.clients.claim();
});

self.addEventListener("fetch", (event) => {
  const { request } = event;
  const url = new URL(request.url);

  if (request.method !== "GET") return;

  if (url.origin === self.location.origin) {
    event.respondWith(
      fetch(request)
        .then((response) => {
          const copy = response.clone();
          caches.open(CACHE_NAME).then((cache) => cache.put(request, copy));
          return response;
        })
        .catch(() => caches.match(request).then((cached) => cached || caches.match("/")))
    );
  }
});
