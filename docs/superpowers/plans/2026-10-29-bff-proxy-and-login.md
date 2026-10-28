# BFF Proxy, Admin Login and Server-Enforced Secret Masking Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move every API key out of the browser into a Next.js server-side proxy with guest/admin sessions, and make the vault itself mask secrets for keys that lack `REVEAL_SECRETS`.

**Architecture:** The Spring Boot vault gains a `REVEAL_SECRETS` permission, placeholder-preserving writes, a real audit endpoint and a seeded `playground` sandbox. The Next.js app gains a catch-all route `/api/vault/[...path]` that picks a server-held key per role and method, plus cookie-based admin login. The browser only ever talks to its own origin.

**Tech Stack:** Spring Boot 3.2 / Java 17 / JUnit 5 + Mockito + MockMvc; Next.js 15.5 App Router / React 19 / TypeScript; Vitest (new dev dependency, frontend only); Web Crypto.

**Spec:** `docs/superpowers/specs/2026-10-29-bff-proxy-and-login-design.md`

## Global Constraints

- No API key in the browser: no `NEXT_PUBLIC_VAULT_*` variables, no key in any URL, header set by client code, or response.
- No new frontend runtime dependencies. Vitest is the only new dev dependency.
- Mask placeholder is exactly `********` (`SecretMaskingUtil.MASKED_PLACEHOLDER`).
- Sandbox app is `playground`, environment `development`.
- Session cookie `vault_session`, payload `{ "role": "admin", "exp": <ms epoch> }`, TTL 8 hours, `HttpOnly`, `SameSite=Lax`, `Path=/`, `Secure` unless the request is plain `http://localhost`.
- Proxy: `export const maxDuration = 60`; guest request bodies over 256 KB rejected with 413.
- Client retries only idempotent requests (GET/HEAD) on 502/503/504 or network error, with backoff, for up to 90 seconds.
- Audit endpoint: `limit` default 50, maximum 200, newest first.
- UI work follows `ui-ux-pro-max` (design authority) + `ui-styling`, components from 21st.dev MCP first.
- Chat replies are terse; code, comments, commits and docs use normal prose.

## Review Focus

1. A guest explicitly requesting `?masked=false` must still get masked secrets. Test in Task 1.
2. Proxy paths with `..`, `.`, empty segments or encoded slashes must be refused before any upstream call. Test in Task 6.
3. A tampered, expired or malformed `vault_session` cookie must quietly mean "guest", never a 500. Test in Task 6.
4. A placeholder nested deep inside `secure_settings` (for example `database.secure_settings.password`) must resolve to the stored value at that exact path. Test in Task 3.
5. A cold-start 502 on a POST must not be retried automatically (it could create a duplicate version). Test in Task 8.

---

### Task 1: `REVEAL_SECRETS` and server-enforced masking on configuration endpoints

**Files:**
- Modify: `src/main/java/com/vault/configmanager/model/ApiKeyRole.java`
- Modify: `src/main/java/com/vault/configmanager/util/SecretMaskingUtil.java`
- Create: `src/main/java/com/vault/configmanager/security/SecretVisibility.java`
- Modify: `src/main/java/com/vault/configmanager/controller/ConfigurationController.java`
- Test: `src/test/java/com/vault/configmanager/util/SecretMaskingUtilTest.java`, `src/test/java/com/vault/configmanager/controller/ConfigurationControllerTest.java`

**Interfaces:**
- Produces: `ApiKeyRole.REVEAL_SECRETS`; `SecretVisibility.canReveal(HttpServletRequest): boolean` (true only when `AUTHENTICATED_KEY_ATTR` is an `ApiKey` with `hasPermission("REVEAL_SECRETS")`; ADMIN implies it via existing `hasPermission`); `SecretMaskingUtil.isSensitivePath(String dotPath): boolean`; `SecretMaskingUtil.maskDiff(DiffResponse): DiffResponse` (returns a copy).

