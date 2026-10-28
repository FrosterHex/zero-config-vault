# Backend-for-Frontend Proxy, Admin Login and Server-Enforced Secret Masking

Date: 2026-10-29
Status: approved 2026-10-29
Branch: `fix/security-hardening` (builds on Phase 1, commit `079bd3c`)

## Intent

The live dashboard on Vercel currently ships an ADMIN API key (`sk-dev-key-001`) inside its public
JavaScript bundle, so anyone who opens the site holds full control of the vault. Phase 1 deactivates
that key outside dev mode, which means Phase 1 cannot be merged until the dashboard stops needing a key
in the browser.

This work moves every credential to the server and gives the dashboard two kinds of user:

- **Guest** (no login): panelists and visitors can explore the whole vault safely.
- **Admin** (password): the project owner gets full control, including decrypted secrets and key management.

It is also a showcase for a live demo in front of a panel, so the security story should be visible
on screen, not only true in the code.

## Goals

1. No API key ever reaches the browser: not in the JS bundle, URLs, local storage or responses.
2. Guests can read every app and environment, with secret values masked.
3. Guests can create versions, roll back and upload binaries in one sandbox app, `playground`,
   and nowhere else.
4. Admin logs in with a password and gets full access: decrypted secrets, writes everywhere, key management.
5. Secret masking is enforced by the vault itself, based on the calling key's permissions, so a leaked
   guest key still cannot read plaintext secrets.
6. Live updates (SSE) keep working through the proxy.
7. The audit page shows the real audit log (it shows mock data today), including requests the vault refused.
8. Editing a configuration while secrets are masked never overwrites the stored secrets.
9. The panel can see it: masked to revealed on login, a blocked guest write recorded in the audit log,
   a live sandbox update.

## Non-goals

- Multiple user accounts, sign-up, or per-person identity (one admin, anonymous guests).
- OAuth or single sign-on.
- Distributed rate limiting.
- Tightening backend CORS (possible afterwards, since the browser no longer calls the backend directly).
- Phase 2 correctness items (TTL retention, environment enum, duplicate SSE events, bulk transactions).

## Anti-goals (would be a failure even if it technically works)

- Any endpoint that returns plaintext secrets to a guest (including diff, export, path search, binary download).
- A login flow that can break on venue Wi-Fi or captive portals (external redirects).
- The proxy silently falling back to a hard-coded key or `localhost` when configuration is missing.
- Guests being able to alter or roll back the curated showcase apps.

## Constraints

- Frontend: Next.js 15.5 App Router on Vercel, `output: "standalone"`. All pages are client components.
  Nearly every backend call goes through `frontend/src/lib/api.ts`; the exceptions are
  `frontend/src/app/export/page.tsx` (hard-codes `sk-dev-key-001`), binary download links and the
  SSE `EventSource`, both of which put the key in the URL today.
- No new frontend runtime dependencies; use Web Crypto and the platform `fetch`.
- Vercel functions have a maximum duration; streamed responses are cut when it is reached.
- Backend: Spring Boot 3.2, Java 17, on Render's free tier (cold starts of up to about a minute).
- Database: MongoDB Atlas M0. Phase 1 RBAC already enforces roles and app/env scope per key.
- UI work follows the project rule: `ui-ux-pro-max` is the design authority, `ui-styling`
  (shadcn/ui + Tailwind) implements, and components come from the 21st.dev MCP server first.

## Approaches considered

| Approach | Trade-off | Decision |
|---|---|---|
| **A. Next.js catch-all proxy route + signed session cookie** | One serverless function call per request; SSE reconnects when Vercel cuts the stream | **Chosen**: standard backend-for-frontend pattern, no new dependencies, full per-request control |
| B. Same proxy with GitHub login via Auth.js | New dependency, OAuth app setup, external redirect that can fail during a live demo | Rejected: overkill for one admin, demo risk |
| C. Middleware rewrite to Render with an injected key header | No function time limit, but unverified whether Vercel forwards added headers to an external rewrite, and harder to customise per request | Rejected: needs a spike, less control |

