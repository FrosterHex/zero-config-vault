"use client";

import React, { useState } from "react";
import {
  Plus,
  Minus,
  Edit2,
  CheckCircle,
  Columns,
  AlignJustify,
  Copy,
  Check,
  Filter,
} from "lucide-react";
import { DiffResponse } from "@/lib/api";

interface DiffViewerProps {
  diff: DiffResponse;
  v1: number;
  v2: number;
  appName?: string;
  env?: string;
}

export function DiffViewer({ diff, v1, v2, appName, env }: DiffViewerProps) {
  const [viewMode, setViewMode] = useState<"split" | "unified">("split");
  const [filterType, setFilterType] = useState<"all" | "added" | "removed" | "modified">("all");
  const [copied, setCopied] = useState(false);

  const addedEntries = Object.entries(diff.added || {});
  const removedEntries = Object.entries(diff.removed || {});
  const modifiedEntries = Object.entries(diff.modified || {});

  const totalChanges = addedEntries.length + removedEntries.length + modifiedEntries.length;

  const handleCopyDiff = () => {
    const patch = JSON.stringify(diff, null, 2);
    navigator.clipboard.writeText(patch);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <div className="flex flex-col h-full rounded-xl border border-white/10 bg-slate-900/60 backdrop-blur-md overflow-hidden">
      {/* Diff Header Bar */}
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-white/10 bg-slate-950/40 px-4 py-3">
        <div className="flex items-center gap-3">
          <div className="flex items-center gap-2">
            <span className="rounded bg-mint-500/20 border border-mint-500/30 px-2 py-0.5 text-xs font-mono font-bold text-mint-400">
              v{v1}
            </span>
            <span className="text-slate-500 text-xs font-mono">→</span>
            <span className="rounded bg-emerald-500/20 border border-emerald-500/30 px-2 py-0.5 text-xs font-mono font-bold text-emerald-400">
              v{v2}
            </span>
          </div>

          {appName && env && (
            <span className="text-xs font-mono text-slate-400 hidden sm:inline">
              ({appName} • {env})
            </span>
          )}

          {/* Quick Metrics Badges */}
          <div className="flex items-center gap-1.5 text-[11px] font-mono">
            <span className="inline-flex items-center gap-1 rounded bg-emerald-950/50 border border-emerald-500/30 px-2 py-0.5 text-emerald-400 font-semibold">
              <Plus className="h-3 w-3" /> {addedEntries.length} added
            </span>
            <span className="inline-flex items-center gap-1 rounded bg-rose-950/50 border border-rose-500/30 px-2 py-0.5 text-rose-400 font-semibold">
              <Minus className="h-3 w-3" /> {removedEntries.length} removed
            </span>
            <span className="inline-flex items-center gap-1 rounded bg-amber-950/50 border border-amber-500/30 px-2 py-0.5 text-amber-300 font-semibold">
              <Edit2 className="h-3 w-3" /> {modifiedEntries.length} modified
            </span>
            <span className="inline-flex items-center gap-1 rounded bg-slate-800/60 border border-white/10 px-2 py-0.5 text-slate-400">
              <CheckCircle className="h-3 w-3" /> {diff.unchangedCount || 0} unchanged
            </span>
          </div>
        </div>

        {/* View Mode & Filter Controls */}
        <div className="flex items-center gap-2">
          {/* Filter Dropdown */}
          <div className="inline-flex items-center gap-1 rounded-lg border border-white/10 bg-black/40 px-2 py-1 text-xs font-mono">
            <Filter className="h-3 w-3 text-slate-400" />
            <select
              value={filterType}
              onChange={(e) => setFilterType(e.target.value as "all" | "added" | "removed" | "modified")}
              className="bg-transparent text-slate-300 text-xs focus:outline-none cursor-pointer"
            >
              <option value="all" className="bg-slate-900 text-white">All Changes</option>
              <option value="added" className="bg-slate-900 text-emerald-400">Added Only</option>
              <option value="removed" className="bg-slate-900 text-rose-400">Removed Only</option>
              <option value="modified" className="bg-slate-900 text-amber-300">Modified Only</option>
            </select>
          </div>

          {/* View Mode Switcher */}
          <div className="inline-flex rounded-lg border border-white/10 bg-black/40 p-0.5 text-xs font-mono">
            <button
              type="button"
              onClick={() => setViewMode("split")}
              className={`inline-flex items-center gap-1 rounded-md px-2 py-1 transition-all ${
                viewMode === "split"
                  ? "bg-mint-600 text-white font-medium"
                  : "text-slate-400 hover:text-white"
              }`}
            >
              <Columns className="h-3.5 w-3.5" />
              <span className="hidden sm:inline">Split</span>
            </button>
            <button
              type="button"
              onClick={() => setViewMode("unified")}
              className={`inline-flex items-center gap-1 rounded-md px-2 py-1 transition-all ${
                viewMode === "unified"
                  ? "bg-mint-600 text-white font-medium"
                  : "text-slate-400 hover:text-white"
              }`}
            >
              <AlignJustify className="h-3.5 w-3.5" />
              <span className="hidden sm:inline">Unified</span>
            </button>
          </div>

          {/* Copy Patch Button */}
          <button
            type="button"
            onClick={handleCopyDiff}
            className="rounded-lg border border-white/10 bg-white/5 p-1.5 text-slate-400 hover:text-white hover:bg-white/10 transition-colors"
            title="Copy diff payload as JSON"
          >
            {copied ? <Check className="h-3.5 w-3.5 text-emerald-400" /> : <Copy className="h-3.5 w-3.5" />}
          </button>
        </div>
      </div>

      {/* Main Diff Content */}
      <div className="flex-1 overflow-auto p-4 font-mono text-xs">
        {totalChanges === 0 ? (
          <div className="flex flex-col items-center justify-center h-48 text-center">
            <CheckCircle className="h-8 w-8 text-emerald-400 mb-2" />
            <h4 className="font-bold text-white text-sm">No Structural Differences</h4>
            <p className="text-slate-400 text-xs mt-1">
              Revisions v{v1} and v{v2} have identical configuration payloads ({diff.unchangedCount} keys match exactly).
            </p>
          </div>
        ) : viewMode === "split" ? (
          /* Split Comparison View */
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {/* Left Side: Revision v1 (Base) */}
            <div className="rounded-lg border border-white/5 bg-slate-950/40 p-3 space-y-2">
              <div className="flex items-center justify-between pb-2 border-b border-white/5 text-[11px] font-semibold text-slate-400 uppercase tracking-wider">
                <span>Base (v{v1})</span>
                <span className="text-rose-400">-{removedEntries.length + modifiedEntries.length} lines</span>
              </div>
              <div className="space-y-1.5">
                {(filterType === "all" || filterType === "removed") &&
                  removedEntries.map(([key, val]) => (
                    <div
                      key={`rem-${key}`}
                      className="rounded bg-rose-950/30 border border-rose-500/20 p-2 text-rose-300"
                    >
                      <div className="flex items-center gap-1.5 font-bold text-rose-400">
                        <Minus className="h-3 w-3" />
                        <span>{key}</span>
                      </div>
                      <pre className="mt-1 text-[11px] text-rose-200/80 overflow-x-auto whitespace-pre-wrap">
                        {JSON.stringify(val, null, 2)}
                      </pre>
                    </div>
                  ))}

                {(filterType === "all" || filterType === "modified") &&
                  modifiedEntries.map(([key, mod]) => (
                    <div
                      key={`mod-old-${key}`}
                      className="rounded bg-amber-950/30 border border-amber-500/20 p-2 text-amber-200"
                    >
                      <div className="flex items-center gap-1.5 font-bold text-amber-400">
                        <Edit2 className="h-3 w-3" />
                        <span>{key}</span>
                        <span className="text-[10px] text-amber-500 uppercase tracking-widest">(Before)</span>
                      </div>
                      <pre className="mt-1 text-[11px] text-amber-200/80 overflow-x-auto whitespace-pre-wrap">
                        {JSON.stringify(mod.oldValue, null, 2)}
                      </pre>
                    </div>
                  ))}
              </div>
            </div>

            {/* Right Side: Revision v2 (Target) */}
            <div className="rounded-lg border border-white/5 bg-slate-950/40 p-3 space-y-2">
              <div className="flex items-center justify-between pb-2 border-b border-white/5 text-[11px] font-semibold text-slate-400 uppercase tracking-wider">
                <span>Target (v{v2})</span>
                <span className="text-emerald-400">+{addedEntries.length + modifiedEntries.length} lines</span>
              </div>
              <div className="space-y-1.5">
                {(filterType === "all" || filterType === "added") &&
                  addedEntries.map(([key, val]) => (
                    <div
                      key={`add-${key}`}
                      className="rounded bg-emerald-950/30 border border-emerald-500/20 p-2 text-emerald-300"
                    >
                      <div className="flex items-center gap-1.5 font-bold text-emerald-400">
                        <Plus className="h-3 w-3" />
                        <span>{key}</span>
                      </div>
                      <pre className="mt-1 text-[11px] text-emerald-200/80 overflow-x-auto whitespace-pre-wrap">
                        {JSON.stringify(val, null, 2)}
                      </pre>
                    </div>
                  ))}

                {(filterType === "all" || filterType === "modified") &&
                  modifiedEntries.map(([key, mod]) => (
                    <div
                      key={`mod-new-${key}`}
                      className="rounded bg-amber-950/30 border border-amber-500/20 p-2 text-amber-200"
                    >
                      <div className="flex items-center gap-1.5 font-bold text-amber-400">
                        <Edit2 className="h-3 w-3" />
                        <span>{key}</span>
                        <span className="text-[10px] text-emerald-400 uppercase tracking-widest">(After)</span>
                      </div>
                      <pre className="mt-1 text-[11px] text-amber-200/80 overflow-x-auto whitespace-pre-wrap">
                        {JSON.stringify(mod.newValue, null, 2)}
                      </pre>
                    </div>
                  ))}
              </div>
            </div>
          </div>
        ) : (
          /* Unified Patch View */
          <div className="space-y-2">
            {(filterType === "all" || filterType === "added") &&
              addedEntries.map(([key, val]) => (
                <div
                  key={`uni-add-${key}`}
                  className="rounded-lg bg-emerald-950/30 border-l-4 border-emerald-500 border border-white/5 p-3 text-emerald-300"
                >
                  <div className="flex items-center gap-2 font-bold text-emerald-400">
                    <Plus className="h-3.5 w-3.5" />
                    <span>{key}</span>
                    <span className="rounded bg-emerald-500/20 px-1.5 py-0.2 text-[9px] uppercase tracking-wider font-semibold">
                      Added in v{v2}
                    </span>
                  </div>
                  <pre className="mt-1.5 text-[11px] text-emerald-200/90 whitespace-pre-wrap overflow-x-auto">
                    {JSON.stringify(val, null, 2)}
                  </pre>
                </div>
              ))}

            {(filterType === "all" || filterType === "removed") &&
              removedEntries.map(([key, val]) => (
                <div
                  key={`uni-rem-${key}`}
                  className="rounded-lg bg-rose-950/30 border-l-4 border-rose-500 border border-white/5 p-3 text-rose-300"
                >
                  <div className="flex items-center gap-2 font-bold text-rose-400">
                    <Minus className="h-3.5 w-3.5" />
                    <span>{key}</span>
                    <span className="rounded bg-rose-500/20 px-1.5 py-0.2 text-[9px] uppercase tracking-wider font-semibold">
                      Removed from v{v1}
                    </span>
                  </div>
                  <pre className="mt-1.5 text-[11px] text-rose-200/90 whitespace-pre-wrap overflow-x-auto">
                    {JSON.stringify(val, null, 2)}
                  </pre>
                </div>
              ))}

            {(filterType === "all" || filterType === "modified") &&
              modifiedEntries.map(([key, mod]) => (
                <div
                  key={`uni-mod-${key}`}
                  className="rounded-lg bg-amber-950/30 border-l-4 border-amber-500 border border-white/5 p-3 text-amber-200"
                >
                  <div className="flex items-center gap-2 font-bold text-amber-300">
                    <Edit2 className="h-3.5 w-3.5" />
                    <span>{key}</span>
                    <span className="rounded bg-amber-500/20 px-1.5 py-0.2 text-[9px] uppercase tracking-wider font-semibold">
                      Modified
                    </span>
                  </div>
                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 mt-2">
                    <div className="rounded bg-black/40 p-2 border border-rose-500/20">
                      <div className="text-[10px] text-rose-400 font-bold uppercase mb-1">Before (v{v1})</div>
                      <pre className="text-[11px] text-rose-200/80 whitespace-pre-wrap overflow-x-auto">
                        {JSON.stringify(mod.oldValue, null, 2)}
                      </pre>
                    </div>
                    <div className="rounded bg-black/40 p-2 border border-emerald-500/20">
                      <div className="text-[10px] text-emerald-400 font-bold uppercase mb-1">After (v{v2})</div>
                      <pre className="text-[11px] text-emerald-200/80 whitespace-pre-wrap overflow-x-auto">
                        {JSON.stringify(mod.newValue, null, 2)}
                      </pre>
                    </div>
                  </div>
                </div>
              ))}
          </div>
        )}
      </div>
    </div>
  );
}