- [ ] **Step 1: Write failing util tests** in `SecretMaskingUtilTest`:
  - `isSensitivePath_detectsSecureSettingsSegment`: `isSensitivePath("database.secure_settings.password")` is true; `isSensitivePath("database.host")` is false; `isSensitivePath("auth.password")` is true (sensitive key name).
  - `maskDiff_masksSecretValuesButKeepsPaths`: a diff with `added={"payment.secure_settings.apiKey":"sk_x","payment.currency":"USD"}` and `modified={"database.secure_settings.password":{"oldValue":"a","newValue":"b"}}` yields `added.get("payment.secure_settings.apiKey")=="********"`, `added.get("payment.currency")=="USD"`, `modified...oldValue=="********"` and `newValue=="********"`, same key sets.
- [ ] **Step 2: Run** `mvn -q test -Dtest=SecretMaskingUtilTest` — expect compile failure / FAIL.
- [ ] **Step 3: Implement** `REVEAL_SECRETS`, `isSensitivePath` (split on `.`, any segment matching `isSecureSettingsKey` or `isSensitiveKey`), `maskDiff`, and `SecretVisibility.canReveal`.
- [ ] **Step 4: Write failing controller tests** in `ConfigurationControllerTest`, registering a key `sk-guest-read` with `READ, EXPORT` on `*`/`*` and `sk-guest-sandbox` with `READ, WRITE, ROLLBACK` on `playground`/`*` via `TestApiKeys`:
  - `getLatest_withoutReveal_masksEvenWhenMaskedFalseRequested`: GET `/config/user-svc/prod?masked=false` with `sk-guest-read`, payload `{"host":"h","secure_settings":{"pw":"s"}}` → `$.payload.secure_settings.pw == "********"`, `$.payload.host == "h"`.
  - `getLatest_admin_revealsPlaintext`: same with `sk-dev-key-001` → `"s"`.
  - `getByVersion_withoutReveal_masks`, `rollback_withoutReveal_masksResponse` (sandbox key on `playground`), `create_withoutReveal_masksResponse`, `bulk_withoutReveal_masksEachResult` (admin-scoped bulk not needed; use a key with WRITE on `*` and no ADMIN).
  - `diff_withoutReveal_masksSecretValues`: service returns the diff from Step 1 → JSON values `"********"`.
  - `searchByPath_withoutReveal_masksSecretPath`: path `database.secure_settings.password` → `$.value == "********"`; path `database.host` → plain value.
- [ ] **Step 5: Run** `mvn -q test -Dtest=ConfigurationControllerTest` — expect FAIL.
- [ ] **Step 6: Implement** in `ConfigurationController`: every handler returning configuration values applies masking when `masked || !SecretVisibility.canReveal(request)` (`maskMap` for `ConfigResponse` payloads, including each result of `BulkConfigResponse`; `maskDiff` for diff; `isSensitivePath` → `MASKED_PLACEHOLDER` for path search). Add `HttpServletRequest` parameters where missing (diff).
- [ ] **Step 7: Run** `mvn -q test` — expect all pass.
- [ ] **Step 8: Commit** `feat(security): add REVEAL_SECRETS and mask config responses for keys without it`

### Task 2: Export masking and binary content gate

**Files:**
- Modify: `src/main/java/com/vault/configmanager/service/ExportService.java:46`
- Modify: `src/main/java/com/vault/configmanager/controller/ExportController.java`, `ConfigurationController.java` (export handler), `BinaryConfigController.java:72-93`
- Test: `ExportServiceTest`, `ExportControllerTest`, `BinaryConfigControllerTest`

**Interfaces:**
- Consumes: `SecretVisibility.canReveal`, `SecretMaskingUtil.maskMap`.
- Produces: `ExportService.exportConfig(String app, String env, Integer version, String format, boolean reveal, String ip, String apiKey): ExportResult` (replaces the old signature; both controllers pass `SecretVisibility.canReveal(request)`).

- [ ] **Step 1: Write failing tests:**
  - `ExportServiceTest.exportConfig_withoutReveal_masksAllFormats`: payload with `secure_settings.pw="s3cr3t"`, for `json`, `yaml`, `env` with `reveal=false` the content does not contain `s3cr3t` and contains `********`; with `reveal=true` it contains `s3cr3t`.
  - `BinaryConfigControllerTest.download_withoutReveal_returns403`: guest-read key → 403, service never called; admin key → 200.
  - `ExportControllerTest.export_passesRevealFalseForGuestKey`: verify `exportConfig(..., eq(false), ...)` for a READ+EXPORT key.