Secret masking was also considered at the proxy only. Rejected: export, diff and path search return
decrypted values that the proxy cannot reliably mask, and a leaked guest key would bypass it.
Masking is enforced by the vault instead.

## Design

### 1. Backend: `REVEAL_SECRETS` permission

- Add `REVEAL_SECRETS` to `ApiKeyRole`. `ADMIN` implies it (consistent with `ApiKey.hasPermission`).
- A key without `REVEAL_SECRETS` always receives masked responses, exactly as if it had requested
  `?masked=true` today (same rule as `SecretMaskingUtil.maskMap`: everything under `secure_settings`
  plus the existing sensitive key names).
- This applies to every endpoint that returns configuration values:
  - `GET /config/{app}/{env}` and `GET /config/{app}/{env}/version/{n}`
  - write responses: `POST /config`, `PATCH /config/{app}/{env}`, `POST .../rollback/{n}`, bulk import results
  - `GET /config/{app}/{env}/diff`: secret paths still appear as added/removed/modified, values masked
  - `GET /config/{app}/{env}/search?path=...`: masked when the path points at a secret
  - export (`/config/{app}/{env}/export` and `/export/{app}/{env}`), all formats
  - global search already never returns secrets (Phase 1)
- Binary file **content** is secret material: `GET /binaries/{app}/{env}/{filename}` requires
  `REVEAL_SECRETS`. Listing, version history, sizes and hashes stay readable with `READ`.
- Existing `?masked=true` keeps working for any caller.
- Where the masking is applied (controller, service, or response advice) is left to the builder,
  but it must be one shared rule, covered by a test per endpoint above.

### 1b. Backend: masked values on write keep the stored secret

Today the configs page loads payloads masked by default and sends the whole editor payload back on save,
so `********` is encrypted and stored as the new secret value (this already affects admin edits). With
guests always masked it would wipe secrets on every sandbox edit.

- On `POST /config`, `PATCH /config/{app}/{env}` and bulk import, any value under `secure_settings` that is
  exactly the mask placeholder `********` means "keep the current value at this path". The service replaces
  it with the decrypted current value before encrypting the new version.
- If there is no current value at that path (new key, or first version), the request is rejected with 400.
- Secrets therefore behave as write-only for guests: they can set a new secret value in `playground`,
  but never read one back.
- Tests: placeholder preserved, changed value stored, placeholder at an unknown path rejected, and the
  same behaviour for an admin key.

### 1c. Backend: real audit log

- New `GET /api/v1/vault/audit?limit=<n>&app=<a>&env=<e>`: newest entries first (`$natural: -1` on the
  capped collection), `limit` default 50, maximum 200. Requires `READ`; results are limited to the apps and
  environments in the key's scope.
- For keys without `REVEAL_SECRETS`, the requester IP is truncated (IPv4 last octet, IPv6 last 80 bits
  replaced). Masked API keys are already stored masked.
- `ApiKeyAuthFilter` records refused requests: `AUTH_DENIED` (401) and `SCOPE_DENIED` (403), with method,
  path, masked key and IP. The capped collection bounds the volume.
- Audit page: loads real entries and refreshes when a live event arrives on the stream for the currently
  selected app and environment (default `playground/development` for guests).

### 2. Backend: sandbox app and keys

- Sandbox app: `playground`, environment `development` (valid under the current `$jsonSchema`).
- `DataBootstrapSeeder` ensures `playground/development` exists with a v1 (a few plain settings and a
  sample `secure_settings` block), independently of the existing "seed only if empty" check, and
  idempotently, so it also appears on the already-seeded Atlas database.
- Three keys serve the dashboard, all created through the existing `POST /keys` admin API:

  | Key | Apps | Envs | Roles |
  |---|---|---|---|
  | guest-read | `*` | `*` | READ, EXPORT |
  | guest-sandbox | `playground` | `*` | READ, WRITE, ROLLBACK |
  | admin | `*` | `*` | ADMIN (implies REVEAL_SECRETS) |

