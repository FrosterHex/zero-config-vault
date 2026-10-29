/**
 * Zero-Config Vault API Client Library
 * Talks to the same-origin /api/vault proxy, which attaches credentials server-side.
 * This module never handles an API key.
 */

const BASE_URL = "/api/vault";

export type Role = "guest" | "admin";

export interface ConfigResponse {
  id: string;
  applicationName: string;
  environment: string;
  version: number;
  documentHash: string;
  payload: Record<string, unknown>;
  tags: Record<string, string>;
  createdAt: string;
}

export interface VersionSummary {
  version: number;
  documentHash: string;
  tags: Record<string, string>;
  createdAt: string;
}

export interface DiffResponse {
  added: Record<string, unknown>;
  removed: Record<string, unknown>;
  modified: Record<string, { oldValue: unknown; newValue: unknown }>;
  unchangedCount: number;
}

export interface StatsResponse {
  totalApplications: number;
  totalEnvironments: number;
  totalVersions: number;
  averageVersionsPerApp?: number;
  averageVersionsPerApplication?: number;
  totalAuditEntries?: number;
  auditLogCount?: number;
  cacheDocumentCount?: number;
  cachedConfigCount?: number;
  mostRecentApp?: string;
  mostRecentEnv?: string;
  activeEnvironments?: string[];
  versionCounts?: Record<string, number>;
  storageSizeDistributions?: Record<string, unknown>;
  mostRecentlyUpdated?: {
    applicationName: string;
    environment: string;
    version: number;
    updatedAt: string;
  } | null;
}

export interface StreamEvent {
  action: string;
  applicationName: string;
  environment: string;
  version: number;
  documentHash: string;
  timestamp: string;
}

export interface TelemetryStats {
  totalRequests: number;
  avgLatencyMs: number;
  p95LatencyMs: number;
  cacheHitRatio: number;
  cacheHits: number;
  cacheMisses: number;
  operations: Record<string, number>;
  timeline: Array<{
    timeLabel: string;
    requestCount: number;
    avgLatency: number;
    cacheHits: number;
  }>;
}

export interface SchemaValidation {
  targetCollection: string;
  validationEnabled: boolean;
  validationLevel: string;
  validationAction: string;
  requiredFields: string[];
  fieldRules: Record<string, { type?: string; pattern?: string; enum?: string[]; rule?: string }>;
  rawJsonSchema: string;
}

export interface AuditEntry {
  id: string;
  action: string;
  applicationName: string;
  environment: string;
  requestingIp: string;
  apiKey: string;
  success: boolean;
  failureReason: string | null;
  timestamp: string;
}

export interface SearchResult {
  applicationName: string;
  environment: string;
  version: number;
  documentHash: string;
  createdAt: string;
  score: number;
  matchingSnippets: Record<string, unknown>;
}

const RETRY_STATUSES = new Set([502, 503, 504]);
const INITIAL_BACKOFF_MS = 1_000;
const MAX_BACKOFF_MS = 8_000;
const WAKE_TIMEOUT_MS = 90_000;

const wakingListeners = new Set<(waking: boolean) => void>();
let wakingCount = 0;

function setWakingDelta(delta: 1 | -1) {
  const wasWaking = wakingCount > 0;
  wakingCount += delta;
  const isWaking = wakingCount > 0;
  if (wasWaking !== isWaking) wakingListeners.forEach((listener) => listener(isWaking));
}

/** Subscribes to "vault is waking up" changes. Returns an unsubscribe function. */
export function onWakingChange(listener: (waking: boolean) => void): () => void {
  wakingListeners.add(listener);
  return () => {
    wakingListeners.delete(listener);
  };
}

const sleep = (ms: number) => new Promise<void>((resolve) => setTimeout(resolve, ms));

/**
 * fetch with a wake-up retry for idempotent reads. The hosted vault can be asleep, so GET/HEAD
 * retry on 502/503/504 or a network error with exponential backoff. Other methods never retry,
 * since a retry could create a duplicate version.
 */
async function send(endpoint: string, options: RequestInit = {}): Promise<Response> {
  const method = (options.method || "GET").toUpperCase();
  const retryable = method === "GET" || method === "HEAD";
  const startedAt = Date.now();
  let delay = INITIAL_BACKOFF_MS;
  let waking = false;

  try {
    for (;;) {
      let response: Response | null = null;
      let networkError: unknown = null;
      try {
        response = await fetch(`${BASE_URL}${endpoint}`, options);
      } catch (err) {
        networkError = err;
      }

      if (response && !RETRY_STATUSES.has(response.status)) return response;
      if (!retryable) {
        if (response) return response;
        throw networkError;
      }

      if (Date.now() - startedAt + delay > WAKE_TIMEOUT_MS) {
        throw new Error("The vault is still waking up. Please try again in a minute.");
      }
      if (!waking) {
        waking = true;
        setWakingDelta(1);
      }
      await sleep(delay);
      delay = Math.min(delay * 2, MAX_BACKOFF_MS);
    }
  } finally {
    if (waking) setWakingDelta(-1);
  }
}