- [ ] **Step 2: Run** `mvn -q test -Dtest='ExportServiceTest,ExportControllerTest,BinaryConfigControllerTest'` — FAIL.
- [ ] **Step 3: Implement**: `ExportService` masks the decrypted payload with `maskMap` before formatting when `!reveal`; binary download throws `ScopeViolationException("Downloading binary content requires the REVEAL_SECRETS permission")` when `!canReveal`. Listing and versions endpoints unchanged.
- [ ] **Step 4: Run** `mvn -q test` — all pass.
- [ ] **Step 5: Commit** `feat(security): mask exports and gate binary downloads behind REVEAL_SECRETS`

### Task 3: Masked placeholder on write keeps the stored secret

**Files:**
- Modify: `src/main/java/com/vault/configmanager/service/ConfigurationService.java` (`createConfiguration`)
- Test: `src/test/java/com/vault/configmanager/service/ConfigurationServiceTest.java`

**Interfaces:**
- Produces: placeholder semantics only (no public API change). Unknown-path placeholders raise `IllegalArgumentException` (already mapped to 400 by `GlobalExceptionHandler`).

- [ ] **Step 1: Write failing tests:**
  - `create_placeholderKeepsStoredSecret`: latest v1 stored with `database.secure_settings.password="real"`; create v2 with `database.secure_settings.password="********"` and `database.host="new"` → saved v2 decrypts to `password=="real"`, `host=="new"`.
  - `create_changedSecretIsStored`: placeholder replaced by `"rotated"` → v2 decrypts to `"rotated"`.
  - `create_placeholderAtUnknownPath_rejected`: placeholder at `database.secure_settings.newKey` with no stored value → `IllegalArgumentException` whose message names the path; nothing saved.
  - `create_placeholderOnFirstVersion_rejected`.
  - `patch_placeholderKeepsStoredSecret`: `updateConfigurationPartial` with `{"database":{"secure_settings":{"password":"********"}}}` keeps `"real"`.
- [ ] **Step 2: Run** `mvn -q test -Dtest=ConfigurationServiceTest` — FAIL.
- [ ] **Step 3: Implement** a private `Document resolveMaskedPlaceholders(Document incoming, Document currentDecrypted)` in `ConfigurationService`, called in `createConfiguration` before encryption. Fetch the latest version first (move the existing `findTop...` lookup up) and decrypt it only when the incoming payload contains a placeholder under `secure_settings`.
- [ ] **Step 4: Run** `mvn -q test` — all pass.
- [ ] **Step 5: Commit** `fix(config): keep stored secrets when a masked placeholder is saved`

### Task 4: Real audit log endpoint and audited denials

**Files:**
- Create: `src/main/java/com/vault/configmanager/controller/AuditController.java`
- Modify: `src/main/java/com/vault/configmanager/service/AuditService.java`, `security/ApiKeyAuthFilter.java`
- Test: `AuditServiceTest`, new `AuditControllerTest`, `ApiKeyAuthFilterTest`; update every test that constructs `ApiKeyAuthFilter`

