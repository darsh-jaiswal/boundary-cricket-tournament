# Boundary Android 1.0.10

The Android and web stores now list only the four owner-supplied photographed
products: Black Full-Sleeve Jersey (₹1,200), Blue Jersey (₹899), White Jersey
(₹899), and Maroon Cap (₹399). Apparel has S, M, L, XL and XXL options; the cap
is adjustable. Each listing sells the named item only, not the other clothing
visible in the photo. The original PNG files are retained unchanged.

Cards, product details and bag thumbnails use those photos. Generated drawings,
sample filler listings and the generic material collection are removed. Android
loads photos off the UI thread, caches them in memory/on disk and offers photo
retry if unavailable. An unreachable catalog shows retry instead of fake products.

Migration 014 retires generic listings without deleting orders, reservations or
their original item snapshots. It transfers the selected source SKUs' remaining
unreserved inventory once. A stock-return trigger routes cancellations of those
legacy reservations to replacement SKUs, including old function calls waiting
through migration. Other retired inventory remains disabled. Prices, shipping
and checkout authentication remain subject to server validation.

Validation: signed debug/release builds, 93 Android unit tests, 18 JavaScript and
database checks, independent Java/JavaScript/database review, and 360px/1280px
browser checks of the four images, details, size selection, bag totals,
persistence, removal and failed-catalog behavior. Database tests use temporary
tables and verify stock transfer, cancellation idempotence and historical IDs.
Production verification confirmed four products/16 variants, byte-identical
photos and a full APK download matching the published checksum. A 360px Android
emulator loaded all four photos from production and saved the selected XXL
jersey to its local bag; the device check passed. The real production mobile
browser also verified cap details, bag pricing and anonymous checkout gating.
No real payment was charged as part of these checks.
