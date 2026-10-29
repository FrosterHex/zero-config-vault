"use client";

import React, { useState, useEffect, useCallback, Suspense } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { DashboardShell } from "@/components/dashboard/DashboardShell";
import { AccessibleFade } from "@/components/motion/AccessibleMotion";
import { GlassCard3D } from "@/components/ui/GlassCard3D";
import { ConfigDataGrid } from "@/components/configs/ConfigDataGrid";
import { JsonTreeEditor } from "@/components/21st/JsonTreeEditor";
import { IntegrityBadge } from "@/components/integrity-badge";
import { SecretToggle } from "@/components/secret-toggle";
import { useVault, Environment } from "@/context/VaultContext";
import { GuestWriteNotice, GUEST_WRITE_HINT } from "@/components/auth/GuestWriteNotice";
import {
  vaultApi,
  ConfigResponse,
  VersionSummary,
} from "@/lib/api";
import {
  Search,
  RefreshCw,
  AlertCircle,
  CheckCircle2,
  RotateCcw,
} from "lucide-react";

const DEFAULT_PAYLOAD: Record<string, unknown> = {
  server: {
    port: 8443,
    timeoutMs: 3000,
    ssl: { enabled: true, protocol: "TLSv1.3" },
  },
  payment: {
    currency: "USD",
    maxRetryAttempts: 5,
    rateLimitPerMinute: 2500,
    gateways: ["stripe", "adyen", "paypal", "apple_pay"],
  },
  database: {
    host: "db-cluster.internal.payment.prod",
    port: 27017,
    name: "prod_payments",
  },
  secure_settings: {
    stripe_api_secret: "sec_live_9981248719283719238",
    webhook_signing_salt: "whsec_7128937128941728934",
  },
  tags: { tier: "tier-1", compliance: "PCI-DSS" },
};

function ConfigsContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const { activeEnv, setActiveEnv, activeApp, setActiveApp, refreshKey, apps, envs, canWrite } = useVault();

  const [selectedApp, setSelectedApp] = useState(() => searchParams.get("app") || activeApp);
  const [selectedEnv, setSelectedEnv] = useState<Environment>(() => (searchParams.get("env") as Environment) || activeEnv);
  const [masked, setMasked] = useState(true);

  const [versions, setVersions] = useState<VersionSummary[]>([]);
  const [currentConfig, setCurrentConfig] = useState<ConfigResponse | null>(null);
  const [activeVersion, setActiveVersion] = useState<number>(1);
  const [editorPayload, setEditorPayload] = useState<Record<string, unknown>>(DEFAULT_PAYLOAD);

  const [loading, setLoading] = useState(false);
  const [statusMessage, setStatusMessage] = useState<{ type: "success" | "error"; text: string } | null>(null);

  // Path Search state
  const [pathQuery, setPathQuery] = useState("");
  const [pathResult, setPathResult] = useState<string | null>(null);

  // Sync with global context if activeApp / activeEnv changed
  useEffect(() => {
    if (activeApp && activeApp !== selectedApp) {
      setSelectedApp(activeApp);
    }
  }, [activeApp]);

  useEffect(() => {
    if (activeEnv && activeEnv !== selectedEnv) {
      setSelectedEnv(activeEnv);
    }
  }, [activeEnv]);

  // Fetch active configuration
  const loadConfig = useCallback(
    async (version?: number, overrideMasked?: boolean) => {
      const isMasked = overrideMasked !== undefined ? overrideMasked : masked;
      setLoading(true);
      try {
        let resp: ConfigResponse;
        if (version) {
          resp = await vaultApi.getConfigByVersion(selectedApp, selectedEnv, version, isMasked);
        } else {
          resp = await vaultApi.getLatestConfig(selectedApp, selectedEnv, isMasked);
        }
        setCurrentConfig(resp);
        setActiveVersion(resp.version);
        setEditorPayload(resp.payload || {});
      } catch (err) {
        console.warn("Could not load config from vault:", err);
      } finally {
        setLoading(false);
      }
    },
    [selectedApp, selectedEnv, masked]
  );

  // Fetch versions list
  const fetchVersions = useCallback(async () => {
    try {
      const list = await vaultApi.listVersions(selectedApp, selectedEnv);
      setVersions(list);
      return list;
    } catch {
      setVersions([]);
      return [];
    }
  }, [selectedApp, selectedEnv]);

  // Load versions and config whenever app, env, or global refresh changes
  useEffect(() => {
    let isMounted = true;
    fetchVersions().then((list) => {
      if (!isMounted) return;
      if (list.length > 0) {
        loadConfig(list[0].version);
      } else {
        // Attempt get latest
        loadConfig();
      }
    });
    return () => {
      isMounted = false;
    };
  }, [selectedApp, selectedEnv, refreshKey]);

  // Handle secret toggle
  const handleToggleMask = (newMasked: boolean) => {
    setMasked(newMasked);
    loadConfig(activeVersion, newMasked);
  };

  // Handle Commit New Revision
  const handleCommitRevision = async (newPayload: Record<string, unknown>) => {
    setLoading(true);
    setStatusMessage(null);
    try {
      const resp = await vaultApi.createConfig(selectedApp, selectedEnv, newPayload, {
        updatedBy: "console-user",
        timestamp: new Date().toISOString(),
      });
      setStatusMessage({
        type: "success",
        text: `Successfully committed Revision v${resp.version} for ${selectedApp} [${selectedEnv}].`,
      });
      await fetchVersions();
      await loadConfig(resp.version);
    } catch (e: unknown) {
      const err = e as Error;
      setStatusMessage({
        type: "error",
        text: `Failed to commit revision: ${err.message}`,
      });
    } finally {
      setLoading(false);
    }
  };

  // Handle Rollback
  const handleRollback = async (targetVersion: number) => {
    setLoading(true);
    setStatusMessage(null);
    try {
      const resp = await vaultApi.rollbackConfig(selectedApp, selectedEnv, targetVersion);
      setStatusMessage({
        type: "success",
        text: `Successfully rolled back to state of v${targetVersion}. Generated new revision v${resp.version}.`,
      });
      await fetchVersions();
      await loadConfig(resp.version);
    } catch (e: unknown) {
      const err = e as Error;
      setStatusMessage({
        type: "error",
        text: `Rollback failed: ${err.message}`,
      });
    } finally {
      setLoading(false);
    }
  };

  const writable = canWrite(selectedApp);

  const switchToPlayground = () => {
    setSelectedApp("playground");
    setActiveApp("playground");
    setSelectedEnv("development");
    setActiveEnv("development");
  };

  const handleResetPlayground = () => {
    if (confirm(`Reset playground [${selectedEnv}] to v1? This creates a new revision with v1's state.`)) {
      void handleRollback(1);
    }
  };

  // Handle Dot-Notation Path Search
  const handlePathSearch = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!pathQuery.trim()) return;

    try {
      const res = await vaultApi.searchByPath(selectedApp, selectedEnv, pathQuery.trim());
      setPathResult(JSON.stringify(res.value));
    } catch {
      // Local fallback lookup in current payload
      const keys = pathQuery.trim().split(".");
      let val: unknown = editorPayload;
      for (const k of keys) {
        if (val && typeof val === "object" && k in (val as Record<string, unknown>)) {
          val = (val as Record<string, unknown>)[k];
        } else {
          val = undefined;
          break;
        }
      }
      setPathResult(val !== undefined ? JSON.stringify(val) : "Path not found (404)");
    }
  };

  return (
    <DashboardShell>
      {/* 3D Cyber Atmosphere Background Elements */}
      <div className="pointer-events-none fixed inset-0 overflow-hidden -z-10 bg-grid-cyber">
        <div className="absolute top-1/4 left-1/4 h-80 w-80 rounded-full bg-indigo-600/15 blur-[120px]" />
        <div className="absolute top-1/3 right-1/4 h-80 w-80 rounded-full bg-purple-600/10 blur-[130px]" />
      </div>

      {/* Editorial Header */}
      <AccessibleFade delay={0}>
        <div className="flex flex-col lg:flex-row lg:items-end justify-between gap-4 pb-4 border-b border-white/10">
          <div>
            <div className="flex items-center gap-2 mb-1">
              <span className="flex h-2 w-2 rounded-full bg-indigo-400" />
              <span className="text-[11px] font-mono uppercase tracking-widest text-indigo-400 font-semibold">
                Dynamic Configuration Console
              </span>
            </div>
            <h1 className="font-['Cormorant_Garamond',serif] text-4xl md:text-5xl font-bold tracking-tight text-white leading-tight">
              Configuration Ledger & Time Machine
            </h1>
            <p className="text-xs md:text-sm text-slate-400 max-w-2xl font-['Montserrat',sans-serif] font-light mt-1">
              Interactive JSON tree viewer with automated envelope encryption, live secret masking,
              immutable temporal version history, and zero-downtime rollback controls.
            </p>
          </div>

          {/* App & Env Selectors */}
          <div className="flex flex-wrap items-center gap-2">
            {/* App Dropdown */}
            <div className="flex items-center gap-1.5 rounded-lg border border-white/10 bg-slate-900/80 px-2.5 py-1 text-xs">
              <span className="text-slate-400 font-mono text-[11px]">App:</span>
              <select
                value={selectedApp}
                onChange={(e) => {
                  setSelectedApp(e.target.value);
                  setActiveApp(e.target.value);
                }}
                className="bg-transparent font-mono text-white text-xs font-semibold focus:outline-none cursor-pointer"
              >
                {apps.map((app) => (
                  <option key={app} value={app} className="bg-slate-900 text-white">
                    {app}
                  </option>
                ))}
              </select>
            </div>

            {/* Env Dropdown */}
            <div className="flex items-center gap-1.5 rounded-lg border border-white/10 bg-slate-900/80 px-2.5 py-1 text-xs">
              <span className="text-slate-400 font-mono text-[11px]">Env:</span>
              <select
                value={selectedEnv}
                onChange={(e) => {
                  const env = e.target.value as Environment;
                  setSelectedEnv(env);
                  setActiveEnv(env);
                }}
                className="bg-transparent font-mono text-white text-xs font-semibold focus:outline-none cursor-pointer"
              >
                {envs.map((env) => (
                  <option key={env} value={env} className="bg-slate-900 text-white">
                    {env}
                  </option>
                ))}
              </select>
            </div>

            {selectedApp === "playground" && (
              <button
                type="button"
                onClick={handleResetPlayground}
                disabled={loading}
                title="Roll the playground back to its seeded v1 state"
                className="inline-flex items-center gap-1.5 rounded-lg border border-amber-500/30 bg-amber-500/10 px-2.5 py-1.5 text-xs font-mono text-amber-200 hover:bg-amber-500/20 hover:text-white transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
              >
                <RotateCcw className="h-3.5 w-3.5" aria-hidden="true" />
                <span>Reset playground</span>
              </button>
            )}

            {/* Reload Button */}
            <button
              type="button"
              onClick={() => loadConfig(activeVersion)}
              title="Refresh configuration from server"
              className="rounded-lg border border-white/10 bg-white/5 p-2 text-slate-400 hover:text-white hover:bg-white/10 transition-colors"
            >
              <RefreshCw className={`h-3.5 w-3.5 ${loading ? "animate-spin text-indigo-400" : ""}`} />
            </button>
          </div>
        </div>
      </AccessibleFade>

      {/* Control Banner: Hash, Mask Toggle, Dot-Notation Search */}
      <AccessibleFade delay={0.05}>
        {!writable && (
          <div className="mb-2">
            <GuestWriteNotice onSwitchToPlayground={switchToPlayground} />
          </div>
        )}
        <div className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-white/10 bg-slate-950/40 p-3.5">
          <div className="flex flex-wrap items-center gap-3">
            {/* Active Version Badge */}
            <div className="inline-flex items-center gap-1.5 rounded-lg border border-indigo-500/30 bg-indigo-950/20 px-2.5 py-1 text-xs font-mono">
              <span className="text-indigo-400 font-bold">Revision v{activeVersion}</span>
              <span className="text-slate-500">•</span>
              <span className="text-slate-400 text-[11px]">
                {currentConfig ? new Date(currentConfig.createdAt).toLocaleTimeString() : "Live"}
              </span>
            </div>

            {/* Integrity Badge */}
            {currentConfig && <IntegrityBadge hash={currentConfig.documentHash} />}

            {/* Secret Masking Toggle (Masked vs Decrypted Plaintext) */}
            <SecretToggle masked={masked} onToggle={handleToggleMask} />
          </div>

          {/* Dot-Notation Path Query Form */}
          <form onSubmit={handlePathSearch} className="flex items-center gap-2">
            <div className="relative">
              <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-slate-400" />
              <input
                type="text"
                placeholder="Query path: e.g. database.host"
                value={pathQuery}
                onChange={(e) => setPathQuery(e.target.value)}
                className="h-8 w-52 sm:w-64 rounded-lg border border-white/10 bg-black/40 pl-8 pr-3 text-xs text-white placeholder-slate-500 font-mono focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500"
              />
            </div>
            <button
              type="submit"
              className="h-8 rounded-lg border border-white/10 bg-white/5 px-2.5 text-xs font-mono text-slate-300 hover:bg-white/10 hover:text-white transition-colors"
            >
              Lookup
            </button>
          </form>
        </div>

        {/* Path Search Result Banner */}
        {pathResult && (
          <div className="mt-2 rounded-lg border border-indigo-500/30 bg-indigo-950/40 px-3.5 py-2 text-xs font-mono text-indigo-200 flex items-center justify-between">
            <div className="flex items-center gap-2">
              <span className="text-indigo-400 font-semibold">{pathQuery}:</span>
              <span className="text-white font-bold">{pathResult}</span>
            </div>
            <button
              type="button"
              onClick={() => setPathResult(null)}
              className="text-slate-400 hover:text-white text-[11px]"
            >
              Clear
            </button>
          </div>
        )}

        {/* Status Notification */}
        {statusMessage && (
          <div
            className={`mt-2 rounded-lg border px-3.5 py-2 text-xs font-mono flex items-center gap-2 ${
              statusMessage.type === "success"
                ? "border-emerald-500/30 bg-emerald-950/40 text-emerald-300"
                : "border-rose-500/30 bg-rose-950/40 text-rose-300"
            }`}
          >
            {statusMessage.type === "success" ? (
              <CheckCircle2 className="h-4 w-4 text-emerald-400 shrink-0" />
            ) : (
              <AlertCircle className="h-4 w-4 text-rose-400 shrink-0" />
            )}
            <span>{statusMessage.text}</span>
          </div>
        )}
      </AccessibleFade>

      {/* 3D Glassmorphic Responsive Split Console Layout */}
      <AccessibleFade delay={0.1}>
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 min-h-[580px]">
          {/* Left Panel: Version History Data Grid with 3D Tilt */}
          <div className="lg:col-span-5 h-[580px]">
            <GlassCard3D glowColor="indigo" className="h-full">
              <ConfigDataGrid
                versions={versions}
                activeVersion={activeVersion}
                onSelectVersion={(v) => loadConfig(v)}
                onRollback={handleRollback}
                rollbackDisabledReason={writable ? undefined : GUEST_WRITE_HINT}
                onDiffWithLatest={(v) => {
                  router.push(`/diff?app=${selectedApp}&env=${selectedEnv}&v1=${v}&v2=${versions[0]?.version || 1}`);
                }}
              />
            </GlassCard3D>
          </div>

          {/* Right Panel: Interactive JSON Tree & Code Editor with 3D Tilt */}
          <div className="lg:col-span-7 h-[580px]">
            <GlassCard3D glowColor="purple" className="h-full">
              <JsonTreeEditor
                initialValue={editorPayload}
                onSave={handleCommitRevision}
                saveDisabledReason={writable ? undefined : GUEST_WRITE_HINT}
              />
            </GlassCard3D>
          </div>
        </div>
      </AccessibleFade>
    </DashboardShell>
  );
}

export default function ConfigsPage() {
  return (
    <Suspense fallback={<div className="p-8 text-center text-slate-400 font-mono">Loading Config Console...</div>}>
      <ConfigsContent />
    </Suspense>
  );
}