**Interfaces:**
- Consumes: `SecretVisibility.canReveal`, `ApiKey.isAppAllowed/isEnvAllowed`.
- Produces:
  - `GET /api/v1/vault/audit?limit&app&env` → `List<AuditLog>` (READ required via the filter's default GET rule).
  - `AuditService.recent(int limit, String app, String env, ApiKey scope, boolean revealIp): List<AuditLog>` — clamps `limit` to 1..200, sorts `$natural` descending (via `MongoTemplate`), drops entries outside the key's app/env scope (entries whose app is not a real app name, e.g. `"keys"`, `"bulk"`, a URI, are visible only to keys with `*` apps), and truncates IPs when `!revealIp`.
  - `AuditService.truncateIp(String ip): String` — IPv4 `203.0.113.7` → `203.0.113.x`; IPv6 keeps the first 3 groups then `:x`; null/blank → `"unknown"`.
  - `ApiKeyAuthFilter(ObjectMapper, ApiKeyService, AuditService)`; logs `AUTH_DENIED` on 401 and `SCOPE_DENIED` on 403 with `failureReason = "<METHOD> <URI>"`, masked key (or `"none"`), remote IP, app/env when extracted.

- [ ] **Step 1: Write failing tests:**
  - `AuditServiceTest.truncateIp_*` for the three IP cases.
  - `AuditServiceTest.recent_filtersByScopeAndClampsLimit`: limit 500 → query limit 200; a key scoped to `payment-gateway` sees only that app's entries.
  - `AuditControllerTest.guestKey_getsTruncatedIps`, `adminKey_getsFullIps`.
  - `ApiKeyAuthFilterTest.scopeDenied_isAudited`: 403 → `auditService.log(eq("SCOPE_DENIED"), eq("billing-svc"), eq("dev"), any(), any(), eq(false), eq("GET /api/v1/vault/config/billing-svc/dev"))`; `invalidKey_isAudited` → `AUTH_DENIED`.
- [ ] **Step 2: Run** `mvn -q test` — FAIL (and compile errors where tests build the filter; fix those constructions to pass `mock(AuditService.class)`).
- [ ] **Step 3: Implement** the endpoint, `recent`, `truncateIp`, and filter logging. `AuditService` gains a constructor taking `MongoTemplate` (keep the existing one-arg constructor for tests that don't need queries).
- [ ] **Step 4: Run** `mvn -q test` — all pass.
- [ ] **Step 5: Commit** `feat(audit): expose the audit log and record refused requests`

### Task 5: Playground sandbox seeding and dev dashboard keys

**Files:**
- Modify: `src/main/java/com/vault/configmanager/config/DataBootstrapSeeder.java`, `service/ApiKeyService.java`
- Test: `ApiKeyServiceTest`, new `DataBootstrapSeederTest`

**Interfaces:**
- Produces: `DataBootstrapSeeder.ensurePlayground()` (package-private, called from `run` independently of the "seed if empty" check); dev keys `sk-dev-guest-read` (READ, EXPORT; `*`/`*`) and `sk-dev-guest-sandbox` (READ, WRITE, ROLLBACK; `playground`/`*`), both in `WELL_KNOWN_DEV_KEYS`. `sk-dev-key-001` remains the dev admin key.

- [ ] **Step 1: Write failing tests:**
  - `DataBootstrapSeederTest.ensurePlayground_createsV1WhenMissing`: repository returns empty for `("playground","development")` → `createConfiguration` called once with app `playground`, env `development`, payload containing a `secure_settings` block.
  - `ensurePlayground_isIdempotent`: repository returns a document → no call.
  - `ApiKeyServiceTest.seedDefaultKeys_inDevMode_seedsEachMissingWellKnownKey`: repository already has `sk-dev-key-001` → only the 4 missing keys are saved (replace the existing `times(3)` test).
  - `seedDefaultKeys_outsideDevMode_deactivatesGuestDevKeysToo`.
- [ ] **Step 2: Run** `mvn -q test` — FAIL.
- [ ] **Step 3: Implement**. Dev seeding becomes per-key idempotent (seed each well-known key whose hash is absent) instead of "only when the collection is empty". Playground v1 payload: `{"feature":{"darkMode":false,"banner":"Welcome to the playground"},"limits":{"requestsPerMinute":60},"secure_settings":{"apiToken":"pg_sandbox_token_123"}}`, tags `{"purpose":"guest-sandbox"}`.
- [ ] **Step 4: Run** `mvn -q test` — all pass.
- [ ] **Step 5: Commit** `feat(seed): add the playground sandbox and dev dashboard keys`

### Task 6: Frontend server-side security logic with Vitest

**Files:**
- Modify: `frontend/package.json` (dev dependency `vitest`, script `"test": "vitest run"`)
- Create: `frontend/vitest.config.ts`, `frontend/src/lib/server/session.ts`, `frontend/src/lib/server/proxy-policy.ts`, `frontend/src/lib/server/config.ts`
- Test: `frontend/src/lib/server/session.test.ts`, `proxy-policy.test.ts`, `config.test.ts`

**Interfaces:**
- Produces:
  - `session.ts`: `type Role = "guest" | "admin"`; `SESSION_COOKIE = "vault_session"`; `SESSION_TTL_MS = 8 * 60 * 60 * 1000`; `signSession(exp: number, secret: string): Promise<string>`; `verifySession(token: string | undefined, secret: string, now?: number): Promise<Role>`; `passwordMatches(input: string, expected: string): Promise<boolean>` (SHA-256 both, constant-time compare).
  - `proxy-policy.ts`: `type VaultKeys = { guestRead: string; guestSandbox: string; admin: string }`; `selectKey(role: Role, method: string, keys: VaultKeys): string`; `isAllowedPath(segments: string[]): boolean` (non-empty; no `""`, `"."`, `".."`, or segment containing `/`, `\`, `%`; first segment in `config, export, binaries, stream, keys, stats, dashboard, audit`); `isGuestBlocked(segments: string[]): boolean` (`keys/**`, or `stream` with no further segment); `isSameOrigin(origin: string | null, requestUrl: string): boolean`; `GUEST_MAX_BODY_BYTES = 262144`.
  - `config.ts`: `readServerConfig(env?: NodeJS.ProcessEnv): { apiUrl: string; keys: VaultKeys; adminPassword: string; sessionSecret: string }`, throwing `Error("Missing server configuration: <NAMES>")` listing every missing variable among `VAULT_API_URL, VAULT_GUEST_READ_KEY, VAULT_GUEST_SANDBOX_KEY, VAULT_ADMIN_KEY, ADMIN_PASSWORD, SESSION_SECRET`; strips a trailing `/` from `apiUrl`.

- [ ] **Step 1: Install** `npm install -D vitest` in `frontend/`; add the script and a minimal `vitest.config.ts` (`environment: "node"`, `@` alias to `src`).
- [ ] **Step 2: Write failing tests:**
  - session: round trip → `"admin"`; expired (`now > exp`) → `"guest"`; flipped payload byte → `"guest"`; wrong secret → `"guest"`; `undefined`, `""`, `"garbage"`, `"a.b.c"` → `"guest"`; `passwordMatches("pw","pw")` true, `("pw","pW")` false.
  - proxy-policy: `selectKey("admin","POST",k)===k.admin`; `selectKey("guest","GET",k)===k.guestRead`; `"HEAD"` → guestRead; `"POST"|"PATCH"|"PUT"|"DELETE"` → guestSandbox; `isAllowedPath(["config","a","b"])` true; `[]`, `["..","x"]`, `["config","..","keys"]`, `["config","a%2Fb"]`, `["admin"]` false; `isGuestBlocked(["keys"])`, `["keys","id"]`, `["stream"]` true and `["stream","a","b"]` false; `isSameOrigin("https://x.app","https://x.app/api/vault/config")` true, `null` or `"https://evil.app"` false.
  - config: complete env → values, trailing slash removed; missing two vars → error message naming both.
- [ ] **Step 3: Run** `npm test` — FAIL.
- [ ] **Step 4: Implement** the three modules with Web Crypto (`crypto.subtle`, HMAC-SHA256, base64url). Server-only code: never import these from client components.
- [ ] **Step 5: Run** `npm test` — pass; `npx tsc --noEmit` — clean.
- [ ] **Step 6: Commit** `feat(frontend): add session, proxy policy and server config modules`

### Task 7: Proxy route and auth routes

**Files:**
- Create: `frontend/src/app/api/vault/[...path]/route.ts`, `frontend/src/app/api/auth/login/route.ts`, `logout/route.ts`, `session/route.ts`
- Create: `frontend/.env.example`
- Modify: `docker-compose.yml` (frontend service environment)
- Test: `frontend/src/app/api/vault/route.test.ts`, `frontend/src/app/api/auth/auth.test.ts`

**Interfaces:**
- Consumes: Task 6 modules.
- Produces: same-origin endpoints `/api/vault/*` (GET, POST, PUT, PATCH, DELETE; `maxDuration = 60`; `dynamic = "force-dynamic"`), `POST /api/auth/login` `{password}` → 204 + cookie or 401, `POST /api/auth/logout` → 204 clearing the cookie, `GET /api/auth/session` → `{ role }`.

- [ ] **Step 1: Write failing tests** (call the exported handlers with `new Request(...)`, stub `globalThis.fetch`, set `process.env`):
  - guest GET `/api/vault/config/a/b?x=1` → upstream `${VAULT_API_URL}/api/v1/vault/config/a/b?x=1` with `X-API-Key: guestRead`; incoming `X-API-Key` header and `apiKey` query parameter are not forwarded.
  - guest POST with matching `Origin` → `guestSandbox`; without `Origin` or with a foreign one → 403, no upstream call.
  - guest `/api/vault/keys` → 403 with JSON `{message}`; disallowed path → 404; no upstream call in either case.
  - guest POST with `Content-Length` over 262144 → 413.
  - admin cookie (from `signSession`) → `admin` key.
  - upstream status, `Content-Type` and `Content-Disposition` are passed through; body is streamed (assert `response.body` is the upstream stream).
  - missing `VAULT_ADMIN_KEY` → 500 with the `Missing server configuration` message.
  - login: correct password → 204 and `Set-Cookie` containing `vault_session=`, `HttpOnly`, `SameSite=Lax`; wrong password → 401; logout clears; session route returns `{role:"guest"}` without cookie and `{role:"admin"}` with a valid one.
- [ ] **Step 2: Run** `npm test` — FAIL.
- [ ] **Step 3: Implement** the routes. Forward with `fetch(url, { method, headers, body: req.body, duplex: "half", signal: req.signal, cache: "no-store" })` and return `new Response(upstream.body, { status, headers })`. Failed logins wait 500 ms before answering.
- [ ] **Step 4: Add** `.env.example` (all six variables, dev values commented as dev-only) and docker-compose frontend env: `VAULT_API_URL=http://backend:8080`, `VAULT_GUEST_READ_KEY=sk-dev-guest-read`, `VAULT_GUEST_SANDBOX_KEY=sk-dev-guest-sandbox`, `VAULT_ADMIN_KEY=sk-dev-key-001`, `ADMIN_PASSWORD=admin`, `SESSION_SECRET=<64 hex chars, dev only>`; remove both `NEXT_PUBLIC_VAULT_*` lines.
- [ ] **Step 5: Run** `npm test` and `npx tsc --noEmit` — pass.
- [ ] **Step 6: Commit** `feat(frontend): add the vault proxy and admin session routes`

### Task 8: Client switch to the proxy, wake-up retry and role context

**Files:**
- Modify: `frontend/src/lib/api.ts`, `frontend/src/context/VaultContext.tsx`, `frontend/src/app/export/page.tsx`
- Test: `frontend/src/lib/api.test.ts`

**Interfaces:**
- Consumes: Task 7 endpoints.
- Produces:
  - `api.ts`: base path `"/api/vault"`; no key handling anywhere; `exportConfig(...)` becomes `Promise<string>` (fetches text) and `export/page.tsx` uses it; `getBinaryDownloadUrl(...)` returns a relative URL without a key; `subscribeToStream(app, env, onMessage, onError?, onOpen?)` uses a relative URL and calls `onOpen` on every (re)connect; `onWakingChange(listener: (waking: boolean) => void): () => void`; `authApi = { login(password): Promise<void>, logout(): Promise<void>, session(): Promise<Role> }`.
  - `VaultContext`: adds `role: Role`, `refreshRole(): Promise<void>`, `isWaking: boolean`, `canWrite(app: string): boolean` (`role === "admin" || app === "playground"`); `"playground"` is always in `apps`.
- [ ] **Step 1: Write failing tests** (stub `fetch`, fake timers):
  - GET answered `503, 503, 200` → resolves with the 200 body; waking listener receives `true` then `false`.
  - GET still failing after 90 s of backoff → rejects with a message mentioning the vault waking up.
  - POST answered `502` → rejects immediately, `fetch` called exactly once.
  - no request made by `api.ts` carries an `X-API-Key` header or `apiKey` query parameter.
- [ ] **Step 2: Run** `npm test` — FAIL.
- [ ] **Step 3: Implement** the changes; delete `NEXT_PUBLIC_VAULT_API_URL` / `NEXT_PUBLIC_VAULT_API_KEY` usage.
- [ ] **Step 4: Run** `npm test`, `npx tsc --noEmit`, `npm run lint` — clean; `grep -rn "sk-\|NEXT_PUBLIC_VAULT" frontend/src` returns nothing.
- [ ] **Step 5: Commit** `feat(frontend): route all vault calls through the proxy`

### Task 9: Role-aware UI

**Files:**
- Create: `frontend/src/components/auth/LoginDialog.tsx`, `frontend/src/components/auth/RoleBadge.tsx`
- Modify: `frontend/src/components/dashboard/Header.tsx`, `frontend/src/app/configs/page.tsx`, `frontend/src/app/binaries/page.tsx`, `frontend/src/app/access/page.tsx`, `frontend/src/app/audit/page.tsx`, `frontend/src/lib/api.ts` (`getAuditLog`)

**Interfaces:**
- Consumes: `useVault().role/canWrite/refreshRole/isWaking`, `authApi`, `subscribeToStream(..., onOpen)`.
- Produces: `vaultApi.getAuditLog(params?: { limit?: number; app?: string; env?: string }): Promise<AuditEntry[]>` where `AuditEntry` mirrors the backend `AuditLog` fields (`id, action, applicationName, environment, requestingIp, apiKey, success, failureReason, timestamp`).

- [ ] **Step 1: Design pass**: run the `ui-ux-pro-max` search for the badge, the login dialog and the locked/disabled states; fetch matching components from the 21st.dev MCP first.
- [ ] **Step 2: Header**: role badge ("Guest · read-only" / "Admin"), login button opening `LoginDialog` (password field, error message on 401, calls `refreshRole` on success), logout for admin, a "Waking up the vault…" indicator while `isWaking`.
- [ ] **Step 3: Configs and binaries pages**: when `!canWrite(app)`, disable save, rollback and upload with the hint "Guests can write only in the playground app" and a button that switches to `playground/development`. In `playground`, add "Reset playground" (rollback to v1). Binary download buttons are hidden for guests.
- [ ] **Step 4: Access page**: guests see a locked panel explaining key management is admin-only; no key requests are made. Add `REVEAL_SECRETS` to the role options when creating keys.
- [ ] **Step 5: Audit page**: remove the mock entries; load `getAuditLog({ limit: 50 })`; subscribe to the stream of the selected app/env (`playground/development` by default for guests) and reload on each event and on `onOpen`; show `SCOPE_DENIED`/`AUTH_DENIED` entries distinctly.
- [ ] **Step 6: Verify**: `npm run build` succeeds; `npm test`, `npx tsc --noEmit`, `npm run lint` clean.
- [ ] **Step 7: Commit** `feat(frontend): role-aware dashboard with admin login and live audit log`

### Task 10: End-to-end verification

**Files:** none (verification only; fix forward in the owning task's files if anything fails)

- [ ] **Step 1: Start** a MongoDB 7 replica set in Docker, the backend jar with `SPRING_PROFILES_ACTIVE=dev`, and the frontend with `npm run build && npm start` using the docker-compose dev values (`VAULT_API_URL=http://localhost:8080`).
- [ ] **Step 2: Bundle check**: `grep -rl "sk-dev\|sk_live\|X-API-Key" frontend/.next/static` returns nothing.
- [ ] **Step 3: Guest flows** (browser automation): `payment-gateway` secrets show `********`; save/rollback disabled there; a forced `POST /api/vault/config/payment-gateway/production/rollback/1` returns 403 and appears in the audit page as `SCOPE_DENIED`; in `playground` a new version saves, its secret survives (stored value unchanged when read as admin), the audit page updates live, "Reset playground" returns it to v1 content; binary download hidden; access page locked; no request URL contains a key.
- [ ] **Step 4: Admin flows**: wrong password → error, right password → badge "Admin", real secret values visible, key list loads, logout returns to guest.
- [ ] **Step 5: Backend suite**: `mvn -B clean test` — 0 failures. Frontend: `npm test` — 0 failures.
- [ ] **Step 6: Commit** any fixes, then hand back with the deployment order from the spec (section 8).
