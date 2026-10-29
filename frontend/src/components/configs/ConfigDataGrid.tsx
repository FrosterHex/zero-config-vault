"use client";

import React, { useState } from "react";
import {
  History,
  RotateCcw,
  GitCompare,
  Eye,
  Tag,
  Search,
  Calendar,
} from "lucide-react";
import { IntegrityBadge } from "@/components/integrity-badge";
import { VersionSummary } from "@/lib/api";

interface ConfigDataGridProps {
  versions: VersionSummary[];
  activeVersion: number;
  onSelectVersion: (version: number) => void;
  onRollback: (version: number) => void;
  onDiffWithLatest: (version: number) => void;
  /** When set, rollback buttons stay visible but disabled, with this text as their explanation. */
  rollbackDisabledReason?: string;
}

export function ConfigDataGrid({
  versions,
  activeVersion,
  onSelectVersion,
  onRollback,
  onDiffWithLatest,
  rollbackDisabledReason,
}: ConfigDataGridProps) {
  const [filterText, setFilterText] = useState("");

  const filteredVersions = versions.filter((v) => {
    if (!filterText) return true;
    const q = filterText.toLowerCase();
    if (String(v.version).includes(q)) return true;
    if (v.documentHash.toLowerCase().includes(q)) return true;
    if (
      v.tags &&
      Object.entries(v.tags).some(
        ([k, val]) => k.toLowerCase().includes(q) || val.toLowerCase().includes(q)
      )
    ) {
      return true;
    }
    return false;
  });

  const latestVersion = versions.length > 0 ? versions[0].version : 1;

  return (
    <div className="flex flex-col h-full rounded-xl border border-white/10 bg-slate-900/60 backdrop-blur-md overflow-hidden">
      {/* Table Toolbar */}
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-white/10 bg-slate-950/40 px-4 py-3">
        <div className="flex items-center gap-2">
          <History className="h-4 w-4 text-indigo-400" />
          <h3 className="font-['Montserrat',sans-serif] text-xs font-semibold uppercase tracking-wider text-white">
            Immutable Revision Ledger
          </h3>
          <span className="rounded-full bg-white/10 px-2 py-0.5 text-[10px] font-mono text-slate-300">
            {versions.length} revisions
          </span>
        </div>

        {/* Filter Input */}
        <div className="relative">
          <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-slate-400" />
          <input
            type="text"
            placeholder="Search version, hash, or tags..."
            value={filterText}
            onChange={(e) => setFilterText(e.target.value)}
            className="h-7 w-48 sm:w-64 rounded-md border border-white/10 bg-black/40 pl-8 pr-3 text-xs text-white placeholder-slate-500 font-mono focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500"
          />
        </div>
      </div>

      {/* Revisions Table */}
      <div className="flex-1 overflow-auto">
        <table className="w-full border-collapse text-left text-xs font-mono">
          <thead>
            <tr className="border-b border-white/10 bg-white/5 text-[11px] uppercase tracking-wider text-slate-400">
              <th className="py-2.5 px-4 font-semibold">Version</th>
              <th className="py-2.5 px-4 font-semibold">Integrity Hash</th>
              <th className="py-2.5 px-4 font-semibold hidden sm:table-cell">Metadata Tags</th>
              <th className="py-2.5 px-4 font-semibold hidden md:table-cell">Timestamp</th>
              <th className="py-2.5 px-4 font-semibold text-right">Actions</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-white/5">
            {filteredVersions.length === 0 ? (
              <tr>
                <td colSpan={5} className="py-8 text-center text-slate-500 font-sans">
                  No matching revisions found.
                </td>
              </tr>
            ) : (
              filteredVersions.map((v) => {
                const isSelected = v.version === activeVersion;
                const isLatest = v.version === latestVersion;

                return (
                  <tr
                    key={v.version}
                    onClick={() => onSelectVersion(v.version)}
                    className={`cursor-pointer transition-colors ${
                      isSelected
                        ? "bg-indigo-950/40 hover:bg-indigo-950/60"
                        : "hover:bg-white/5"
                    }`}
                  >
                    {/* Version Column */}
                    <td className="py-3 px-4">
                      <div className="flex items-center gap-2">
                        <span
                          className={`flex h-6 w-6 items-center justify-center rounded font-bold ${
                            isLatest
                              ? "bg-emerald-500/20 text-emerald-400 border border-emerald-500/30"
                              : isSelected
                              ? "bg-indigo-500/20 text-indigo-400 border border-indigo-500/30"
                              : "bg-white/10 text-slate-300"
                          }`}
                        >
                          v{v.version}
                        </span>
                        {isLatest && (
                          <span className="rounded bg-emerald-950/60 border border-emerald-500/30 px-1.5 py-0.5 text-[9px] font-semibold uppercase tracking-wider text-emerald-400">
                            Current
                          </span>
                        )}
                      </div>
                    </td>

                    {/* Hash Badge Column */}
                    <td className="py-3 px-4">
                      <IntegrityBadge hash={v.documentHash} />
                    </td>

                    {/* Tags Column */}
                    <td className="py-3 px-4 hidden sm:table-cell">
                      <div className="flex flex-wrap gap-1">
                        {v.tags && Object.keys(v.tags).length > 0 ? (
                          Object.entries(v.tags).map(([key, val]) => (
                            <span
                              key={key}
                              className="inline-flex items-center gap-1 rounded bg-slate-800/80 px-2 py-0.5 text-[10px] text-slate-300 border border-white/5"
                            >
                              <Tag className="h-2.5 w-2.5 text-indigo-400" />
                              <span>{key}:{val}</span>
                            </span>
                          ))
                        ) : (
                          <span className="text-slate-600 text-[11px]">—</span>
                        )}
                      </div>
                    </td>

                    {/* Timestamp Column */}
                    <td className="py-3 px-4 hidden md:table-cell text-slate-400 text-[11px]">
                      <div className="flex items-center gap-1.5">
                        <Calendar className="h-3 w-3 text-slate-500" />
                        <span>{new Date(v.createdAt).toLocaleString()}</span>
                      </div>
                    </td>

                    {/* Actions Column */}
                    <td className="py-3 px-4 text-right">
                      <div className="inline-flex items-center gap-1.5">
                        <button
                          type="button"
                          onClick={(e) => {
                            e.stopPropagation();
                            onSelectVersion(v.version);
                          }}
                          title="Inspect payload in editor"
                          className="rounded p-1 text-slate-400 hover:bg-white/10 hover:text-white transition-colors"
                        >
                          <Eye className="h-3.5 w-3.5" />
                        </button>

                        {!isLatest && (
                          <>
                            <button
                              type="button"
                              onClick={(e) => {
                                e.stopPropagation();
                                onDiffWithLatest(v.version);
                              }}
                              title="Compare diff with latest revision"
                              className="rounded p-1 text-slate-400 hover:bg-white/10 hover:text-indigo-400 transition-colors"
                            >
                              <GitCompare className="h-3.5 w-3.5" />
                            </button>

                            <button
                              type="button"
                              onClick={(e) => {
                                e.stopPropagation();
                                if (
                                  confirm(
                                    `Are you sure you want to rollback to v${v.version}? This creates a new revision with v${v.version}'s state.`
                                  )
                                ) {
                                  onRollback(v.version);
                                }
                              }}
                              disabled={!!rollbackDisabledReason}
                              title={rollbackDisabledReason ?? "Rollback to this version (creates new GitOps revision)"}
                              className="rounded p-1 text-amber-400 hover:bg-amber-950/40 hover:text-amber-300 transition-colors disabled:cursor-not-allowed disabled:opacity-40 disabled:hover:bg-transparent disabled:hover:text-amber-400"
                            >
                              <RotateCcw className="h-3.5 w-3.5" />
                            </button>
                          </>
                        )}
                      </div>
                    </td>
                  </tr>
                );
              })
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
