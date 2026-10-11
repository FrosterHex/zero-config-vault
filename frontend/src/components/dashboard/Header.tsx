"use client";

import React, { useState } from "react";
import { Search, Key, RefreshCw } from "lucide-react";
import { useVault, Environment } from "@/context/VaultContext";

export function Header() {
  const { activeEnv, setActiveEnv, triggerRefresh, setIsSearchOpen } = useVault();
  const [spinning, setSpinning] = useState(false);

  const handleRefreshClick = () => {
    setSpinning(true);
    triggerRefresh();
    setTimeout(() => setSpinning(false), 800);
  };

  const envs: { key: Environment; label: string; activeClass: string }[] = [
    { key: "production", label: "production", activeClass: "bg-indigo-600 text-white shadow-indigo-600/40 shadow-sm" },
    { key: "staging", label: "staging", activeClass: "bg-purple-600 text-white shadow-purple-600/40 shadow-sm" },
    { key: "development", label: "dev", activeClass: "bg-emerald-600 text-white shadow-emerald-600/40 shadow-sm" },
  ];

  return (
    <header className="flex h-16 items-center justify-between border-b border-white/10 bg-[#0B0F19]/80 backdrop-blur-md px-6 z-10 shrink-0">
      {/* Search Input with ⌘K prompt */}
      <div className="flex items-center gap-3 w-80 sm:w-96">
        <button
          type="button"
          onClick={() => setIsSearchOpen(true)}
          className="relative w-full text-left group"
        >
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-slate-500 group-hover:text-indigo-400 transition-colors" />
          <div className="w-full rounded-lg border border-white/10 bg-white/5 pl-9 pr-12 py-1.5 text-xs text-slate-400 group-hover:border-indigo-500/40 group-hover:text-slate-200 transition-all font-mono truncate">
            Search apps, keys, dot paths (e.g. database.host)...
          </div>
          <kbd className="absolute right-2.5 top-1/2 -translate-y-1/2 rounded border border-white/10 bg-white/10 px-1.5 py-0.5 text-[10px] font-mono text-slate-400 group-hover:border-indigo-500/30">
            ⌘K
          </kbd>
        </button>
      </div>

      {/* Right Controls */}
      <div className="flex items-center gap-3 sm:gap-4">
        {/* Environment Pill Switcher */}
        <div className="flex items-center rounded-lg border border-white/10 bg-white/5 p-1 text-[11px] font-mono font-medium">
          {envs.map((env) => (
            <button
              key={env.key}
              type="button"
              onClick={() => setActiveEnv(env.key)}
              className={`rounded px-2.5 py-0.5 transition-all ${
                activeEnv === env.key
                  ? env.activeClass
                  : "text-slate-400 hover:text-white"
              }`}
            >
              {env.label}
            </button>
          ))}
        </div>

        {/* API Key Status */}
        <div className="hidden sm:flex items-center gap-1.5 rounded-lg border border-white/10 bg-black/30 px-3 py-1.5 text-xs text-slate-300 font-mono">
          <Key className="h-3.5 w-3.5 text-indigo-400" />
          <span className="text-slate-500 text-[11px]">KEY:</span>
          <span className="text-slate-300">sk-dev-***-001</span>
        </div>

        {/* Sync Indicator / Refresh Button */}
        <button
          type="button"
          onClick={handleRefreshClick}
          className="flex h-8 w-8 items-center justify-center rounded-lg border border-white/10 bg-white/5 text-slate-400 hover:text-white hover:bg-white/10 transition-colors"
          title="Refresh Vault Telemetry & Live Data"
        >
          <RefreshCw className={`h-3.5 w-3.5 ${spinning ? "animate-spin text-indigo-400" : ""}`} />
        </button>
      </div>
    </header>
  );
}
