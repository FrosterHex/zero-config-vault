"use client";

import React, { useState, useEffect, useCallback, useRef, Suspense } from "react";
import { useSearchParams } from "next/navigation";
import Link from "next/link";
import { DashboardShell } from "@/components/dashboard/DashboardShell";
import { AccessibleFade } from "@/components/motion/AccessibleMotion";
import { GlassCard3D } from "@/components/ui/GlassCard3D";
import { DiffViewer } from "@/components/diff-viewer";
import { RevisionTimeline } from "@/components/timeline";
import { useVault, useSelectedScope, Environment } from "@/context/VaultContext";
import { vaultApi, DiffResponse, VersionSummary } from "@/lib/api";
import {
  ArrowLeftRight,
  RefreshCw,
  History,
  GitCompare,
  PlusCircle,
  FileCheck2,
} from "lucide-react";

function DiffStudioContent() {
  const searchParams = useSearchParams();
  const { refreshKey, apps, envs } = useVault();
  const { selectedApp, selectedEnv, selectApp, selectEnv } = useSelectedScope(
    searchParams.get("app"),
    searchParams.get("env")
  );

  const [v1, setV1] = useState<number>(() => Number(searchParams.get("v1")) || 0);
  const [v2, setV2] = useState<number>(() => Number(searchParams.get("v2")) || 0);

  const [versions, setVersions] = useState<VersionSummary[]>([]);
  const [diffData, setDiffData] = useState<DiffResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Load versions. Within the same app/env, keep the chosen pair (for example v1/v2 from
  // "diff with latest") while both still exist; otherwise default to the two newest versions.
  const pairScope = useRef(`${selectedApp}/${selectedEnv}`);
  const loadVersions = useCallback(async () => {
    setError(null);
    const scope = `${selectedApp}/${selectedEnv}`;
    const sameScope = scope === pairScope.current;
    pairScope.current = scope;
    try {
      const list = await vaultApi.listVersions(selectedApp, selectedEnv);
      setVersions(list);
      const keep = (v: number) => sameScope && list.some((ver) => ver.version === v);
      if (list.length >= 2) {
        setV2((cur) => (keep(cur) ? cur : list[0].version));
        setV1((cur) => (keep(cur) ? cur : list[1].version));
      } else {
        setDiffData(null);
      }
      return list;
    } catch (e: unknown) {
      setVersions([]);
      setDiffData(null);
      setError(`Could not load versions: ${e instanceof Error ? e.message : "unknown error"}`);
      return [];
    }
  }, [selectedApp, selectedEnv]);

  // Load diff; only the newest request may update the page.
  const diffSeq = useRef(0);
  const loadDiff = useCallback(async () => {
    if (!v1 || !v2 || versions.length < 2) {
      setDiffData(null);
      return;
    }
    const seq = ++diffSeq.current;
    setLoading(true);
    setError(null);
    try {
      const data = await vaultApi.diffVersions(selectedApp, selectedEnv, v1, v2);
      if (seq === diffSeq.current) setDiffData(data);
    } catch (e: unknown) {
      if (seq !== diffSeq.current) return;
      setError(e instanceof Error ? e.message : "Could not compute the diff");
      setDiffData(null);
    } finally {
      if (seq === diffSeq.current) setLoading(false);
    }
  }, [selectedApp, selectedEnv, v1, v2, versions.length]);

  useEffect(() => {
    loadVersions();
  }, [loadVersions, refreshKey]);

  useEffect(() => {
    if (versions.length >= 2 && v1 && v2) {
      loadDiff();
    }
  }, [loadDiff, v1, v2, versions.length]);

  const handleSwap = () => {
    const temp = v1;
    setV1(v2);
    setV2(temp);
  };

  return (
    <DashboardShell>
      {/* 3D Cyber Atmosphere Background Elements */}
      <div className="pointer-events-none fixed inset-0 overflow-hidden -z-10 bg-grid-cyber">
        <div className="absolute top-1/4 right-1/4 h-80 w-80 rounded-full bg-cyan-600/15 blur-[130px]" />
        <div className="absolute top-1/3 left-1/4 h-80 w-80 rounded-full bg-indigo-600/10 blur-[130px]" />
      </div>

      {/* Editorial Header */}
      <AccessibleFade delay={0}>
        <div className="flex flex-col lg:flex-row lg:items-end justify-between gap-4 pb-4 border-b border-white/10">
          <div>
            <div className="flex items-center gap-2 mb-1">
              <span className="flex h-2 w-2 rounded-full bg-cyan-400" />
              <span className="text-[11px] font-mono uppercase tracking-widest text-cyan-400 font-semibold">
                Structural Delta Analysis
              </span>
            </div>
            <h1 className="font-['Cormorant_Garamond',serif] text-4xl md:text-5xl font-bold tracking-tight text-white leading-tight">
              Colorized Diff Studio
            </h1>
            <p className="text-xs md:text-sm text-slate-400 max-w-2xl font-['Montserrat',sans-serif] font-light mt-1">
              Compare any two historical configuration revisions key-by-key. Highlight added,
              removed, and modified keys with cryptographic assurance.
            </p>
          </div>

          {/* Scope Selectors */}
          <div className="flex flex-wrap items-center gap-2">
            <div className="flex items-center gap-1.5 rounded-lg border border-white/10 bg-slate-900/80 px-2.5 py-1 text-xs">
              <span className="text-slate-400 font-mono text-[11px]">App:</span>
              <select
                value={selectedApp}
                onChange={(e) => selectApp(e.target.value)}
                className="bg-transparent font-mono text-white text-xs font-semibold focus:outline-none cursor-pointer"
              >
                {apps.map((a) => (
                  <option key={a} value={a} className="bg-slate-900 text-white">
                    {a}
                  </option>
                ))}
              </select>
            </div>

            <div className="flex items-center gap-1.5 rounded-lg border border-white/10 bg-slate-900/80 px-2.5 py-1 text-xs">
              <span className="text-slate-400 font-mono text-[11px]">Env:</span>
              <select
                value={selectedEnv}
                onChange={(e) => selectEnv(e.target.value as Environment)}
                className="bg-transparent font-mono text-white text-xs font-semibold focus:outline-none cursor-pointer"
              >
                {envs.map((env) => (
                  <option key={env} value={env} className="bg-slate-900 text-white">
                    {env}
                  </option>
                ))}
              </select>
            </div>

            <button
              type="button"
              onClick={loadVersions}
              title="Refresh versions from server"
              className="rounded-lg border border-white/10 bg-white/5 p-2 text-slate-400 hover:text-white hover:bg-white/10 transition-colors"
            >
              <RefreshCw className={`h-3.5 w-3.5 ${loading ? "animate-spin text-cyan-400" : ""}`} />
            </button>
          </div>
        </div>
      </AccessibleFade>

      {/* Version Comparison Selector Ribbon */}
      <AccessibleFade delay={0.05}>
        <div className="flex flex-wrap items-center justify-between gap-4 rounded-xl border border-white/10 bg-slate-950/40 p-4">
          <div className="flex flex-wrap items-center gap-3">
            {/* V1 Base */}
            <div className="flex items-center gap-2">
              <span className="text-xs font-mono text-slate-400 font-semibold">Base (v1):</span>
              <select
                value={v1}
                disabled={versions.length < 2}
                onChange={(e) => setV1(Number(e.target.value))}
                className="h-8 rounded-lg border border-indigo-500/30 bg-indigo-950/40 px-3 text-xs font-mono font-bold text-indigo-300 focus:outline-none focus:ring-1 focus:ring-indigo-500 cursor-pointer disabled:opacity-40"
              >
                {versions.map((ver) => (
                  <option key={ver.version} value={ver.version} className="bg-slate-900 text-white">
                    Revision v{ver.version}
                  </option>
                ))}
              </select>
            </div>

            {/* Swap Button */}
            <button
              type="button"
              disabled={versions.length < 2}
              onClick={handleSwap}
              title="Swap comparison order"
              className="flex h-8 w-8 items-center justify-center rounded-lg border border-white/10 bg-white/5 text-slate-400 hover:text-white hover:bg-white/10 transition-colors disabled:opacity-40"
            >
              <ArrowLeftRight className="h-3.5 w-3.5" />
            </button>

            {/* V2 Target */}
            <div className="flex items-center gap-2">
              <span className="text-xs font-mono text-slate-400 font-semibold">Target (v2):</span>
              <select
                value={v2}
                disabled={versions.length < 2}
                onChange={(e) => setV2(Number(e.target.value))}
                className="h-8 rounded-lg border border-cyan-500/30 bg-cyan-950/40 px-3 text-xs font-mono font-bold text-cyan-300 focus:outline-none focus:ring-1 focus:ring-cyan-500 cursor-pointer disabled:opacity-40"
              >
                {versions.map((ver) => (
                  <option key={ver.version} value={ver.version} className="bg-slate-900 text-white">
                    Revision v{ver.version}
                  </option>
                ))}
              </select>
            </div>
          </div>

          {/* Re-compute Button */}
          <button
            type="button"
            disabled={versions.length < 2}
            onClick={loadDiff}
            className="inline-flex items-center gap-2 rounded-lg border border-white/10 bg-white/5 px-3 py-1.5 text-xs font-mono text-slate-300 hover:bg-white/10 hover:text-white transition-colors disabled:opacity-40"
          >
            <RefreshCw className={`h-3.5 w-3.5 ${loading ? "animate-spin text-cyan-400" : ""}`} />
            <span>Re-compute Delta</span>
          </button>
        </div>

        {error && (
          <div className="mt-2 rounded-lg border border-rose-500/30 bg-rose-950/40 px-3.5 py-2 text-xs font-mono text-rose-300">
            {error}
          </div>
        )}
      </AccessibleFade>

      {/* Main Split Grid or Informative Empty State */}
      <AccessibleFade delay={0.1}>
        {versions.length < 2 ? (
          <GlassCard3D glowColor="indigo">
            <div className="p-10 flex flex-col items-center justify-center text-center space-y-4">
              <div className="flex h-16 w-16 items-center justify-center rounded-2xl bg-indigo-500/10 text-indigo-400 border border-indigo-500/20">
                <GitCompare className="h-8 w-8" />
              </div>
              <div className="space-y-1">
                <h3 className="font-['Montserrat',sans-serif] text-lg font-bold text-white">
                  Requires At Least 2 Revisions to Diff
                </h3>
                <p className="text-xs sm:text-sm text-slate-400 font-mono max-w-md">
                  Application <span className="text-indigo-400 font-bold">&quot;{selectedApp}&quot;</span> in environment{" "}
                  <span className="text-cyan-400 font-bold">&quot;{selectedEnv}&quot;</span> has{" "}
                  <span className="text-emerald-400 font-bold">{versions.length} revision(s)</span>.
                </p>
              </div>
              <div className="flex flex-wrap items-center justify-center gap-3 pt-2">
                <Link
                  href={`/configs?app=${encodeURIComponent(selectedApp)}&env=${encodeURIComponent(selectedEnv)}`}
                  className="glass-button flex items-center gap-2 rounded-lg px-4 py-2 text-xs font-semibold text-white transition-all"
                >
                  <PlusCircle className="h-4 w-4" />
                  <span>Create Revision in Configuration Studio</span>
                </Link>
                <button
                  type="button"
                  onClick={() => {
                    // Switch to an app with multiple revisions
                    selectApp("payment-gateway");
                    selectEnv("production");
                  }}
                  className="rounded-lg border border-white/10 bg-white/5 hover:bg-white/10 px-4 py-2 text-xs font-mono text-slate-300 transition-colors"
                >
                  View payment-gateway [production] Diff
                </button>
              </div>
            </div>
          </GlassCard3D>
        ) : (
          <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 min-h-[600px]">
            {/* Left Column: Visual Git Timeline Path in 3D Card */}
            <div className="lg:col-span-4 h-[600px]">
              <GlassCard3D glowColor="indigo" className="h-full">
                <div className="p-4 h-full flex flex-col">
                  <div className="flex items-center gap-2 mb-4 pb-2 border-b border-white/10 shrink-0">
                    <History className="h-4 w-4 text-indigo-400" />
                    <h3 className="font-['Montserrat',sans-serif] text-xs font-bold uppercase tracking-wider text-white">
                      Git Timeline Path
                    </h3>
                  </div>
                  <div className="flex-1 overflow-auto">
                    <RevisionTimeline
                      versions={versions}
                      selectedV1={v1}
                      selectedV2={v2}
                      onSelectV1={(version) => setV1(version)}
                      onSelectV2={(version) => setV2(version)}
                    />
                  </div>
                </div>
              </GlassCard3D>
            </div>

            {/* Right Column: Colorized Side-by-Side Diff Viewer in 3D Card */}
            <div className="lg:col-span-8 h-[600px]">
              <GlassCard3D glowColor="cyan" className="h-full">
                {diffData ? (
                  <DiffViewer
                    diff={diffData}
                    v1={v1}
                    v2={v2}
                    appName={selectedApp}
                    env={selectedEnv}
                  />
                ) : error ? (
                  <div className="flex flex-col items-center justify-center h-full text-center p-8 space-y-3 font-mono text-xs text-rose-300">
                    <FileCheck2 className="h-8 w-8 text-rose-400" />
                    <p>Could not compare v{v1} and v{v2}. See the error above.</p>
                  </div>
                ) : (
                  <div className="flex flex-col items-center justify-center h-full text-center p-8 space-y-3 font-mono text-xs text-slate-400">
                    <FileCheck2 className="h-8 w-8 text-cyan-400 animate-pulse" />
                    <p>Computing structural differences between v{v1} and v{v2}...</p>
                  </div>
                )}
              </GlassCard3D>
            </div>
          </div>
        )}
      </AccessibleFade>
    </DashboardShell>
  );
}

export default function DiffPage() {
  return (
    <Suspense fallback={<div className="p-8 text-center text-slate-400 font-mono">Loading Diff Studio...</div>}>
      <DiffStudioContent />
    </Suspense>
  );
}
