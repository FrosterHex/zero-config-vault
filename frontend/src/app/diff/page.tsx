"use client";

import React, { useState, useEffect, useCallback, Suspense } from "react";
import { useSearchParams } from "next/navigation";
import { DashboardShell } from "@/components/dashboard/DashboardShell";
import { AccessibleFade } from "@/components/motion/AccessibleMotion";
import { DiffViewer } from "@/components/diff-viewer";
import { RevisionTimeline } from "@/components/timeline";
import { vaultApi, DiffResponse, VersionSummary } from "@/lib/api";
import {
  ArrowLeftRight,
  RefreshCw,
  AlertCircle,
  History,
} from "lucide-react";

const APPS = ["payment-gateway", "order-service", "auth-service"];
const ENVS = ["production", "staging", "development"];

const DEFAULT_DIFF: DiffResponse = {
  added: {
    "payment.gateways[3]": "apple_pay",
    "tags.release": "v2.4.0",
  },
  removed: {},
  modified: {
    "server.timeoutMs": { oldValue: 5000, newValue: 3000 },
    "payment.rateLimitPerMinute": { oldValue: 1200, newValue: 2500 },
  },
  unchangedCount: 8,
};

function DiffStudioContent() {
  const searchParams = useSearchParams();

  const [selectedApp, setSelectedApp] = useState(() => searchParams.get("app") || "payment-gateway");
  const [selectedEnv, setSelectedEnv] = useState(() => searchParams.get("env") || "production");

  const [v1, setV1] = useState<number>(() => {
    const p1 = searchParams.get("v1");
    return p1 ? parseInt(p1, 10) : 1;
  });

  const [v2, setV2] = useState<number>(() => {
    const p2 = searchParams.get("v2");
    return p2 ? parseInt(p2, 10) : 2;
  });

  const [versions, setVersions] = useState<VersionSummary[]>([]);
  const [diffData, setDiffData] = useState<DiffResponse>(DEFAULT_DIFF);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Load versions
  const loadVersions = useCallback(async () => {
    try {
      const list = await vaultApi.listVersions(selectedApp, selectedEnv);
      setVersions(list);
      if (list.length >= 2) {
        setV2(list[0].version);
        setV1(list[1].version);
      } else if (list.length === 1) {
        setV2(list[0].version);
        setV1(list[0].version);
      }
    } catch {
      // Mock versions fallback
      const mockList: VersionSummary[] = [
        {
          version: 3,
          documentHash: "b893f1246d88c221469e34927f8d68f6385a5358052069c90be68d18e95081c7",
          tags: { release: "v2.4.0" },
          createdAt: new Date().toISOString(),
        },
        {
          version: 2,
          documentHash: "a143f1146d88c221469e34927f8d68f6385a5358052069c90be68d18e95081b2",
          tags: { release: "v2.3.1" },
          createdAt: new Date(Date.now() - 86400000).toISOString(),
        },
        {
          version: 1,
          documentHash: "7c23f1146d88c221469e34927f8d68f6385a5358052069c90be68d18e95081a1",
          tags: { release: "v2.0.0" },
          createdAt: new Date(Date.now() - 172800000).toISOString(),
        },
      ];
      setVersions(mockList);
    }
  }, [selectedApp, selectedEnv]);

  // Load diff
  const loadDiff = useCallback(async () => {
    if (!v1 || !v2) return;
    setLoading(true);
    setError(null);
    try {
      const data = await vaultApi.diffVersions(selectedApp, selectedEnv, v1, v2);
      setDiffData(data);
    } catch (e: unknown) {
      const err = e as Error;
      setError(err.message);
      setDiffData(DEFAULT_DIFF);
    } finally {
      setLoading(false);
    }
  }, [selectedApp, selectedEnv, v1, v2]);

  useEffect(() => {
    loadVersions();
  }, [loadVersions]);

  useEffect(() => {
    loadDiff();
  }, [loadDiff]);

  const handleSwap = () => {
    const temp = v1;
    setV1(v2);
    setV2(temp);
  };

  return (
    <DashboardShell>
      {/* Editorial Header */}
      <AccessibleFade delay={0}>
        <div className="flex flex-col lg:flex-row lg:items-end justify-between gap-4 pb-4 border-b border-white/10">
          <div>
            <div className="flex items-center gap-2 mb-1">
              <span className="flex h-2 w-2 rounded-full bg-indigo-400" />
              <span className="text-[11px] font-mono uppercase tracking-widest text-indigo-400 font-semibold">
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
                onChange={(e) => setSelectedApp(e.target.value)}
                className="bg-transparent font-mono text-white text-xs font-semibold focus:outline-none cursor-pointer"
              >
                {APPS.map((a) => (
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
                onChange={(e) => setSelectedEnv(e.target.value)}
                className="bg-transparent font-mono text-white text-xs font-semibold focus:outline-none cursor-pointer"
              >
                {ENVS.map((env) => (
                  <option key={env} value={env} className="bg-slate-900 text-white">
                    {env}
                  </option>
                ))}
              </select>
            </div>
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
                onChange={(e) => setV1(Number(e.target.value))}
                className="h-8 rounded-lg border border-indigo-500/30 bg-indigo-950/40 px-3 text-xs font-mono font-bold text-indigo-300 focus:outline-none focus:ring-1 focus:ring-indigo-500 cursor-pointer"
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
              onClick={handleSwap}
              title="Swap comparison order"
              className="flex h-8 w-8 items-center justify-center rounded-lg border border-white/10 bg-white/5 text-slate-400 hover:text-white hover:bg-white/10 transition-colors"
            >
              <ArrowLeftRight className="h-3.5 w-3.5" />
            </button>

            {/* V2 Target */}
            <div className="flex items-center gap-2">
              <span className="text-xs font-mono text-slate-400 font-semibold">Target (v2):</span>
              <select
                value={v2}
                onChange={(e) => setV2(Number(e.target.value))}
                className="h-8 rounded-lg border border-emerald-500/30 bg-emerald-950/40 px-3 text-xs font-mono font-bold text-emerald-300 focus:outline-none focus:ring-1 focus:ring-emerald-500 cursor-pointer"
              >
                {versions.map((ver) => (
                  <option key={ver.version} value={ver.version} className="bg-slate-900 text-white">
                    Revision v{ver.version}
                  </option>
                ))}
              </select>
            </div>
          </div>

          {/* Refresh Action */}
          <button
            type="button"
            onClick={loadDiff}
            className="inline-flex items-center gap-2 rounded-lg border border-white/10 bg-white/5 px-3 py-1.5 text-xs font-mono text-slate-300 hover:bg-white/10 hover:text-white transition-colors"
          >
            <RefreshCw className={`h-3.5 w-3.5 ${loading ? "animate-spin text-indigo-400" : ""}`} />
            <span>Re-compute Delta</span>
          </button>
        </div>

        {error && (
          <div className="mt-2 rounded-lg border border-rose-500/30 bg-rose-950/40 px-3.5 py-2 text-xs font-mono text-rose-300 flex items-center gap-2">
            <AlertCircle className="h-4 w-4 text-rose-400 shrink-0" />
            <span>{error}</span>
          </div>
        )}
      </AccessibleFade>

      {/* Main Split Grid: Left = Visual Timeline, Right = Diff Viewer */}
      <AccessibleFade delay={0.1}>
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 min-h-[600px]">
          {/* Left Column: Visual Git Timeline Path */}
          <div className="lg:col-span-4 rounded-xl border border-white/10 bg-slate-900/60 p-4 h-[600px] overflow-auto">
            <div className="flex items-center gap-2 mb-4 pb-2 border-b border-white/10">
              <History className="h-4 w-4 text-indigo-400" />
              <h3 className="font-['Montserrat',sans-serif] text-xs font-bold uppercase tracking-wider text-white">
                Git Timeline Path
              </h3>
            </div>
            <RevisionTimeline
              versions={versions}
              selectedV1={v1}
              selectedV2={v2}
              onSelectV1={(version) => setV1(version)}
              onSelectV2={(version) => setV2(version)}
            />
          </div>

          {/* Right Column: Colorized Side-by-Side Diff Viewer */}
          <div className="lg:col-span-8 h-[600px]">
            <DiffViewer
              diff={diffData}
              v1={v1}
              v2={v2}
              appName={selectedApp}
              env={selectedEnv}
            />
          </div>
        </div>
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