- Resetting the sandbox needs no new endpoint: "Reset playground" in the UI rolls `playground/development`
  back to v1 (history stays immutable, the current version is clean). Anyone with sandbox access can use it,
  so it also works right before the demo.
- Abuse limits: the proxy rejects guest request bodies over 256 KB; the backend keeps Spring's default
  1 MB multipart file limit. Unlimited version spam by anonymous visitors is an accepted risk for a demo
  deployment on Atlas M0 (distributed rate limiting is a non-goal).
- In the `dev` profile only, these three are seeded as well-known dev keys (added to
  `WELL_KNOWN_DEV_KEYS`, so they are deactivated outside dev mode like the others), so
  `docker-compose up` works without manual steps.

### 3. Frontend: proxy route

- New route handler `frontend/src/app/api/vault/[...path]/route.ts` for GET, POST, PUT, PATCH, DELETE.
- Forwards `/api/vault/<path>?<query>` to `${VAULT_API_URL}/api/v1/vault/<path>?<query>`. Nothing
  outside that prefix can be reached.
- Drops any incoming `X-API-Key`, `apiKey` query parameter and cookies; sets `X-API-Key` server-side.
- Key selection:
  - admin session: admin key, for every request
  - guest, safe method (GET, HEAD): guest-read key
  - guest, any other method: guest-sandbox key
- Guests are refused at the proxy (403 with a friendly message) for `/keys/**` and the global stream
  (`/stream` without app/env). The vault would refuse these too; the proxy just answers faster and clearer.
- Request bodies are streamed through (JSON and multipart uploads). Responses are streamed back with
  status, `Content-Type` and `Content-Disposition` preserved, so SSE and downloads work.
- CSRF: for non-GET requests the proxy requires an `Origin` header matching the site's own origin.
  Together with a `SameSite=Lax` session cookie this blocks cross-site writes.
- Missing configuration (`VAULT_API_URL` or a required key) returns 500 with a clear message.
  There is no fallback to `localhost` or a hard-coded key.
- Function limits: the proxy route sets `export const maxDuration = 60`, the maximum that works on every
  Vercel plan, so the design does not depend on the plan.
- Cold starts: when the proxy gets 502/503/504 or times out, the client shows "waking up the vault" and
  retries with backoff for up to about 90 seconds before showing an error.
- SSE: the stream is cut at the function limit and the browser reconnects. On every (re)connect the client
  refetches the data it shows, so an event missed during a reconnect cannot leave the page stale.
- Demo runbook: open the site about two minutes before presenting so Render is already awake.

### 4. Frontend: admin session

- `POST /api/auth/login` with `{ password }`. The password is compared with `ADMIN_PASSWORD` in
  constant time (compare SHA-256 digests). A failure returns 401 after a short fixed delay.
- On success, set cookie `vault_session` = `base64url(payload).base64url(HMAC-SHA256(SESSION_SECRET, payload))`,
  payload `{ "role": "admin", "exp": <now + 8 h> }`. Flags: `HttpOnly`, `Secure` (except local http),
  `SameSite=Lax`, `Path=/`.
- `POST /api/auth/logout` clears the cookie. `GET /api/auth/session` returns `{ role: "guest" | "admin" }`.
- No cookie, a bad signature or an expired payload all mean "guest". There is never an error page for it.

### 5. Frontend: client changes

- `api.ts` calls relative `/api/vault/...` paths and stops setting keys. The download URL and the
  SSE URL become relative and carry no key. The export page uses `api.ts` instead of its own fetch.
- `NEXT_PUBLIC_VAULT_API_URL` and `NEXT_PUBLIC_VAULT_API_KEY` are removed everywhere.
- `VaultContext` loads the role from `/api/auth/session` and exposes it to pages.
- Header: a role badge ("Guest · read-only" or "Admin") and a login/logout control. Login is a small
  password dialog, no full page navigation.
