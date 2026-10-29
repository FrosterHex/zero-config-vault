"use client";

import React from "react";
import Link from "next/link";
import { DashboardShell } from "@/components/dashboard/DashboardShell";
import { AccessibleFade } from "@/components/motion/AccessibleMotion";
import { GlassCard3D } from "@/components/ui/GlassCard3D";
import {
  Layers,
  GitCompare,
  HardDrive,
  FileCode,
  Radio,
  KeyRound,
  ShieldCheck,
  RefreshCw,
  Cpu,
  Lock,
  ArrowRight,
  TrendingUp,
  Activity,
  Zap,
  Database,
} from "lucide-react";

import { useVault } from "@/context/VaultContext";
import { VAULT_STATUS_DISPLAY } from "@/components/dashboard/VaultStatusText";

const STATUS_BADGE = {
  ok: "border-emerald-500/30 bg-emerald-500/10 text-emerald-400",
  wait: "border-sky-500/30 bg-sky-500/10 text-sky-300",
  error: "border-rose-500/30 bg-rose-500/10 text-rose-300",
} as const;

export default function Home() {
  const { stats, loadingStats, triggerRefresh, vaultStatus } = useVault();
  const status = VAULT_STATUS_DISPLAY[vaultStatus];

  // Show a dash until real numbers arrive, never invented ones.
  const totalApps = stats?.totalApplications ?? "—";
  const totalEnvs = stats?.totalEnvironments ?? "—";
  const totalVers = stats?.totalVersions ?? "—";
  const auditLogs = stats?.totalAuditEntries ?? stats?.auditLogCount ?? "—";
  const cachedConfigs = stats?.cacheDocumentCount ?? stats?.cachedConfigCount ?? "—";

  return (
    <DashboardShell>
      <div className="pointer-events-none fixed inset-0 overflow-hidden -z-10 bg-grid-cyber" />

      {/* Overview hero (the WebGL planet lives on the landing page) */}
      <AccessibleFade delay={0}>
        <div className="relative overflow-hidden rounded-[26px] border border-[rgba(150,175,230,0.22)] bg-[radial-gradient(120%_140%_at_50%_0%,rgba(93,240,168,0.12),transparent_55%),rgba(16,24,48,0.65)] backdrop-blur-[18px] p-6 lg:p-10 shadow-[0_30px_80px_-30px_rgba(0,0,0,0.75)]">
          <div className="relative z-10">
            <div className="space-y-6 max-w-3xl">
              <div className="flex flex-wrap items-center gap-2">
                <span
                  role="status"
                  className={`flex items-center gap-1.5 rounded-full border px-3 py-1 text-[11px] font-mono font-semibold ${STATUS_BADGE[status.tone]}`}
                >
                  <span
                    className={`h-1.5 w-1.5 rounded-full bg-current ${status.tone === "ok" ? "motion-safe:animate-ping" : ""}`}
                    aria-hidden="true"
                  />
                  {status.label}
                </span>
                <span className="rounded-full border border-mint-500/30 bg-mint-500/10 px-3 py-1 text-[11px] font-mono text-mint-400">
                  AES-256-GCM envelope encryption
                </span>
              </div>

              <div>
                <h1 className="font-display text-4xl sm:text-5xl font-extrabold tracking-[-0.03em] text-white leading-[1.04]">
                  Vault <em className="not-italic bg-[linear-gradient(180deg,#5df0a8,#2fd38a)] bg-clip-text text-transparent">overview</em>
                </h1>
                <p className="mt-3 text-sm sm:text-base text-[#9aa6c4] max-w-xl leading-relaxed">
                  Configuration and secrets for microservices, stored only in MongoDB: a new version for every
                  change with one-click rollback, AES-256-GCM envelope encryption, and live updates from MongoDB
                  change streams.
                </p>
              </div>

              {/* Action Buttons */}
              <div className="flex flex-wrap items-center gap-3 pt-2">
                <Link
                  href="/configs"
                  className="glass-button flex items-center gap-2 rounded-full px-5 py-2.5 text-sm font-semibold transition-all group"
                >
                  <Layers className="h-4 w-4" />
                  <span>Enter Configuration Studio</span>
                  <ArrowRight className="h-3.5 w-3.5 group-hover:translate-x-0.5 transition-transform" />
                </Link>
                <Link
                  href="/diff"
                  className="flex items-center gap-2 rounded-full border border-[rgba(150,175,230,0.22)] bg-white/[0.04] hover:bg-white/[0.09] px-5 py-2.5 text-sm font-semibold text-[#f3f6ff] transition-all backdrop-blur-md"
                >
                  <GitCompare className="h-4 w-4 text-cyan-400" />
                  <span>Delta Studio</span>
                </Link>
                <button
                  type="button"
                  onClick={triggerRefresh}
                  title="Synchronize live stats"
                  className="flex items-center gap-2 rounded-xl border border-white/10 bg-white/5 hover:bg-white/10 p-2.5 text-slate-400 hover:text-white transition-all"
                >
                  <RefreshCw className={`h-4 w-4 ${loadingStats ? "animate-spin text-mint-400" : ""}`} />
                </button>
              </div>

              {/* Quick Key Metrics Pill */}
              <div className="flex flex-wrap items-center gap-4 pt-4 border-t border-white/10 text-xs font-mono text-slate-400">
                <div className="flex items-center gap-1.5">
                  <ShieldCheck className="h-4 w-4 text-emerald-400" />
                  <span>SHA-512 integrity hash per version</span>
                </div>
                <div className="flex items-center gap-1.5">
                  <Activity className="h-4 w-4 text-cyan-400" />
                  <span>Live updates over Server-Sent Events</span>
                </div>
                <div className="flex items-center gap-1.5">
                  <Cpu className="h-4 w-4 text-purple-400" />
                  <span>Atomic bulk import (transactions)</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </AccessibleFade>

      {/* 3D Glassmorphism Interactive KPI Cards */}
      <AccessibleFade delay={0.08}>
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
          <GlassCard3D tilt glowColor="mint">
            <div className="p-5 space-y-3">
              <div className="flex items-center justify-between text-slate-400">
                <span className="text-[11px] font-mono uppercase tracking-wider">Managed Applications</span>
                <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-mint-500/10 text-mint-400 border border-mint-500/20">
                  <Layers className="h-4 w-4" />
                </div>
              </div>
              <div className="flex items-baseline gap-2">
                <span className="text-3xl font-extrabold text-white font-mono">{totalApps}</span>
                <span className="text-xs text-emerald-400 font-mono flex items-center">
                  <TrendingUp className="h-3 w-3 mr-0.5" aria-hidden="true" /> with stored versions
                </span>
              </div>
              <div className="text-[10px] font-mono text-slate-500 pt-2 border-t border-white/5 flex justify-between gap-2">
                <span>Latest change</span>
                <span className="truncate">
                  {stats?.mostRecentApp ? `${stats.mostRecentApp} [${stats.mostRecentEnv}]` : "—"}
                </span>
              </div>
            </div>
          </GlassCard3D>

          <GlassCard3D tilt glowColor="cyan">
            <div className="p-5 space-y-3">
              <div className="flex items-center justify-between text-slate-400">
                <span className="text-[11px] font-mono uppercase tracking-wider">Active Environments</span>
                <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-cyan-500/10 text-cyan-400 border border-cyan-500/20">
                  <Zap className="h-4 w-4" />
                </div>
              </div>
              <div className="flex items-baseline gap-2">
                <span className="text-3xl font-extrabold text-white font-mono">{totalEnvs}</span>
                <span className="text-xs text-cyan-400 font-mono">in use</span>
              </div>
              <div className="text-[10px] font-mono text-slate-500 pt-2 border-t border-white/5 flex justify-between">
                <span>prod • staging • dev</span>
                <span>Keys scoped per environment</span>
              </div>
            </div>
          </GlassCard3D>

          <GlassCard3D tilt glowColor="purple">
            <div className="p-5 space-y-3">
              <div className="flex items-center justify-between text-slate-400">
                <span className="text-[11px] font-mono uppercase tracking-wider">GitOps Revisions</span>
                <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-purple-500/10 text-purple-400 border border-purple-500/20">
                  <GitCompare className="h-4 w-4" />
                </div>
              </div>
              <div className="flex items-baseline gap-2">
                <span className="text-3xl font-extrabold text-white font-mono">{totalVers}</span>
                <span className="text-xs text-purple-400 font-mono">stored versions</span>
              </div>
              <div className="text-[10px] font-mono text-slate-500 pt-2 border-t border-white/5 flex justify-between">
                <span>Compound unique index</span>
                <span>Rollback = new version</span>
              </div>
            </div>
          </GlassCard3D>

          <GlassCard3D tilt glowColor="emerald">
            <div className="p-5 space-y-3">
              <div className="flex items-center justify-between text-slate-400">
                <span className="text-[11px] font-mono uppercase tracking-wider">Security & Audit</span>
                <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
                  <Lock className="h-4 w-4" />
                </div>
              </div>
              <div className="flex items-baseline gap-2">
                <span className="text-3xl font-extrabold text-white font-mono">{auditLogs}</span>
                <span className="text-xs text-emerald-400 font-mono">audit entries</span>
              </div>
              <div className="text-[10px] font-mono text-slate-500 pt-2 border-t border-white/5 flex justify-between">
                <span>Secrets masked</span>
                <span>TTL Cache: {cachedConfigs}</span>
              </div>
            </div>
          </GlassCard3D>
        </div>
      </AccessibleFade>

      {/* Interactive Feature Matrix Grid */}
      <AccessibleFade delay={0.16}>
        <div className="space-y-4 pt-2">
          <div className="flex items-center justify-between">
            <h2 className="font-display text-2xl font-bold text-white">
              Vault System Capabilities
            </h2>
            <span className="text-xs font-mono text-slate-400">Select any module to inspect</span>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            <Link href="/configs" className="group">
              <GlassCard3D tilt glowColor="mint" className="h-full">
                <div className="p-5 space-y-3">
                  <div className="flex items-center justify-between">
                    <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-mint-500/10 text-mint-400 border border-mint-500/20 group-hover:scale-110 transition-transform">
                      <Layers className="h-5 w-5" />
                    </div>
                    <span className="text-[10px] font-mono uppercase text-mint-400 bg-mint-500/10 px-2 py-0.5 rounded border border-mint-500/20">
                      Core Engine
                    </span>
                  </div>
                  <h3 className="font-sans text-base font-bold text-white group-hover:text-mint-300 transition-colors">
                    Configuration Studio
                  </h3>
                  <p className="text-xs text-slate-400 leading-relaxed font-light">
                    Collapsible JSON tree with lock badges on envelope-encrypted secure_settings, a raw JSON editor with syntax checking, and rollback.
                  </p>
                </div>
              </GlassCard3D>
            </Link>

            <Link href="/diff" className="group">
              <GlassCard3D tilt glowColor="cyan" className="h-full">
                <div className="p-5 space-y-3">
                  <div className="flex items-center justify-between">
                    <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-cyan-500/10 text-cyan-400 border border-cyan-500/20 group-hover:scale-110 transition-transform">
                      <GitCompare className="h-5 w-5" />
                    </div>
                    <span className="text-[10px] font-mono uppercase text-cyan-400 bg-cyan-500/10 px-2 py-0.5 rounded border border-cyan-500/20">
                      GitOps
                    </span>
                  </div>
                  <h3 className="font-sans text-base font-bold text-white group-hover:text-cyan-300 transition-colors">
                    Delta Studio & Diff Engine
                  </h3>
                  <p className="text-xs text-slate-400 leading-relaxed font-light">
                    Side-by-side split comparison and unified patch views colorizing added (green), removed (red), and modified (yellow) values.
                  </p>
                </div>
              </GlassCard3D>
            </Link>

            <Link href="/binaries" className="group">
              <GlassCard3D tilt glowColor="emerald" className="h-full">
                <div className="p-5 space-y-3">
                  <div className="flex items-center justify-between">
                    <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 group-hover:scale-110 transition-transform">
                      <HardDrive className="h-5 w-5" />
                    </div>
                    <span className="text-[10px] font-mono uppercase text-emerald-400 bg-emerald-500/10 px-2 py-0.5 rounded border border-emerald-500/20">
                      GridFS
                    </span>
                  </div>
                  <h3 className="font-sans text-base font-bold text-white group-hover:text-emerald-300 transition-colors">
                    GridFS Binary Blobs
                  </h3>
                  <p className="text-xs text-slate-400 leading-relaxed font-light">
                    Store and version binary files such as TLS certificates (.crt, .pem) and Java keystores (.jks), each with a SHA-512 hash.
                  </p>
                </div>
              </GlassCard3D>
            </Link>

            <Link href="/export" className="group">
              <GlassCard3D tilt glowColor="purple" className="h-full">
                <div className="p-5 space-y-3">
                  <div className="flex items-center justify-between">
                    <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-purple-500/10 text-purple-400 border border-purple-500/20 group-hover:scale-110 transition-transform">
                      <FileCode className="h-5 w-5" />
                    </div>
                    <span className="text-[10px] font-mono uppercase text-purple-400 bg-purple-500/10 px-2 py-0.5 rounded border border-purple-500/20">
                      Export
                    </span>
                  </div>
                  <h3 className="font-sans text-base font-bold text-white group-hover:text-purple-300 transition-colors">
                    Export Studio
                  </h3>
                  <p className="text-xs text-slate-400 leading-relaxed font-light">
                    Export any stored configuration as JSON, YAML or a flat .env file for containers and deployment pipelines.
                  </p>
                </div>
              </GlassCard3D>
            </Link>

            <Link href="/audit" className="group">
              <GlassCard3D tilt glowColor="cyan" className="h-full">
                <div className="p-5 space-y-3">
                  <div className="flex items-center justify-between">
                    <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-cyan-500/10 text-cyan-400 border border-cyan-500/20 group-hover:scale-110 transition-transform">
                      <Radio className="h-5 w-5" />
                    </div>
                    <span className="text-[10px] font-mono uppercase text-cyan-400 bg-cyan-500/10 px-2 py-0.5 rounded border border-cyan-500/20">
                      50MB Capped
                    </span>
                  </div>
                  <h3 className="font-sans text-base font-bold text-white group-hover:text-cyan-300 transition-colors">
                    Capped Audit Log Stream
                  </h3>
                  <p className="text-xs text-slate-400 leading-relaxed font-light">
                    Append-only capped collection: entries cannot be deleted one by one, and the oldest are discarded at 50 MB. Secret values are masked before an entry is written.
                  </p>
                </div>
              </GlassCard3D>
            </Link>

            <Link href="/access" className="group">
              <GlassCard3D tilt glowColor="mint" className="h-full">
                <div className="p-5 space-y-3">
                  <div className="flex items-center justify-between">
                    <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-mint-500/10 text-mint-400 border border-mint-500/20 group-hover:scale-110 transition-transform">
                      <KeyRound className="h-5 w-5" />
                    </div>
                    <span className="text-[10px] font-mono uppercase text-mint-400 bg-mint-500/10 px-2 py-0.5 rounded border border-mint-500/20">
                      RBAC
                    </span>
                  </div>
                  <h3 className="font-sans text-base font-bold text-white group-hover:text-mint-300 transition-colors">
                    Scoped API Key Engine
                  </h3>
                  <p className="text-xs text-slate-400 leading-relaxed font-light">
                    API keys stored as hashes, each limited to chosen applications, environments and roles.
                  </p>
                </div>
              </GlassCard3D>
            </Link>

            <Link href="/nosql" className="group">
              <GlassCard3D tilt glowColor="purple" className="h-full">
                <div className="p-5 space-y-3">
                  <div className="flex items-center justify-between">
                    <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-purple-500/10 text-purple-400 border border-purple-500/20 group-hover:scale-110 transition-transform">
                      <Database className="h-5 w-5" />
                    </div>
                    <span className="text-[10px] font-mono uppercase text-purple-400 bg-purple-500/10 px-2 py-0.5 rounded border border-purple-500/20">
                      NoSQL Core
                    </span>
                  </div>
                  <h3 className="font-sans text-base font-bold text-white group-hover:text-purple-300 transition-colors">
                    NoSQL Deep Dive & Sharding
                  </h3>
                  <p className="text-xs text-slate-400 leading-relaxed font-light">
                    Time-series request telemetry, the $jsonSchema validation rules, and a simulator for the sharding design.
                  </p>
                </div>
              </GlassCard3D>
            </Link>
          </div>
        </div>
      </AccessibleFade>
    </DashboardShell>
  );
}
