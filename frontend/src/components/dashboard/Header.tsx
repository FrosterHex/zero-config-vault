"use client";

import React from "react";
import { Search, Key, RefreshCw } from "lucide-react";

export function Header() {
  return (
    <header className="flex h-16 items-center justify-between border-b border-white/10 bg-[#0B0F19]/80 backdrop-blur-md px-6 z-10">
      {/* Search Input with ⌘K prompt */}
      <div className="flex items-center gap-3 w-96">
        <div className="relative w-full">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-slate-500" />
          <input
            type="text"
            placeholder="Search apps, keys, dot-notation paths (e.g. database.host)..."
            className="w-full rounded-lg border border-white/10 bg-white/5 pl-9 pr-12 py-1.5 text-xs text-slate-200 placeholder:text-slate-500 focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500"
          />
          <kbd className="absolute right-2.5 top-1/2 -translate-y-1/2 rounded border border-white/10 bg-white/10 px-1.5 py-0.5 text-[10px] font-mono text-slate-400">
            ⌘K
          </kbd>
        </div>
      </div>

      {/* Right Controls */}
      <div className="flex items-center gap-4">
        {/* Environment Pill */}
        <div className="flex items-center rounded-lg border border-white/10 bg-white/5 p-1 text-[11px] font-mono font-medium">
          <span className="rounded bg-indigo-600 px-2.5 py-0.5 text-white">production</span>
          <span className="px-2.5 py-0.5 text-slate-400 hover:text-white cursor-pointer">staging</span>
          <span className="px-2.5 py-0.5 text-slate-400 hover:text-white cursor-pointer">dev</span>
        </div>

        {/* API Key Status */}
        <div className="flex items-center gap-1.5 rounded-lg border border-white/10 bg-black/30 px-3 py-1.5 text-xs text-slate-300 font-mono">
          <Key className="h-3.5 w-3.5 text-indigo-400" />
          <span className="text-slate-500 text-[11px]">KEY:</span>
          <span className="text-slate-300">sk-dev-***-001</span>
        </div>

        {/* Sync Indicator */}
        <button
          className="flex h-8 w-8 items-center justify-center rounded-lg border border-white/10 bg-white/5 text-slate-400 hover:text-white hover:bg-white/10 transition-colors"
          title="Refresh Vault Telemetry"
        >
          <RefreshCw className="h-3.5 w-3.5" />
        </button>
      </div>
    </header>
  );
}