- Guests on any app other than `playground`: write controls (new version, rollback, upload) are
  disabled with a hint "Guests can write only in the playground app" and a shortcut to it. The vault
  still enforces this, so the blocked path can also be demonstrated deliberately.
- Access page (key management): guests see a locked state explaining it is admin-only.
- While a request is slow because Render is waking up, show a short "waking up the vault" state rather
  than a spinner with no explanation.

### 6. Configuration

| Where | Variable | Notes |
|---|---|---|
| Vercel (server only) | `VAULT_API_URL` | Render base URL |
| Vercel (server only) | `VAULT_GUEST_READ_KEY`, `VAULT_GUEST_SANDBOX_KEY`, `VAULT_ADMIN_KEY` | raw keys from `POST /keys` |
| Vercel (server only) | `ADMIN_PASSWORD`, `SESSION_SECRET` | `SESSION_SECRET` at least 32 random bytes |
| Render | `VAULT_MASTER_KEY`, `VAULT_BOOTSTRAP_ADMIN_KEY` | from Phase 1 |
| docker-compose | frontend gets `VAULT_API_URL=http://backend:8080` plus dev values | the proxy now calls the backend from inside the Docker network |

### 7. Testing

- Backend, test first: masking per endpoint for a key without `REVEAL_SECRETS` (and plaintext with it),
  binary download 403 without it, admin implies it, placeholder-preserving writes (section 1b), audit
  endpoint scope and IP truncation, filter denials recorded, playground seeding is idempotent, dev keys
  seeded only in dev mode and deactivated otherwise.
- Frontend: add Vitest as a dev dependency for the security logic only: session sign/verify
  (tampered, expired, wrong secret), key selection per role and method, the Origin check, and the
  proxy's path allowlist.
- End to end, run locally: guest browse with masked secrets, blocked write on a showcase app, sandbox
  write and rollback, live SSE update, admin login revealing secrets, key management, logout. Confirm
  that the built JS bundle contains no `sk-` key and that no request URL carries a key.

### 8. Deployment order

1. Render: set `VAULT_MASTER_KEY` and `VAULT_BOOTSTRAP_ADMIN_KEY`. If the master key differs from
   the one that encrypted the current Atlas data, wipe the demo collections so the seeder reseeds.
2. Merge to `main`; Render and Vercel both deploy.
3. With the bootstrap admin key, create the guest-read, guest-sandbox and admin keys via `POST /keys`.
4. Vercel: set the server-only variables, delete the two `NEXT_PUBLIC_VAULT_*` variables, redeploy.
5. Verify: no key in the bundle, guest and admin flows work on the live URLs.

The dashboard is unavailable between steps 2 and 4 (a few minutes).

## Demo moments this enables

- Open the browser's dev tools: no API key anywhere in the bundle or network tab.
- As guest, a payment secret shows `********`; log in as admin and the real value appears.
- As guest, try to roll back `payment-gateway`: the UI blocks it, and a forced request returns 403
  from the vault and appears in the audit log as `SCOPE_DENIED`.
- As guest, create a new `playground` version and watch the audit page update live through SSE.
- Edit a playground setting while secrets are masked: the secrets survive (write-only secrets).
- "Reset playground" rolls back to v1, showing immutable history in action.

## Calls made by the author (review these)

- Audit log made real and in scope (section 1c), including filter-level denials.
- Masked placeholder on write means "keep current value" (section 1b); unknown path is a 400.
- Sandbox reset is a rollback to v1, not a delete; version spam is an accepted demo risk.
- Guests see the audit log with truncated IPs.
- Binary file content counts as a secret (download needs `REVEAL_SECRETS`).
- `maxDuration = 60` plus client retry, so the design works on any Vercel plan.

## Left to the builder

- Exact UI components and styling, within the design-authority rule above.
- Where the masking rule lives in the backend, as long as it is one shared rule.
- File layout of the proxy and session helpers, keeping secrets in server-only code.
- The exact copy of hints and messages.
