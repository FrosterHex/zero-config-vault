import React from "react";
import { GitCommit, Calendar, Tag } from "lucide-react";
import { IntegrityBadge } from "@/components/integrity-badge";
import { VersionSummary } from "@/lib/api";

interface RevisionTimelineProps {
  versions: VersionSummary[];
  selectedV1: number;
  selectedV2: number;
  onSelectV1: (v: number) => void;
  onSelectV2: (v: number) => void;
}

export function RevisionTimeline({
  versions,
  selectedV1,
  selectedV2,
  onSelectV1,
  onSelectV2,
}: RevisionTimelineProps) {
  return (
    <div className="relative pl-6 space-y-6 before:absolute before:left-2.5 before:top-3 before:bottom-3 before:w-0.5 before:bg-gradient-to-b before:from-indigo-500 before:via-purple-500 before:to-slate-800">
      {versions.map((ver, idx) => {
        const isV1 = ver.version === selectedV1;
        const isV2 = ver.version === selectedV2;
        const isLatest = idx === 0;

        return (
          <div key={ver.version} className="relative group">
            {/* Visual Git Node on the Spine */}
            <div
              className={`absolute -left-6 top-1 flex h-5 w-5 items-center justify-center rounded-full border-2 transition-all ${
                isLatest
                  ? "border-emerald-400 bg-emerald-950 text-emerald-400 shadow-sm shadow-emerald-400/50"
                  : isV1 || isV2
                  ? "border-indigo-400 bg-indigo-950 text-indigo-400 shadow-sm shadow-indigo-400/50"
                  : "border-slate-600 bg-slate-900 text-slate-400 group-hover:border-white group-hover:text-white"
              }`}
            >
              <GitCommit className="h-3 w-3" />
            </div>

            {/* Commit Card Container */}
            <div
              className={`rounded-xl border p-3.5 transition-all backdrop-blur-sm ${
                isV1 && isV2
                  ? "border-purple-500/50 bg-purple-950/20 shadow-lg shadow-purple-500/10"
                  : isV1
                  ? "border-indigo-500/50 bg-indigo-950/20 shadow-lg shadow-indigo-500/10"
                  : isV2
                  ? "border-emerald-500/50 bg-emerald-950/20 shadow-lg shadow-emerald-500/10"
                  : "border-white/10 bg-slate-900/40 hover:border-white/20 hover:bg-slate-900/60"
              }`}
            >
              <div className="flex flex-wrap items-center justify-between gap-2 mb-2">
                <div className="flex items-center gap-2">
                  <span className="font-mono text-xs font-bold text-white">
                    Revision v{ver.version}
                  </span>
                  {isLatest && (
                    <span className="rounded bg-emerald-500/20 border border-emerald-500/30 px-1.5 py-0.2 text-[9px] font-mono font-semibold uppercase tracking-wider text-emerald-400">
                      Head / Current
                    </span>
                  )}
                  {isV1 && (
                    <span className="rounded bg-indigo-500/20 border border-indigo-500/30 px-1.5 py-0.2 text-[9px] font-mono font-semibold uppercase tracking-wider text-indigo-400">
                      Base (V1)
                    </span>
                  )}
                  {isV2 && (
                    <span className="rounded bg-emerald-500/20 border border-emerald-500/30 px-1.5 py-0.2 text-[9px] font-mono font-semibold uppercase tracking-wider text-emerald-400">
                      Target (V2)
                    </span>
                  )}
                </div>

                <div className="flex items-center gap-1.5 text-[11px] font-mono text-slate-400">
                  <Calendar className="h-3 w-3 text-slate-500" />
                  <span>{new Date(ver.createdAt).toLocaleString()}</span>
                </div>
              </div>

              {/* Hash and Tags */}
              <div className="flex flex-wrap items-center gap-2 mb-3">
                <IntegrityBadge hash={ver.documentHash} />

                {ver.tags &&
                  Object.entries(ver.tags).map(([k, val]) => (
                    <span
                      key={k}
                      className="inline-flex items-center gap-1 rounded bg-slate-800/60 border border-white/5 px-2 py-0.5 text-[10px] font-mono text-slate-300"
                    >
                      <Tag className="h-2.5 w-2.5 text-indigo-400" />
                      <span>{k}:{val}</span>
                    </span>
                  ))}
              </div>

              {/* Comparison Picker Buttons */}
              <div className="flex items-center gap-2 pt-2 border-t border-white/5 text-[11px] font-mono">
                <button
                  type="button"
                  onClick={() => onSelectV1(ver.version)}
                  className={`rounded px-2.5 py-1 transition-all ${
                    isV1
                      ? "bg-indigo-600 text-white font-bold"
                      : "bg-white/5 text-slate-400 hover:text-white hover:bg-white/10"
                  }`}
                >
                  Set as Base (V1)
                </button>
                <button
                  type="button"
                  onClick={() => onSelectV2(ver.version)}
                  className={`rounded px-2.5 py-1 transition-all ${
                    isV2
                      ? "bg-emerald-600 text-white font-bold"
                      : "bg-white/5 text-slate-400 hover:text-white hover:bg-white/10"
                  }`}
                >
                  Set as Target (V2)
                </button>
              </div>
            </div>
          </div>
        );
      })}
    </div>
  );
}
