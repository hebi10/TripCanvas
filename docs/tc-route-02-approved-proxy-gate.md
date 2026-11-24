# TC-ROUTE-02-A — 검증된 경로 프록시 호출 경계

- Date: 2026-11-17
- Base: main 0fff553102d3f4fe87f916210ff2d543f832cb14
- Scope: Android client transport, explicit per-request location disclosure, response validation and tests. Not a production provider integration or Play release.

## Request boundary

Set TRIPCANVAS_ROUTE_PROXY_URL during an explicitly approved build only to an HTTPS endpoint with path /v1/routes on a publicly resolved domain. Its default is empty and routing network requests remain disabled. The URL is not a secret. Never embed provider keys in app code, build flags or APK. Endpoint approval must separately include server-side app/auth validation, abuse protection, provider quotas, billing limit, supported modes, logs/retention and privacy policy.

Clicking 경로 조회 opens an explicit consent dialog. Only the selected origin/destination coordinates and mode are sent. No bulk location submission, background polling, route optimization or modification of itinerary order occurs. Failed/unconfigured/unsupported requests yield no estimated numbers. The route provider name and response timestamp accompany successful values.

Example proxy JSON contract, with *illustrative schema fields only (not a real route estimate)*:

POST /v1/routes
Request fields: origin {latitude, longitude}, destination {latitude, longitude}, mode WALKING|DRIVING|CYCLING|TRANSIT
Success JSON fields: distanceMeters integer >= 0, durationSeconds integer >= 0, provider nonblank, queriedAtMillis positive and recent.
Unavailable: HTTP 204/404/422. Not authorized: 401/403. Rate limit: 429.

Client safeguards: HTTPS-only canonical endpoint, no credentials/query/redirects, explicit confirmation, 7s timeouts, 8KiB max JSON response, 1.2s rate limiting, 10min memory cache per place coordinates and travel mode; freshness and nonnegative validation. No fake distance/duration fallback.

## Remaining release gates

1. Work: approve routing provider and commercial terms; provision a server endpoint with real API keys stored server-side and authentication/abuse mitigation.
2. Code: verify provider mapping for all four transport modes and fallback when unsupported; add authenticated proxy integration, app identity and quota/limits checks.
3. QA: Android/JVM/API29/35/36 and phone/tablet/foldables; offline, error responses, privacy consent and real-world accuracy.
4. Google Play: do not publish until all P1–P4 and QA are finished and separately approved.

Existing routing UI and local data remain available in builds without this configuration.
