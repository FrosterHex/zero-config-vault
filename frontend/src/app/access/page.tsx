"use client";

import React, { useState, useEffect, useCallback } from "react";
import { DashboardShell } from "@/components/dashboard/DashboardShell";
import { AccessibleFade } from "@/components/motion/AccessibleMotion";
import { GlassCard3D } from "@/components/ui/GlassCard3D";
import { vaultApi } from "@/lib/api";
import { useVault } from "@/context/VaultContext";
import { AdminOnlyPanel } from "@/components/auth/AdminOnlyPanel";
import { CreateKeyForm } from "@/components/auth/CreateKeyForm";
import {
  KeyRound,
  ShieldCheck,
  Copy,
  Check,
  RefreshCw,
  AlertCircle,
} from "lucide-react";

interface KeyItem {
  id: string;
  name: string;
  keyPrefix: string;
  allowedApps: string[];
  allowedEnvironments: string[];
  roles: string[];
  active: boolean;
  createdAt: string;
}

export default function AccessPage() {
  const [keys, setKeys] = useState<KeyItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [copiedId, setCopiedId] = useState<string | null>(null);

  const { role } = useVault();
  const isAdmin = role === "admin";
  const [error, setError] = useState<string | null>(null);

  const loadKeys = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setKeys(await vaultApi.listApiKeys());
    } catch (err) {
      setKeys([]);
      setError(err instanceof Error ? err.message : "Could not load API keys");
    } finally {
      setLoading(false);
    }
  }, []);

  // Key management is admin-only: guests never request the key list.
  useEffect(() => {
    if (isAdmin) {
      void loadKeys();
    } else {
      setKeys([]);
      setError(null);
    }
  }, [isAdmin, loadKeys]);

  const handleCopyPrefix = (id: string, text: string) => {
    navigator.clipboard.writeText(text);
    setCopiedId(id);
    setTimeout(() => setCopiedId(null), 2000);
  };

  return (
    <DashboardShell>
      <div className="pointer-events-none fixed inset-0 overflow-hidden -z-10 bg-grid-cyber">
        <div className="absolute top-1/4 right-1/3 h-80 w-80 rounded-full bg-indigo-600/10 blur-[130px]" />
      </div>

      <AccessibleFade delay={0}>
        <div className="flex flex-col sm:flex-row sm:items-end justify-between gap-4 pb-4 border-b border-white/10">
          <div>
            <div className="flex items-center gap-2 mb-1">
              <span className="flex h-2 w-2 rounded-full bg-indigo-400" />
              <span className="text-[11px] font-mono uppercase tracking-widest text-indigo-400 font-semibold">
                Granular Role-Based Access Control (RBAC)
              </span>
            </div>
            <h1 className="font-['Cormorant_Garamond',serif] text-4xl font-bold tracking-tight text-white">
              Scoped API Key Matrix
            </h1>
            <p className="text-xs sm:text-sm text-slate-400 font-['Montserrat',sans-serif] font-light mt-1 max-w-2xl">
              Cryptographically hashed API credentials stored in MongoDB. Restrict tokens by specific microservice
              application boundaries, environments, and administrative roles.
            </p>
          </div>

          {isAdmin && (
            <div className="flex items-center gap-3">
              <button
                onClick={loadKeys}
                title="Reload API keys"
                className="rounded-lg border border-white/10 bg-white/5 p-2 text-slate-400 hover:text-white transition-colors"
              >
                <RefreshCw className={`h-4 w-4 ${loading ? "animate-spin text-indigo-400" : ""}`} />
              </button>
            </div>
          )}
        </div>
      </AccessibleFade>

      {!isAdmin ? (
        <AccessibleFade delay={0.05}>
          <AdminOnlyPanel
            title="Key management is admin-only"
            description="Scoped API keys grant direct access to the vault, so listing, creating and revoking them requires an admin session. Sign in with the admin login in the header to manage keys."
          />
        </AccessibleFade>
      ) : (
        <>
          <AccessibleFade delay={0.05}>
            <CreateKeyForm onCreated={loadKeys} />
          </AccessibleFade>

          {error && (
            <div role="alert" className="rounded-lg border border-rose-500/30 bg-rose-950/40 px-3.5 py-2 text-xs font-mono text-rose-300 flex items-center gap-2">
              <AlertCircle className="h-4 w-4 text-rose-400 shrink-0" />
              <span>{error}</span>
            </div>
          )}

          <AccessibleFade delay={0.08}>
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
              {keys.map((k) => (
                <GlassCard3D key={k.id} glowColor="indigo">
                  <div className="p-6 space-y-4">
                    <div className="flex items-center justify-between">
                      <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-indigo-500/10 text-indigo-400 border border-indigo-500/20">
                        <KeyRound className="h-5 w-5" />
                      </div>
                      <div className="flex items-center gap-1.5">
                        {k.roles.map((r) => (
                          <span
                            key={r}
                            className="rounded px-2 py-0.5 text-[10px] font-mono font-bold uppercase bg-indigo-500/20 text-indigo-400 border border-indigo-500/30"
                          >
                            {r}
                          </span>
                        ))}
                      </div>
                    </div>

                    <div>
                      <h3 className="font-mono text-sm font-bold text-white">
                        {k.name}
                      </h3>
                      <div className="flex items-center gap-2 mt-2">
                        <span className="font-mono text-xs text-slate-400 bg-black/40 px-2 py-0.5 rounded border border-white/5">
                          Prefix: {k.keyPrefix}****
                        </span>
                        <button
                          onClick={() => handleCopyPrefix(k.id, k.keyPrefix)}
                          className="text-slate-400 hover:text-white transition-colors"
                        >
                          {copiedId === k.id ? <Check className="h-3.5 w-3.5 text-emerald-400" /> : <Copy className="h-3.5 w-3.5" />}
                        </button>
                      </div>
                    </div>

                    <div className="rounded-xl bg-black/30 p-3 font-mono text-[11px] space-y-2 border border-white/5">
                      <div>
                        <span className="text-slate-500 block text-[10px] uppercase tracking-wider mb-1">
                          Allowed Applications:
                        </span>
                        <div className="flex flex-wrap gap-1">
                          {k.allowedApps.map((app) => (
                            <span key={app} className="bg-slate-800 text-slate-300 px-1.5 py-0.5 rounded text-[10px]">
                              {app}
                            </span>
                          ))}
                        </div>
                      </div>

                      <div>
                        <span className="text-slate-500 block text-[10px] uppercase tracking-wider mb-1">
                          Allowed Environments:
                        </span>
                        <div className="flex flex-wrap gap-1">
                          {k.allowedEnvironments.map((env) => (
                            <span key={env} className="bg-slate-800 text-emerald-400 px-1.5 py-0.5 rounded text-[10px]">
                              {env}
                            </span>
                          ))}
                        </div>
                      </div>
                    </div>

                    <div className="flex items-center justify-between pt-2 border-t border-white/5 text-[10px] font-mono text-slate-500">
                      <span>Created: {new Date(k.createdAt).toLocaleDateString()}</span>
                      <span className="text-emerald-400 flex items-center gap-1">
                        <ShieldCheck className="h-3 w-3" />
                        Active
                      </span>
                    </div>
                  </div>
                </GlassCard3D>
              ))}
            </div>
          </AccessibleFade>
        </>
      )}
    </DashboardShell>
  );
}
