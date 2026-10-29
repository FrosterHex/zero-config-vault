"use client";

import React from "react";
import Link from "next/link";
import { DashboardShell } from "@/components/dashboard/DashboardShell";
import { AccessibleFade } from "@/components/motion/AccessibleMotion";
import { VaultHologram3D } from "@/components/3d/VaultHologram3D";
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

export default function Home() {
  const { stats, loadingStats, triggerRefresh } = useVault();

  // Show a dash until real numbers arrive, never invented ones.
  const totalApps = stats?.totalApplications ?? "—";
  const totalEnvs = stats?.totalEnvironments ?? "—";
  const totalVers = stats?.totalVersions ?? "—";
  const auditLogs = stats?.totalAuditEntries ?? stats?.auditLogCount ?? "—";
  const cachedConfigs = stats?.cacheDocumentCount ?? stats?.cachedConfigCount ?? "—";

  return (
    <DashboardShell>
      {/* 3D Cyber Atmosphere Background Elements */}
      <div className="pointer-events-none fixed inset-0 overflow-hidden -z-10 bg-grid-cyber">
        {/* Floating Cyber Neon Light Orbs */}
        <div className="absolute top-1/4 left-1/4 h-96 w-96 rounded-full bg-indigo-600/15 blur-[120px]" />
        <div className="absolute top-1/3 right-1/4 h-[30rem] w-[30rem] rounded-full bg-cyan-500/10 blur-[140px]" />
        <div className="absolute bottom-10 left-1/3 h-80 w-80 rounded-full bg-purple-600/10 blur-[130px]" />
      </div>

      {/* 3D Holographic Hero Enclave */}
      <AccessibleFade delay={0}>
        <div className="relative overflow-hidden rounded-3xl border border-white/10 bg-gradient-to-b from-slate-900/70 via-slate-950/80 to-[#070b16]/90 backdrop-blur-2xl p-6 lg:p-10 shadow-2xl">
          {/* Subtle Horizon Ground Grid */}
          <div className="pointer-events-none absolute bottom-0 inset-x-0 h-40 horizon-grid opacity-60" />

          <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-center relative z-10">
            {/* Left Column: Vision & Quick Access */}
            <div className="lg:col-span-7 space-y-6">
              <div className="flex flex-wrap items-center gap-2">
                <span className="flex items-center gap-1.5 rounded-full border border-emerald-500/30 bg-emerald-500/10 px-3 py-1 text-[11px] font-mono font-semibold text-emerald-400">
                  <span className="h-1.5 w-1.5 rounded-full bg-emerald-400 animate-ping" />
                  MongoDB 7.0 Replica Set (rs0) Live
                </span>
                <span className="rounded-full border border-indigo-500/30 bg-indigo-500/10 px-3 py-1 text-[11px] font-mono text-indigo-400">
                  Hardware Envelope Encryption
                </span>
              </div>

              <div>
                <h1 className="font-['Cormorant_Garamond',serif] text-4xl sm:text-5xl lg:text-6xl font-extrabold tracking-tight text-white leading-[1.08]">
                  Zero-Config <br />
                  <span className="bg-gradient-to-r from-indigo-400 via-cyan-300 to-purple-400 bg-clip-text text-transparent">
                    Secure Vault OS
                  </span>
                </h1>
                <p className="mt-3 text-sm sm:text-base text-slate-300 font-['Montserrat',sans-serif] font-light max-w-xl leading-relaxed">
                  Next-generation centralized configuration and secret plane. Powered exclusively by MongoDB:
                  immutable GitOps temporal versioning, AES-256-GCM envelope cryptography, and real-time oplog Change Streams.
                </p>
              </div>

              {/* Action Buttons */}
              <div className="flex flex-wrap items-center gap-3 pt-2">
                <Link
                  href="/configs"
                  className="glass-button flex items-center gap-2 rounded-xl px-5 py-2.5 text-xs font-semibold text-white transition-all group"
                >
                  <Layers className="h-4 w-4 text-indigo-300 group-hover:rotate-12 transition-transform" />
                  <span>Enter Configuration Studio</span>
                  <ArrowRight className="h-3.5 w-3.5 text-indigo-300 group-hover:translate-x-0.5 transition-transform" />
                </Link>
                <Link
                  href="/diff"
                  className="flex items-center gap-2 rounded-xl border border-white/10 bg-white/5 hover:bg-white/10 px-5 py-2.5 text-xs font-medium text-slate-200 transition-all hover:border-cyan-500/40"
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
                  <RefreshCw className={`h-4 w-4 ${loadingStats ? "animate-spin text-indigo-400" : ""}`} />
                </button>
              </div>

              {/* Quick Key Metrics Pill */}
              <div className="flex flex-wrap items-center gap-4 pt-4 border-t border-white/10 text-xs font-mono text-slate-400">
                <div className="flex items-center gap-1.5">
                  <ShieldCheck className="h-4 w-4 text-emerald-400" />
                  <span>SHA-512 Verification</span>
                </div>
                <div className="flex items-center gap-1.5">
                  <Activity className="h-4 w-4 text-cyan-400" />
                  <span>SSE Oplog Stream Active</span>
                </div>
                <div className="flex items-center gap-1.5">
                  <Cpu className="h-4 w-4 text-purple-400" />
                  <span>Zero Lombok Engine</span>
                </div>
              </div>
            </div>

            {/* Right Column: Interactive 3D Holographic Vault Canvas */}
            <div className="lg:col-span-5 flex items-center justify-center relative min-h-[380px]">
              <div className="absolute inset-0 flex items-center justify-center pointer-events-none">
                <div className="h-72 w-72 rounded-full border border-indigo-500/20 animate-spin" style={{ animationDuration: "24s" }} />
                <div className="absolute h-84 w-84 rounded-full border border-dashed border-cyan-500/20 animate-spin" style={{ animationDuration: "36s", animationDirection: "reverse" }} />
              </div>

              <VaultHologram3D size={420} className="z-10" />
            </div>
          </div>
        </div>
      </AccessibleFade>

      {/* 3D Glassmorphism Interactive KPI Cards */}
      <AccessibleFade delay={0.08}>
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
          <GlassCard3D glowColor="indigo">
            <div className="p-5 space-y-3">
              <div className="flex items-center justify-between text-slate-400">
                <span className="text-[11px] font-mono uppercase tracking-wider">Managed Applications</span>
                <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-indigo-500/10 text-indigo-400 border border-indigo-500/20">
                  <Layers className="h-4 w-4" />
                </div>
              </div>
              <div className="flex items-baseline gap-2">
                <span className="text-3xl font-extrabold text-white font-mono">{totalApps}</span>
                <span className="text-xs text-emerald-400 font-mono flex items-center">
                  <TrendingUp className="h-3 w-3 mr-0.5" /> 100% Online
                </span>
              </div>
              <div className="text-[10px] font-mono text-slate-500 pt-2 border-t border-white/5 flex justify-between">
                <span>Multi-Tenant Cluster</span>
                <span>Tier 0 - Tier 2</span>
              </div>
            </div>
          </GlassCard3D>

          <GlassCard3D glowColor="cyan">
            <div className="p-5 space-y-3">
              <div className="flex items-center justify-between text-slate-400">
                <span className="text-[11px] font-mono uppercase tracking-wider">Active Environments</span>
                <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-cyan-500/10 text-cyan-400 border border-cyan-500/20">
                  <Zap className="h-4 w-4" />
                </div>
              </div>
              <div className="flex items-baseline gap-2">
                <span className="text-3xl font-extrabold text-white font-mono">{totalEnvs}</span>
                <span className="text-xs text-cyan-400 font-mono">Isolated Enclaves</span>
              </div>
              <div className="text-[10px] font-mono text-slate-500 pt-2 border-t border-white/5 flex justify-between">
                <span>prod • staging • dev</span>
                <span>Granular RBAC</span>
              </div>
            </div>
          </GlassCard3D>

          <GlassCard3D glowColor="purple">
            <div className="p-5 space-y-3">
              <div className="flex items-center justify-between text-slate-400">
                <span className="text-[11px] font-mono uppercase tracking-wider">GitOps Revisions</span>
                <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-purple-500/10 text-purple-400 border border-purple-500/20">
                  <GitCompare className="h-4 w-4" />
                </div>
              </div>
              <div className="flex items-baseline gap-2">
                <span className="text-3xl font-extrabold text-white font-mono">{totalVers}</span>
                <span className="text-xs text-purple-400 font-mono">Immutable</span>
              </div>
              <div className="text-[10px] font-mono text-slate-500 pt-2 border-t border-white/5 flex justify-between">
                <span>Compound Unique Index</span>
                <span>Atomic Rollback</span>
              </div>
            </div>
          </GlassCard3D>

          <GlassCard3D glowColor="emerald">
            <div className="p-5 space-y-3">
              <div className="flex items-center justify-between text-slate-400">
                <span className="text-[11px] font-mono uppercase tracking-wider">Security & Audit</span>
                <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
                  <Lock className="h-4 w-4" />
                </div>
              </div>
              <div className="flex items-baseline gap-2">
                <span className="text-3xl font-extrabold text-white font-mono">{auditLogs}</span>
                <span className="text-xs text-emerald-400 font-mono">Capped Events</span>
              </div>
              <div className="text-[10px] font-mono text-slate-500 pt-2 border-t border-white/5 flex justify-between">
                <span>Masked Audit Traces</span>
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
            <h2 className="font-['Cormorant_Garamond',serif] text-2xl font-bold text-white">
              Vault System Capabilities
            </h2>
            <span className="text-xs font-mono text-slate-400">Select any module to inspect</span>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            <Link href="/configs" className="group">
              <GlassCard3D glowColor="indigo" className="h-full">
                <div className="p-5 space-y-3">
                  <div className="flex items-center justify-between">
                    <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-indigo-500/10 text-indigo-400 border border-indigo-500/20 group-hover:scale-110 transition-transform">
                      <Layers className="h-5 w-5" />
                    </div>
                    <span className="text-[10px] font-mono uppercase text-indigo-400 bg-indigo-500/10 px-2 py-0.5 rounded border border-indigo-500/20">
                      Core Engine
                    </span>
                  </div>
                  <h3 className="font-['Montserrat',sans-serif] text-base font-bold text-white group-hover:text-indigo-300 transition-colors">
                    Configuration Studio
                  </h3>
                  <p className="text-xs text-slate-400 leading-relaxed font-light">
                    Interactive JSON tree editor with collapsible keys, envelope-encrypted secure_settings lock badges, and raw editor validation.
                  </p>
                </div>
              </GlassCard3D>
            </Link>

            <Link href="/diff" className="group">
              <GlassCard3D glowColor="cyan" className="h-full">
                <div className="p-5 space-y-3">
                  <div className="flex items-center justify-between">
                    <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-cyan-500/10 text-cyan-400 border border-cyan-500/20 group-hover:scale-110 transition-transform">
                      <GitCompare className="h-5 w-5" />
                    </div>
                    <span className="text-[10px] font-mono uppercase text-cyan-400 bg-cyan-500/10 px-2 py-0.5 rounded border border-cyan-500/20">
                      GitOps
                    </span>
                  </div>
                  <h3 className="font-['Montserrat',sans-serif] text-base font-bold text-white group-hover:text-cyan-300 transition-colors">
                    Delta Studio & Diff Engine
                  </h3>
                  <p className="text-xs text-slate-400 leading-relaxed font-light">
                    Side-by-side split comparison and unified patch views colorizing added (green), removed (red), and modified (yellow) values.
                  </p>
                </div>
              </GlassCard3D>
            </Link>

            <Link href="/binaries" className="group">
              <GlassCard3D glowColor="emerald" className="h-full">
                <div className="p-5 space-y-3">
                  <div className="flex items-center justify-between">
                    <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 group-hover:scale-110 transition-transform">
                      <HardDrive className="h-5 w-5" />
                    </div>
                    <span className="text-[10px] font-mono uppercase text-emerald-400 bg-emerald-500/10 px-2 py-0.5 rounded border border-emerald-500/20">
                      GridFS
                    </span>
                  </div>
                  <h3 className="font-['Montserrat',sans-serif] text-base font-bold text-white group-hover:text-emerald-300 transition-colors">
                    GridFS Binary Blobs
                  </h3>
                  <p className="text-xs text-slate-400 leading-relaxed font-light">
                    Store and version binary configuration assets: TLS certificates (.crt, .pem), Java keystores (.jks), and encrypted bundles.
                  </p>
                </div>
              </GlassCard3D>
            </Link>

            <Link href="/export" className="group">
              <GlassCard3D glowColor="purple" className="h-full">
                <div className="p-5 space-y-3">
                  <div className="flex items-center justify-between">
                    <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-purple-500/10 text-purple-400 border border-purple-500/20 group-hover:scale-110 transition-transform">
                      <FileCode className="h-5 w-5" />
                    </div>
                    <span className="text-[10px] font-mono uppercase text-purple-400 bg-purple-500/10 px-2 py-0.5 rounded border border-purple-500/20">
                      Export
                    </span>
                  </div>
                  <h3 className="font-['Montserrat',sans-serif] text-base font-bold text-white group-hover:text-purple-300 transition-colors">
                    Export Studio
                  </h3>
                  <p className="text-xs text-slate-400 leading-relaxed font-light">
                    Instantly transform live JSON configurations into clean YAML or flat .env files for seamless container and deployment integration.
                  </p>
                </div>
              </GlassCard3D>
            </Link>

            <Link href="/audit" className="group">
              <GlassCard3D glowColor="cyan" className="h-full">
                <div className="p-5 space-y-3">
                  <div className="flex items-center justify-between">
                    <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-cyan-500/10 text-cyan-400 border border-cyan-500/20 group-hover:scale-110 transition-transform">
                      <Radio className="h-5 w-5" />
                    </div>
                    <span className="text-[10px] font-mono uppercase text-cyan-400 bg-cyan-500/10 px-2 py-0.5 rounded border border-cyan-500/20">
                      50MB Capped
                    </span>
                  </div>
                  <h3 className="font-['Montserrat',sans-serif] text-base font-bold text-white group-hover:text-cyan-300 transition-colors">
                    Capped Audit Log Stream
                  </h3>
                  <p className="text-xs text-slate-400 leading-relaxed font-light">
                    Tamper-proof append-only circular log collection. Secrets are masked dynamically to guarantee zero credential leakage.
                  </p>
                </div>
              </GlassCard3D>
            </Link>

            <Link href="/access" className="group">
              <GlassCard3D glowColor="indigo" className="h-full">
                <div className="p-5 space-y-3">
                  <div className="flex items-center justify-between">
                    <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-indigo-500/10 text-indigo-400 border border-indigo-500/20 group-hover:scale-110 transition-transform">
                      <KeyRound className="h-5 w-5" />
                    </div>
                    <span className="text-[10px] font-mono uppercase text-indigo-400 bg-indigo-500/10 px-2 py-0.5 rounded border border-indigo-500/20">
                      RBAC
                    </span>
                  </div>
                  <h3 className="font-['Montserrat',sans-serif] text-base font-bold text-white group-hover:text-indigo-300 transition-colors">
                    Scoped API Key Engine
                  </h3>
                  <p className="text-xs text-slate-400 leading-relaxed font-light">
                    Role-Based Access Control matrix restricting token access to specific applications, environments, and privileges.
                  </p>
                </div>
              </GlassCard3D>
            </Link>

            <Link href="/nosql" className="group">
              <GlassCard3D glowColor="purple" className="h-full">
                <div className="p-5 space-y-3">
                  <div className="flex items-center justify-between">
                    <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-purple-500/10 text-purple-400 border border-purple-500/20 group-hover:scale-110 transition-transform">
                      <Database className="h-5 w-5" />
                    </div>
                    <span className="text-[10px] font-mono uppercase text-purple-400 bg-purple-500/10 px-2 py-0.5 rounded border border-purple-500/20">
                      NoSQL Core
                    </span>
                  </div>
                  <h3 className="font-['Montserrat',sans-serif] text-base font-bold text-white group-hover:text-purple-300 transition-colors">
                    NoSQL Deep Dive & Sharding
                  </h3>
                  <p className="text-xs text-slate-400 leading-relaxed font-light">
                    Native Time-Series request telemetry, engine-level $jsonSchema validation rules, and interactive Shard Key routing simulator.
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
