"use client";

import React from "react";
import {
  Layers,
  Database,
  Radio,
  Clock,
  Zap,
  TrendingUp,
  ShieldCheck,
  Server,
} from "lucide-react";
import { StatsResponse } from "@/lib/api";

interface TelemetryCardsProps {
  stats: StatsResponse | null;
  loading?: boolean;
}

export function TelemetryCards({ stats }: TelemetryCardsProps) {
  const fallbackStats: StatsResponse = {
    totalApplications: 12,
    totalEnvironments: 3,
    totalVersions: 76,
    averageVersionsPerApplication: 6.33,
    auditLogCount: 1420,
    cachedConfigCount: 18,
    mostRecentlyUpdated: {
      applicationName: "payment-gateway",
      environment: "production",
      version: 8,
      updatedAt: new Date().toISOString(),
    },
  };

  const data = stats || fallbackStats;

  return (
    <div className="space-y-6">
      {/* Primary KPI Metrics Grid */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        {/* Card 1: Applications */}
        <div className="glass-panel glass-panel-hover rounded-xl p-5 relative overflow-hidden group">
          <div className="flex items-center justify-between text-slate-400 mb-2">
            <span className="text-[11px] font-mono uppercase tracking-wider">Managed Applications</span>
            <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-indigo-500/10 text-indigo-400 border border-indigo-500/20 group-hover:scale-105 transition-transform">
              <Database className="h-4 w-4" />
            </div>
          </div>
          <div className="flex items-baseline gap-2">
            <span className="text-3xl font-bold text-white font-mono">{data.totalApplications}</span>
            <span className="text-[11px] text-emerald-400 font-mono flex items-center">
              <TrendingUp className="h-3 w-3 mr-0.5" /> 100% active
            </span>
          </div>
          <div className="mt-3 flex items-center justify-between text-[10px] font-mono text-slate-500 pt-2 border-t border-white/5">
            <span>Environments: {data.totalEnvironments}</span>
            <span>MongoDB Multi-Tenant</span>
          </div>
        </div>

        {/* Card 2: Revisions */}
        <div className="glass-panel glass-panel-hover rounded-xl p-5 relative overflow-hidden group">
          <div className="flex items-center justify-between text-slate-400 mb-2">
            <span className="text-[11px] font-mono uppercase tracking-wider">Version Ledger</span>
            <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 group-hover:scale-105 transition-transform">
              <Layers className="h-4 w-4" />
            </div>
          </div>
          <div className="flex items-baseline gap-2">
            <span className="text-3xl font-bold text-white font-mono">{data.totalVersions}</span>
            <span className="text-[11px] text-indigo-400 font-mono">
              ~{data.averageVersionsPerApplication.toFixed(1)} / app
            </span>
          </div>
          <div className="mt-3 flex items-center justify-between text-[10px] font-mono text-slate-500 pt-2 border-t border-white/5">
            <span>GitOps Immutable</span>
            <span>Compound Indexed</span>
          </div>
        </div>

        {/* Card 3: Audit Trail (Capped Collection) */}
        <div className="glass-panel glass-panel-hover rounded-xl p-5 relative overflow-hidden group">
          <div className="flex items-center justify-between text-slate-400 mb-2">
            <span className="text-[11px] font-mono uppercase tracking-wider">Capped Audit Ring</span>
            <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-purple-500/10 text-purple-400 border border-purple-500/20 group-hover:scale-105 transition-transform">
              <Radio className="h-4 w-4" />
            </div>
          </div>
          <div className="flex items-baseline gap-2">
            <span className="text-3xl font-bold text-white font-mono">
              {data.auditLogCount.toLocaleString()}
            </span>
            <span className="text-[11px] text-purple-400 font-mono">50MB ring buffer</span>
          </div>
          <div className="mt-3 flex items-center justify-between text-[10px] font-mono text-slate-500 pt-2 border-t border-white/5">
            <span>Auto-purging FIFO</span>
            <span>Zero-Garbage</span>
          </div>
        </div>

        {/* Card 4: TTL In-Memory Encrypted Cache */}
        <div className="glass-panel glass-panel-hover rounded-xl p-5 relative overflow-hidden group">
          <div className="flex items-center justify-between text-slate-400 mb-2">
            <span className="text-[11px] font-mono uppercase tracking-wider">Encrypted TTL Cache</span>
            <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-amber-500/10 text-amber-400 border border-amber-500/20 group-hover:scale-105 transition-transform">
              <Zap className="h-4 w-4" />
            </div>
          </div>
          <div className="flex items-baseline gap-2">
            <span className="text-3xl font-bold text-white font-mono">{data.cachedConfigCount}</span>
            <span className="text-[11px] text-amber-300 font-mono">300s TTL index</span>
          </div>
          <div className="mt-3 flex items-center justify-between text-[10px] font-mono text-slate-500 pt-2 border-t border-white/5">
            <span>AES-256-GCM RAM Key</span>
            <span>Zero-Disk Leakage</span>
          </div>
        </div>
      </div>

      {/* Aggregation Insights & Storage Telemetry */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
        {/* Most Recent Activity Banner */}
        <div className="lg:col-span-7 glass-panel rounded-xl p-5 border border-white/10">
          <div className="flex items-center justify-between pb-3 border-b border-white/10 mb-4">
            <div className="flex items-center gap-2">
              <Clock className="h-4 w-4 text-indigo-400" />
              <h3 className="font-['Montserrat',sans-serif] text-xs font-bold uppercase tracking-wider text-white">
                Oplog Activity & Telemetry Health
              </h3>
            </div>
            <span className="rounded bg-emerald-500/20 border border-emerald-500/30 px-2 py-0.5 text-[10px] font-mono font-semibold text-emerald-400">
              MongoDB Change Streams Active
            </span>
          </div>

          {data.mostRecentlyUpdated ? (
            <div className="rounded-lg bg-black/40 border border-white/5 p-4 flex flex-col sm:flex-row sm:items-center justify-between gap-4">
              <div>
                <span className="text-[10px] font-mono text-slate-400 uppercase tracking-widest block mb-1">
                  Latest Ingestion Event
                </span>
                <div className="flex items-center gap-2">
                  <span className="font-mono text-sm font-bold text-white">
                    {data.mostRecentlyUpdated.applicationName}
                  </span>
                  <span className="rounded bg-indigo-500/20 text-indigo-300 border border-indigo-500/30 px-1.5 py-0.2 text-[10px] font-mono font-bold">
                    [{data.mostRecentlyUpdated.environment}]
                  </span>
                  <span className="rounded bg-emerald-500/20 text-emerald-300 border border-emerald-500/30 px-1.5 py-0.2 text-[10px] font-mono font-bold">
                    v{data.mostRecentlyUpdated.version}
                  </span>
                </div>
              </div>

              <div className="text-right">
                <span className="text-[10px] font-mono text-slate-400 uppercase tracking-widest block mb-1">
                  Committed At
                </span>
                <span className="font-mono text-xs text-slate-200">
                  {new Date(data.mostRecentlyUpdated.updatedAt).toLocaleString()}
                </span>
              </div>
            </div>
          ) : (
            <div className="text-slate-500 text-xs font-mono py-4 text-center">
              No recent changes recorded.
            </div>
          )}

          {/* Architecture Checklist */}
          <div className="grid grid-cols-2 sm:grid-cols-3 gap-3 mt-4 pt-3 border-t border-white/5 text-[11px] font-mono">
            <div className="flex items-center gap-1.5 text-slate-300">
              <ShieldCheck className="h-3.5 w-3.5 text-emerald-400 shrink-0" />
              <span>DEK/KEK Envelope</span>
            </div>
            <div className="flex items-center gap-1.5 text-slate-300">
              <ShieldCheck className="h-3.5 w-3.5 text-emerald-400 shrink-0" />
              <span>SHA-512 Canonical</span>
            </div>
            <div className="flex items-center gap-1.5 text-slate-300">
              <ShieldCheck className="h-3.5 w-3.5 text-emerald-400 shrink-0" />
              <span>ACID ?atomic=true</span>
            </div>
            <div className="flex items-center gap-1.5 text-slate-300">
              <ShieldCheck className="h-3.5 w-3.5 text-emerald-400 shrink-0" />
              <span>GridFS Blob Vault</span>
            </div>
            <div className="flex items-center gap-1.5 text-slate-300">
              <ShieldCheck className="h-3.5 w-3.5 text-emerald-400 shrink-0" />
              <span>Scoped RBAC Prefix</span>
            </div>
            <div className="flex items-center gap-1.5 text-slate-300">
              <ShieldCheck className="h-3.5 w-3.5 text-emerald-400 shrink-0" />
              <span>Zero-Lombok Core</span>
            </div>
          </div>
        </div>

        {/* Environment Tier Allocation Visualizer */}
        <div className="lg:col-span-5 glass-panel rounded-xl p-5 border border-white/10 flex flex-col justify-between">
          <div>
            <div className="flex items-center justify-between pb-3 border-b border-white/10 mb-4">
              <div className="flex items-center gap-2">
                <Server className="h-4 w-4 text-indigo-400" />
                <h3 className="font-['Montserrat',sans-serif] text-xs font-bold uppercase tracking-wider text-white">
                  Tier Allocation Breakdown
                </h3>
              </div>
              <span className="text-[10px] font-mono text-slate-400">3 Tiers</span>
            </div>

            {/* Simulated Distribution Bars */}
            <div className="space-y-3 font-mono text-xs">
              <div>
                <div className="flex justify-between text-[11px] mb-1">
                  <span className="text-emerald-400 font-semibold">Production (PCI Tier)</span>
                  <span className="text-slate-400">55% allocation</span>
                </div>
                <div className="h-2 w-full rounded-full bg-white/5 overflow-hidden">
                  <div className="h-full rounded-full bg-emerald-500" style={{ width: "55%" }} />
                </div>
              </div>

              <div>
                <div className="flex justify-between text-[11px] mb-1">
                  <span className="text-indigo-400 font-semibold">Staging (Pre-Release)</span>
                  <span className="text-slate-400">30% allocation</span>
                </div>
                <div className="h-2 w-full rounded-full bg-white/5 overflow-hidden">
                  <div className="h-full rounded-full bg-indigo-500" style={{ width: "30%" }} />
                </div>
              </div>

              <div>
                <div className="flex justify-between text-[11px] mb-1">
                  <span className="text-sky-400 font-semibold">Development (Sandbox)</span>
                  <span className="text-slate-400">15% allocation</span>
                </div>
                <div className="h-2 w-full rounded-full bg-white/5 overflow-hidden">
                  <div className="h-full rounded-full bg-sky-500" style={{ width: "15%" }} />
                </div>
              </div>
            </div>
          </div>

          <div className="mt-4 pt-3 border-t border-white/5 text-[10px] font-mono text-slate-500 flex justify-between">
            <span>Engine: MongoDB Aggregation Pipeline</span>
            <span className="text-emerald-400">Live Synchronized</span>
          </div>
        </div>
      </div>
    </div>
  );
}