async function errorMessage(response: Response, fallback: string): Promise<string> {
  try {
    const errJson = await response.json();
    if (errJson.message) return errJson.message;
  } catch {
    // ignore json parse error
  }
  return fallback;
}

async function request<T>(endpoint: string, options: RequestInit = {}): Promise<T> {
  const headers = new Headers(options.headers || {});
  if (!headers.has("Accept")) {
    headers.set("Accept", "application/json");
  }
  if (options.body && typeof options.body === "string" && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }

  const response = await send(endpoint, { ...options, headers });
  if (!response.ok) {
    throw new Error(await errorMessage(response, `HTTP Error ${response.status}: ${response.statusText}`));
  }
  return response.json();
}

async function authRequest(path: string, init: RequestInit): Promise<Response> {
  const response = await fetch(`/api/auth/${path}`, init);
  if (!response.ok) {
    throw new Error(await errorMessage(response, `HTTP Error ${response.status}`));
  }
  return response;
}

export const authApi = {
  login: async (password: string): Promise<void> => {
    await authRequest("login", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ password }),
    });
  },

  logout: async (): Promise<void> => {
    await authRequest("logout", { method: "POST" });
  },

  session: async (): Promise<Role> => {
    const response = await authRequest("session", { headers: { Accept: "application/json" } });
    const body = await response.json();
    return body?.role === "admin" ? "admin" : "guest";
  },
};

const STREAM_RETRY_MIN_MS = 1000;
const STREAM_RETRY_MAX_MS = 8000;

