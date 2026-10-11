/**
 * Zero-Config Vault API Client Library
 * Connects frontend dashboard components directly to Spring Boot backend REST & SSE channels.
 */

const BASE_URL = process.env.NEXT_PUBLIC_VAULT_API_URL || "http://localhost:8080";
const DEFAULT_API_KEY = process.env.NEXT_PUBLIC_VAULT_API_KEY || "sk-dev-key-001";

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

export interface SearchResult {
  applicationName: string;
  environment: string;
  version: number;
  documentHash: string;
  createdAt: string;
  score: number;
  matchingSnippets: Record<string, unknown>;
}

async function request<T>(endpoint: string, options: RequestInit = {}): Promise<T> {
  const headers = new Headers(options.headers || {});
  if (!headers.has("Accept")) {
    headers.set("Accept", "application/json");
  }
  if (!headers.has("X-API-Key")) {
    headers.set("X-API-Key", DEFAULT_API_KEY);
  }
  if (options.body && typeof options.body === "string" && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }

  const response = await fetch(`${BASE_URL}${endpoint}`, {
    ...options,
    headers,
  });

  if (!response.ok) {
    let errorMsg = `HTTP Error ${response.status}: ${response.statusText}`;
    try {
      const errJson = await response.json();
      if (errJson.message) errorMsg = errJson.message;
    } catch {
      // ignore json parse error
    }
    throw new Error(errorMsg);
  }

  return response.json();
}

export const vaultApi = {
  // Configuration Endpoints
  getLatestConfig: (app: string, env: string, masked: boolean = false) =>
    request<ConfigResponse>(`/api/v1/vault/config/${app}/${env}?masked=${masked}`),

  getConfigByVersion: (app: string, env: string, version: number, masked: boolean = false) =>
    request<ConfigResponse>(`/api/v1/vault/config/${app}/${env}/version/${version}?masked=${masked}`),

  listVersions: (app: string, env: string) =>
    request<VersionSummary[]>(`/api/v1/vault/config/${app}/${env}/versions`),

  createConfig: (app: string, env: string, payload: Record<string, unknown>, tags: Record<string, string> = {}) =>
    request<ConfigResponse>(`/api/v1/vault/config`, {
      method: "POST",
      body: JSON.stringify({ applicationName: app, environment: env, payload, tags }),
    }),

  patchConfig: (app: string, env: string, partialPayload: Record<string, unknown>) =>
    request<ConfigResponse>(`/api/v1/vault/config/${app}/${env}`, {
      method: "PATCH",
      body: JSON.stringify(partialPayload),
    }),

  rollbackConfig: (app: string, env: string, targetVersion: number) =>
    request<ConfigResponse>(`/api/v1/vault/config/${app}/${env}/rollback/${targetVersion}`, {
      method: "POST",
    }),

  diffVersions: (app: string, env: string, v1: number, v2: number) =>
    request<DiffResponse>(`/api/v1/vault/config/${app}/${env}/diff?v1=${v1}&v2=${v2}`),

  searchByPath: (app: string, env: string, path: string) =>
    request<{ path: string; value: unknown }>(`/api/v1/vault/config/${app}/${env}/search?path=${encodeURIComponent(path)}`),

  // Export Studio Endpoint
  exportConfig: (app: string, env: string, format: string = "json", version?: number) => {
    const vParam = version ? `&version=${version}` : "";
    return `${BASE_URL}/api/v1/vault/config/${app}/${env}/export?format=${format}${vParam}`;
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
    }>>(`/api/v1/vault/binaries/${app}/${env}`),

  uploadBinary: async (app: string, env: string, file: File, metadata: Record<string, string> = {}) => {
    const formData = new FormData();
    formData.append("file", file);
    Object.entries(metadata).forEach(([k, v]) => formData.append(k, v));
    const headers = new Headers();
    headers.set("X-API-Key", DEFAULT_API_KEY);
    const response = await fetch(`${BASE_URL}/api/v1/vault/binaries/${app}/${env}/upload`, {
      method: "POST",
      headers,
      body: formData,
    });
    if (!response.ok) {
      let errorMsg = `Upload failed: HTTP ${response.status}`;
      try {
        const errJson = await response.json();
        if (errJson.message) errorMsg = errJson.message;
      } catch {
        // ignore
      }
      throw new Error(errorMsg);
    }
    return response.json();
  },

  getBinaryDownloadUrl: (app: string, env: string, filename: string, version?: number) => {
    const vParam = version ? `?version=${version}&apiKey=${DEFAULT_API_KEY}` : `?apiKey=${DEFAULT_API_KEY}`;
    return `${BASE_URL}/api/v1/vault/binaries/${app}/${env}/${filename}${vParam}`;
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
    }>>(`/api/v1/vault/keys`),

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
    }>(`/api/v1/vault/keys`, {
      method: "POST",
      body: JSON.stringify(data),
    }),

  deleteApiKey: (id: string) =>
    request<{ message: string }>(`/api/v1/vault/keys/${id}`, {
      method: "DELETE",
    }),

  // Telemetry Dashboard
  getStats: async () => {
    const raw = await request<StatsResponse>(`/api/v1/vault/stats`);
    return {
      ...raw,
      averageVersionsPerApplication: raw.averageVersionsPerApp ?? raw.averageVersionsPerApplication ?? 0,
      auditLogCount: raw.totalAuditEntries ?? raw.auditLogCount ?? 0,
      cachedConfigCount: raw.cacheDocumentCount ?? raw.cachedConfigCount ?? 0,
    };
  },

  // MongoDB Native Time-Series Telemetry
  getTelemetryStats: (minutes: number = 60) =>
    request<TelemetryStats>(`/api/v1/vault/stats/telemetry?minutes=${minutes}`),

  // MongoDB Engine-Level $jsonSchema Governance
  getSchemaValidation: () =>
    request<SchemaValidation>(`/api/v1/vault/stats/schema`),

  // MongoDB Full-Text and Regex Search
  searchConfigs: (query: string) =>
    request<SearchResult[]>(`/api/v1/vault/config/search?query=${encodeURIComponent(query)}`),

  // Server-Sent Events (SSE) Stream
  subscribeToStream: (
    app: string,
    env: string,
    onMessage: (event: StreamEvent) => void,
    onError?: (error: Event) => void
  ): (() => void) => {
    const url = `${BASE_URL}/api/v1/vault/stream/${app}/${env}?apiKey=${encodeURIComponent(DEFAULT_API_KEY)}`;
    const eventSource = new EventSource(url);

    eventSource.onmessage = (e) => {
      try {
        const parsed = JSON.parse(e.data);
        onMessage(parsed);
      } catch (err) {
        console.warn("Failed to parse SSE payload", err);
      }
    };

    if (onError) {
      eventSource.onerror = onError;
    }

    return () => {
      eventSource.close();
    };
  },
};
