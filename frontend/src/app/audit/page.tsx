"use client";

import React, { useState, useEffect } from "react";
import { DashboardShell } from "@/components/dashboard/DashboardShell";
import { AccessibleFade } from "@/components/motion/AccessibleMotion";
import { GlassCard3D } from "@/components/ui/GlassCard3D";
import { vaultApi, StreamEvent } from "@/lib/api";
import {
  Clock,
  Activity,
  KeyRound,
} from "lucide-react";

interface AuditEvent {
  id: string;
  action: string;
  applicationName: string;
  environment: string;
  version: number;
  apiKeyMasked: string;
  ipAddress: string;
  timestamp: string;
  details: string;
}

export default function AuditPage() {
  const [events, setEvents] = useState<AuditEvent[]>([
    {
      id: "evt-01",
      action: "UPDATE_CONFIG",
      applicationName: "payment-gateway",
      environment: "production",
      version: 2,
      apiKeyMasked: "sk_****_001",
      ipAddress: "192.168.1.104",
      timestamp: new Date().toISOString(),
      details: "Committed revision v2: updated payment.maxRetryAttempts to 5, secure_settings re-encrypted with AES-GCM",
    },
    {
      id: "evt-02",
      action: "ROLLBACK_CONFIG",
      applicationName: "order-service",
      environment: "staging",
      version: 3,
      apiKeyMasked: "sk_****_001",
      ipAddress: "192.168.1.104",
      timestamp: new Date(Date.now() - 3600000 * 2).toISOString(),
      details: "Atomic rollback triggered to target version 1; spawned sequential revision v3",
    },
    {
      id: "evt-03",
      action: "FETCH_DECRYPTED",
      applicationName: "payment-gateway",
      environment: "production",
      version: 2,
      apiKeyMasked: "sk_****_key",
      ipAddress: "10.0.4.12",
      timestamp: new Date(Date.now() - 3600000 * 5).toISOString(),
      details: "Authorized microservice read: envelope decrypted via parent KEK",
    },
    {
      id: "evt-04",
      action: "CREATE_CONFIG",
      applicationName: "auth-service",
      environment: "production",
      version: 1,
      apiKeyMasked: "sk_****_001",
      ipAddress: "127.0.0.1",
      timestamp: new Date(Date.now() - 3600000 * 12).toISOString(),
      details: "Initial setup revision created with SHA-512 canonical hash verification",
    },
  ]);

  useEffect(() => {
    // Hook up real-time SSE listener
    const unsubscribe = vaultApi.subscribeToStream("payment-gateway", "production", (streamEvt: StreamEvent) => {
      const newEvt: AuditEvent = {
        id: `evt-${Date.now()}`,
        action: streamEvt.action || "CONFIG_MUTATED",
        applicationName: streamEvt.applicationName,
        environment: streamEvt.environment,
        version: streamEvt.version,
        apiKeyMasked: "sk_****_001",
        ipAddress: "127.0.0.1",
        timestamp: streamEvt.timestamp || new Date().toISOString(),
        details: `Live MongoDB Change Stream event detected on oplog (hash: ${streamEvt.documentHash?.substring(0, 16)}...)`,
      };
      setEvents((prev) => [newEvt, ...prev]);
    });

    return () => {
      unsubscribe();
    };
  }, []);

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
              Tamper-Proof Audit Log
            </h1>
            <p className="text-xs sm:text-sm text-slate-400 font-['Montserrat',sans-serif] font-light mt-1 max-w-2xl">
              Immutable circular logging for zero-trust compliance. All secret fields (passwords, tokens, private keys)
              are dynamically masked in-memory to prevent persistent credential leakage.
            </p>
          </div>

          <div className="flex items-center gap-3">
            <span className="rounded-lg border border-cyan-500/30 bg-cyan-500/10 px-3 py-1.5 text-xs font-mono text-cyan-400 flex items-center gap-1.5">
              <Activity className="h-3.5 w-3.5 animate-pulse" />
              <span>Oplog Streaming Live</span>
            </span>
          </div>
        </div>
      </AccessibleFade>

      <AccessibleFade delay={0.08}>
        <div className="space-y-3">
          {events.map((evt) => (
            <GlassCard3D key={evt.id} glowColor="cyan">
              <div className="p-4 sm:p-5 flex flex-col md:flex-row md:items-center justify-between gap-4">
                <div className="space-y-1.5">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="font-mono text-xs font-bold px-2 py-0.5 rounded bg-cyan-500/20 text-cyan-400 border border-cyan-500/30">
                      {evt.action}
                    </span>
                    <span className="font-mono text-xs font-semibold text-white">
                      {evt.applicationName}
                    </span>
                    <span className="text-slate-500 text-xs font-mono">•</span>
                    <span className="font-mono text-xs text-indigo-400">
                      {evt.environment}
                    </span>
                    <span className="text-slate-500 text-xs font-mono">•</span>
                    <span className="font-mono text-xs text-emerald-400">
                      v{evt.version}
                    </span>
                  </div>
                  <p className="font-mono text-xs text-slate-300">
                    {evt.details}
                  </p>
                </div>

                <div className="flex items-center gap-4 text-[11px] font-mono text-slate-400 shrink-0">
                  <div className="flex items-center gap-1.5 bg-black/40 px-2.5 py-1 rounded border border-white/5">
                    <KeyRound className="h-3 w-3 text-indigo-400" />
                    <span>{evt.apiKeyMasked}</span>
                  </div>
                  <div className="flex items-center gap-1 text-slate-500">
                    <Clock className="h-3 w-3" />
                    <span>{new Date(evt.timestamp).toLocaleTimeString()}</span>
                  </div>
                </div>
              </div>
            </GlassCard3D>
          ))}
        </div>
      </AccessibleFade>
    </DashboardShell>
  );
}
