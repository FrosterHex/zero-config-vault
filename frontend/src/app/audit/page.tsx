"use client";

import React, { useState, useEffect, useCallback } from "react";
import { DashboardShell } from "@/components/dashboard/DashboardShell";
import { AccessibleFade } from "@/components/motion/AccessibleMotion";
import { GlassCard3D } from "@/components/ui/GlassCard3D";
import { useVault, Environment } from "@/context/VaultContext";
import { vaultApi, AuditEntry } from "@/lib/api";
import {
  Clock,
  Activity,
  KeyRound,
  ShieldAlert,
  Globe,
  AlertCircle,
  RefreshCw,
} from "lucide-react";

const AUDIT_LIMIT = 50;
const DENIED_ACTIONS = new Set(["AUTH_DENIED", "SCOPE_DENIED", "SCOPE_VIOLATION"]);

type StreamState = "connecting" | "live" | "reconnecting";

export default function AuditPage() {
  const { role, activeApp, activeEnv, apps, envs, refreshKey } = useVault();

  // Guests follow the writable sandbox by default; admins follow the globally selected scope.
  const [scope, setScope] = useState<{ app: string; env: Environment } | null>(null);
  const streamApp = scope?.app ?? (role === "admin" ? activeApp : "playground");
  const streamEnv = scope?.env ?? (role === "admin" ? activeEnv : "development");

  const [entries, setEntries] = useState<AuditEntry[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [streamState, setStreamState] = useState<StreamState>("connecting");

  const loadAudit = useCallback(async () => {
    setLoading(true);
    try {
      setEntries(await vaultApi.getAuditLog({ limit: AUDIT_LIMIT }));
      setError(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not load the audit log");
    } finally {
      setLoading(false);
    }
  }, []);

  // Initial load, global refresh, and a reload when the role changes (admins see unmasked IPs).
  useEffect(() => {
    void loadAudit();
  }, [loadAudit, refreshKey, role]);

  // Reload on every change event and on each (re)connect, so nothing is missed while disconnected.
  useEffect(() => {
    setStreamState("connecting");
    const unsubscribe = vaultApi.subscribeToStream(
      streamApp,
      streamEnv,
      () => void loadAudit(),
      () => setStreamState("reconnecting"),
      () => {
        setStreamState("live");
        void loadAudit();
      }
    );
    return unsubscribe;
  }, [streamApp, streamEnv, loadAudit]);

  const streamLabel =
    streamState === "live" ? "Streaming live" : streamState === "connecting" ? "Connecting…" : "Reconnecting…";

  return (
    <DashboardShell>
      <div className="pointer-events-none fixed inset-0 overflow-hidden -z-10 bg-grid-cyber">
        <div className="absolute top-1/3 left-1/4 h-80 w-80 rounded-full bg-cyan-600/10 blur-[130px]" />
      </div>

      <AccessibleFade delay={0}>
        <div className="flex flex-col sm:flex-row sm:items-end justify-between gap-4 pb-4 border-b border-white/10">
          <div>
            <div className="flex items-center gap-2 mb-1">
              <span className="flex h-2 w-2 rounded-full bg-cyan-400 animate-pulse" />
              <span className="text-[11px] font-mono uppercase tracking-widest text-cyan-400 font-semibold">
                MongoDB Capped Collection • 50MB Ring Buffer
              </span>
            </div>
            <h1 className="font-['Cormorant_Garamond',serif] text-4xl font-bold tracking-tight text-white">
              Audit Log
            </h1>
            <p className="text-xs sm:text-sm text-slate-400 font-['Montserrat',sans-serif] font-light mt-1 max-w-2xl">
              Recent operations from an append-only capped collection: entries cannot be deleted one by one, and the
              oldest are discarded once it reaches 50 MB. Secret values are masked before an entry is written.
            </p>
          </div>

          <div className="flex flex-wrap items-center gap-2">
            <div className="flex items-center gap-1.5 rounded-lg border border-white/10 bg-slate-900/80 px-2.5 py-1 text-xs">
              <label htmlFor="audit-stream-app" className="text-slate-400 font-mono text-[11px]">Stream:</label>
              <select
                id="audit-stream-app"
                value={streamApp}
                onChange={(e) => setScope({ app: e.target.value, env: streamEnv })}
                className="bg-transparent font-mono text-white text-xs font-semibold focus:outline-none cursor-pointer"
              >
                {apps.map((a) => (
                  <option key={a} value={a} className="bg-slate-900 text-white">
                    {a}
                  </option>
                ))}
              </select>
              <span className="text-slate-600">/</span>
              <select
                aria-label="Stream environment"
                value={streamEnv}
                onChange={(e) => setScope({ app: streamApp, env: e.target.value as Environment })}
                className="bg-transparent font-mono text-cyan-400 text-xs font-semibold focus:outline-none cursor-pointer"
              >
                {envs.map((env) => (
                  <option key={env} value={env} className="bg-slate-900 text-white">
                    {env}
                  </option>
                ))}
              </select>
            </div>

            <span
              role="status"
              className={`rounded-lg border px-3 py-1.5 text-xs font-mono flex items-center gap-1.5 ${
                streamState === "live"
                  ? "border-cyan-500/30 bg-cyan-500/10 text-cyan-400"
                  : "border-amber-500/30 bg-amber-500/10 text-amber-300"
              }`}
            >
              <Activity className={`h-3.5 w-3.5 ${streamState === "live" ? "animate-pulse" : ""}`} aria-hidden="true" />
              <span>{streamLabel}</span>
            </span>

            <button
              type="button"
              onClick={() => void loadAudit()}
              title="Reload audit log"
              className="rounded-lg border border-white/10 bg-white/5 p-2 text-slate-400 hover:text-white transition-colors"
            >
              <RefreshCw className={`h-4 w-4 ${loading ? "animate-spin text-cyan-400" : ""}`} />
            </button>
          </div>
        </div>
      </AccessibleFade>

      {error && (
        <div role="alert" className="rounded-lg border border-rose-500/30 bg-rose-950/40 px-3.5 py-2 text-xs font-mono text-rose-300 flex items-center gap-2">
          <AlertCircle className="h-4 w-4 text-rose-400 shrink-0" />
          <span>{error}</span>
        </div>
      )}

      <AccessibleFade delay={0.08}>
        <div className="space-y-3">
          {entries.length === 0 && !loading && !error && (
            <p className="py-10 text-center text-xs font-mono text-slate-500">No audit entries yet.</p>
          )}
          {entries.map((entry) => {
            const denied = DENIED_ACTIONS.has(entry.action);
            const failed = !entry.success;
            return (
              <GlassCard3D key={entry.id} glowColor="cyan">
                <div
                  className={`p-4 sm:p-5 flex flex-col md:flex-row md:items-center justify-between gap-4 ${
                    denied ? "border-l-2 border-rose-500/70 bg-rose-950/20" : ""
                  }`}
                >
                  <div className="space-y-1.5 min-w-0">
                    <div className="flex flex-wrap items-center gap-2">
                      <span
                        className={`inline-flex items-center gap-1 font-mono text-xs font-bold px-2 py-0.5 rounded border ${
                          denied
                            ? "bg-rose-500/20 text-rose-300 border-rose-500/40"
                            : failed
                            ? "bg-amber-500/15 text-amber-300 border-amber-500/30"
                            : "bg-cyan-500/20 text-cyan-400 border-cyan-500/30"
                        }`}
                      >
                        {denied && <ShieldAlert className="h-3 w-3" aria-hidden="true" />}
                        {entry.action}
                      </span>
                      {denied && (
                        <span className="rounded bg-rose-500/10 px-1.5 py-0.5 text-[10px] font-mono font-semibold uppercase tracking-wider text-rose-300">
                          Request refused
                        </span>
                      )}
                      <span className="font-mono text-xs font-semibold text-white">{entry.applicationName}</span>
                      <span className="text-slate-500 text-xs font-mono">•</span>
                      <span className="font-mono text-xs text-indigo-400">{entry.environment}</span>
                    </div>
                    {entry.failureReason && (
                      <p className={`font-mono text-xs break-words ${failed ? "text-rose-200/90" : "text-slate-300"}`}>
                        {entry.failureReason}
                      </p>
                    )}
                  </div>

                  <div className="flex flex-wrap items-center gap-3 text-[11px] font-mono text-slate-400 shrink-0">
                    {entry.apiKey && (
                      <div className="flex items-center gap-1.5 bg-black/40 px-2.5 py-1 rounded border border-white/5">
                        <KeyRound className="h-3 w-3 text-indigo-400" aria-hidden="true" />
                        <span>{entry.apiKey}</span>
                      </div>
                    )}
                    {entry.requestingIp && (
                      <div className="flex items-center gap-1 text-slate-500">
                        <Globe className="h-3 w-3" aria-hidden="true" />
                        <span>{entry.requestingIp}</span>
                      </div>
                    )}
                    <div className="flex items-center gap-1 text-slate-500">
                      <Clock className="h-3 w-3" aria-hidden="true" />
                      <time dateTime={entry.timestamp}>{new Date(entry.timestamp).toLocaleString()}</time>
                    </div>
                  </div>
                </div>
              </GlassCard3D>
            );
          })}
        </div>
      </AccessibleFade>
    </DashboardShell>
  );
}