export const vaultApi = {
  // Configuration Endpoints
  getLatestConfig: (app: string, env: string, masked: boolean = false) =>
    request<ConfigResponse>(`/config/${app}/${env}?masked=${masked}`),

  getConfigByVersion: (app: string, env: string, version: number, masked: boolean = false) =>
    request<ConfigResponse>(`/config/${app}/${env}/version/${version}?masked=${masked}`),

  listVersions: (app: string, env: string) =>
    request<VersionSummary[]>(`/config/${app}/${env}/versions`),

  createConfig: (app: string, env: string, payload: Record<string, unknown>, tags: Record<string, string> = {}) =>
    request<ConfigResponse>(`/config`, {
      method: "POST",
      body: JSON.stringify({ applicationName: app, environment: env, payload, tags }),
    }),

  patchConfig: (app: string, env: string, partialPayload: Record<string, unknown>) =>
    request<ConfigResponse>(`/config/${app}/${env}`, {
      method: "PATCH",
      body: JSON.stringify(partialPayload),
    }),

  rollbackConfig: (app: string, env: string, targetVersion: number) =>
    request<ConfigResponse>(`/config/${app}/${env}/rollback/${targetVersion}`, {
      method: "POST",
    }),

  diffVersions: (app: string, env: string, v1: number, v2: number) =>
    request<DiffResponse>(`/config/${app}/${env}/diff?v1=${v1}&v2=${v2}`),

  searchByPath: (app: string, env: string, path: string) =>
    request<{ path: string; value: unknown }>(`/config/${app}/${env}/search?path=${encodeURIComponent(path)}`),

  // Export Studio Endpoint
  exportConfig: async (app: string, env: string, format: string = "json", version?: number): Promise<string> => {
    const vParam = version ? `&version=${version}` : "";
    const response = await send(`/config/${app}/${env}/export?format=${format}${vParam}`);
    if (!response.ok) {
      throw new Error(await errorMessage(response, `Export failed: HTTP ${response.status}`));
    }
    return response.text();
  },

  // GridFS Binary Blob Endpoints
  listBinaries: (app: string, env: string) =>
    request<Array<{
      id: string;
      applicationName: string;
      environment: string;
      filename: string;
      contentType: string;
      version: number;
      gridFsFileId: string;
      fileSizeBytes: number;
      documentHash: string;
      createdAt: string;
    }>>(`/binaries/${app}/${env}`),

  uploadBinary: async (app: string, env: string, file: File, metadata: Record<string, string> = {}) => {
    const formData = new FormData();
    formData.append("file", file);
    Object.entries(metadata).forEach(([k, v]) => formData.append(k, v));
    const response = await send(`/binaries/${app}/${env}/upload`, { method: "POST", body: formData });
    if (!response.ok) {
      throw new Error(await errorMessage(response, `Upload failed: HTTP ${response.status}`));
    }
    return response.json();
  },

  getBinaryDownloadUrl: (app: string, env: string, filename: string, version?: number) => {
    const vParam = version ? `?version=${version}` : "";
    return `${BASE_URL}/binaries/${app}/${env}/${filename}${vParam}`;
  },

  // Scoped API Key Endpoints
  listApiKeys: () =>
    request<Array<{
      id: string;
      name: string;
      keyPrefix: string;
      allowedApps: string[];
      allowedEnvironments: string[];
      roles: string[];
      active: boolean;
      createdAt: string;
      lastUsedAt?: string;
    }>>(`/keys`),

  createApiKey: (data: {
    name: string;
    allowedApps: string[];
    allowedEnvironments: string[];
    roles: string[];
    expiresInDays?: number;
  }) =>
    request<{
      id: string;
      name: string;
      rawKey: string;
      keyPrefix: string;
      allowedApps: string[];
      allowedEnvironments: string[];
      roles: string[];
    }>(`/keys`, {
      method: "POST",
      body: JSON.stringify(data),
    }),

  deleteApiKey: (id: string) =>
    request<{ message: string }>(`/keys/${id}`, {
      method: "DELETE",
    }),

  // Telemetry Dashboard
  getStats: async () => {
    const raw = await request<StatsResponse>(`/stats`);
    return {
      ...raw,
      averageVersionsPerApplication: raw.averageVersionsPerApp ?? raw.averageVersionsPerApplication ?? 0,
      auditLogCount: raw.totalAuditEntries ?? raw.auditLogCount ?? 0,
      cachedConfigCount: raw.cacheDocumentCount ?? raw.cachedConfigCount ?? 0,
    };
  },

  // MongoDB Native Time-Series Telemetry
  getTelemetryStats: (minutes: number = 60) =>
    request<TelemetryStats>(`/stats/telemetry?minutes=${minutes}`),

  // MongoDB Engine-Level $jsonSchema Governance
  getSchemaValidation: () =>
    request<SchemaValidation>(`/stats/schema`),

  // MongoDB Full-Text and Regex Search
  searchConfigs: (query: string) =>
    request<SearchResult[]>(`/config/search?query=${encodeURIComponent(query)}`),

  // Audit log, newest first, limited server-side to the caller's scope
  getAuditLog: (params: { limit?: number; app?: string; env?: string } = {}) => {
    const query = new URLSearchParams();
    if (params.limit !== undefined) query.set("limit", String(params.limit));
    if (params.app) query.set("app", params.app);
    if (params.env) query.set("env", params.env);
    const qs = query.toString();
    return request<AuditEntry[]>(`/audit${qs ? `?${qs}` : ""}`);
  },

  // Server-Sent Events (SSE) Stream
  subscribeToStream: (
    app: string,
    env: string,
    onMessage: (event: StreamEvent) => void,
    onError?: (error: Event) => void,
    onOpen?: () => void
  ): (() => void) => {
    let eventSource: EventSource | undefined;
    let timer: ReturnType<typeof setTimeout> | undefined;
    let delay = STREAM_RETRY_MIN_MS;
    let stopped = false;

    const handle = (e: MessageEvent) => {
      try {
        const parsed = JSON.parse(e.data);
        onMessage(parsed);
      } catch (err) {
        console.warn("Failed to parse SSE payload", err);
      }
    };

    const connect = () => {
      const es = new EventSource(`${BASE_URL}/stream/${app}/${env}`);
      eventSource = es;

      // Fires on every (re)connect, so callers can resync after a dropped stream.
      es.onopen = () => {
        delay = STREAM_RETRY_MIN_MS;
        onOpen?.();
      };
      // The vault names its events (CONFIG_CHANGE), and named events never reach onmessage.
      es.onmessage = handle;
      es.addEventListener("CONFIG_CHANGE", handle as EventListener);
      es.onerror = (err) => {
        onError?.(err);
        // A non-200 response (proxy 502/504 during a cold start) closes the stream for good,
        // so the browser will not retry by itself; reconnect with backoff.
        if (!stopped && es.readyState === EventSource.CLOSED) {
          const wait = delay;
          delay = Math.min(delay * 2, STREAM_RETRY_MAX_MS);
          timer = setTimeout(connect, wait);
        }
      };
    };

    connect();

    return () => {
      stopped = true;
      if (timer !== undefined) clearTimeout(timer);
      eventSource?.close();
    };
  },
};
