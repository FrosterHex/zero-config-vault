"use client";

import React, { useState, useEffect, useRef } from "react";
import { useRouter } from "next/navigation";
import { useVault } from "@/context/VaultContext";
import { vaultApi } from "@/lib/api";
import {
  Search,
  X,
  Layers,
  GitCompare,
  HardDrive,
  FileCode,
  Radio,
  KeyRound,
  ArrowRight,
  Database,
  Sparkles,
} from "lucide-react";

export function CommandPalette() {
  const router = useRouter();
  const { isSearchOpen, setIsSearchOpen, apps, activeEnv, setActiveApp } = useVault();
  const [query, setQuery] = useState("");
  const [pathResult, setPathResult] = useState<{ path: string; value: string } | null>(null);
  const [searchingPath, setSearchingPath] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);

  // Keyboard shortcut listener: Cmd+K / Ctrl+K & Escape
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === "k") {
        e.preventDefault();
        setIsSearchOpen(!isSearchOpen);
      } else if (e.key === "Escape" && isSearchOpen) {
        setIsSearchOpen(false);
      }
    };

    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [isSearchOpen, setIsSearchOpen]);

  // Focus input when opened
  useEffect(() => {
    if (isSearchOpen) {
      setTimeout(() => inputRef.current?.focus(), 50);
      setQuery("");
      setPathResult(null);
    }
  }, [isSearchOpen]);

  // Handle dot-notation lookup when query contains a dot
  useEffect(() => {
    if (!query.includes(".")) {
      setPathResult(null);
      return;
    }

    const timer = setTimeout(async () => {
      setSearchingPath(true);
      try {
        const app = apps[0] || "payment-gateway";
        const res = await vaultApi.searchByPath(app, activeEnv, query.trim());
        setPathResult({ path: query.trim(), value: JSON.stringify(res.value) });
      } catch {
        setPathResult(null);
      } finally {
        setSearchingPath(false);
      }
    }, 350);

    return () => clearTimeout(timer);
  }, [query, apps, activeEnv]);

  if (!isSearchOpen) return null;

  const q = query.toLowerCase().trim();

  // Navigation modules
  const modules = [
    { title: "Configuration Studio", href: "/configs", icon: Layers, desc: "JSON tree editor, encryption badges, temporal rollback" },
    { title: "Delta Studio & Diff Engine", href: "/diff", icon: GitCompare, desc: "Compare any two revisions with colorized additions & removals" },
    { title: "GridFS Binary Keystores", href: "/binaries", icon: HardDrive, desc: "Upload and download TLS certs, PEM, and .jks files" },
    { title: "Export Studio", href: "/export", icon: FileCode, desc: "Export live configs into YAML, JSON, or .env format" },
    { title: "Capped Audit Log Stream", href: "/audit", icon: Radio, desc: "Real-time circular oplog change stream buffer" },
    { title: "Scoped API Key Matrix", href: "/access", icon: KeyRound, desc: "Role-Based Access Control and microservice tokens" },
  ];

  const filteredModules = modules.filter(
    (m) => m.title.toLowerCase().includes(q) || m.desc.toLowerCase().includes(q)
  );

  const filteredApps = apps.filter((app) => app.toLowerCase().includes(q));

  const handleSelectModule = (href: string) => {
    setIsSearchOpen(false);
    router.push(href);
  };

  const handleSelectApp = (app: string) => {
    setActiveApp(app);
    setIsSearchOpen(false);
    router.push(`/configs?app=${encodeURIComponent(app)}&env=${encodeURIComponent(activeEnv)}`);
  };

  return (
    <div className="fixed inset-0 z-50 flex items-start justify-center pt-20 px-4 bg-black/70 backdrop-blur-md animate-fadeIn">
      <div
        className="relative w-full max-w-2xl rounded-2xl border border-indigo-500/30 bg-[#0B0F19]/95 p-4 shadow-2xl shadow-indigo-950/50 backdrop-blur-2xl overflow-hidden"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Glow orb */}
        <div className="pointer-events-none absolute -top-20 -right-20 h-56 w-56 rounded-full bg-indigo-500/20 blur-[80px]" />

        {/* Search Input Bar */}
        <div className="relative flex items-center border-b border-white/10 pb-3">
          <Search className="h-5 w-5 text-indigo-400 ml-2" />
          <input
            ref={inputRef}
            type="text"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Search applications, modules, or dot paths (e.g. database.host, secrets.stripe_secret)..."
            className="w-full bg-transparent px-3 text-sm text-white placeholder-slate-500 focus:outline-none font-mono"
          />
          {query ? (
            <button
              onClick={() => setQuery("")}
              className="text-slate-500 hover:text-white p-1 rounded transition-colors"
            >
              <X className="h-4 w-4" />
            </button>
          ) : (
            <kbd className="rounded border border-white/10 bg-white/10 px-2 py-0.5 text-[10px] font-mono text-slate-400">
              ESC
            </kbd>
          )}
        </div>

        {/* Results Area */}
        <div className="mt-3 max-h-96 overflow-y-auto space-y-4 pr-1 text-xs">
          {/* Dot-Notation Path Live Resolution Result */}
          {query.includes(".") && (
            <div className="rounded-xl border border-indigo-500/30 bg-indigo-950/40 p-3">
              <div className="flex items-center justify-between mb-1.5">
                <span className="flex items-center gap-1.5 font-mono text-[11px] font-bold text-indigo-300">
                  <Sparkles className="h-3.5 w-3.5 text-indigo-400 animate-spin" />
                  Dot-Notation Path Traversal
                </span>
                <span className="text-[10px] font-mono text-slate-400">
                  Scope: {apps[0] || "payment-gateway"} [{activeEnv}]
                </span>
              </div>
              {searchingPath ? (
                <p className="font-mono text-slate-400 text-[11px]">Traversing MongoDB BSON tree...</p>
              ) : pathResult ? (
                <div className="rounded bg-black/50 p-2 font-mono text-emerald-400 text-xs break-all border border-emerald-500/20">
                  <span className="text-slate-400">{pathResult.path} = </span>
                  <span className="font-bold">{pathResult.value}</span>
                </div>
              ) : (
                <p className="font-mono text-amber-400/80 text-[11px]">
                  Path &quot;{query}&quot; not found in active document root.
                </p>
              )}
            </div>
          )}

          {/* Applications List */}
          {filteredApps.length > 0 && (
            <div>
              <div className="text-[10px] font-mono uppercase tracking-wider text-slate-500 px-2 mb-1.5">
                Managed Applications
              </div>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-1.5">
                {filteredApps.map((app) => (
                  <button
                    key={app}
                    onClick={() => handleSelectApp(app)}
                    className="flex items-center justify-between rounded-lg border border-white/5 bg-white/5 hover:bg-indigo-600/20 hover:border-indigo-500/30 p-2.5 text-left transition-all group"
                  >
                    <div className="flex items-center gap-2">
                      <Database className="h-4 w-4 text-indigo-400" />
                      <span className="font-mono font-medium text-white group-hover:text-indigo-300">
                        {app}
                      </span>
                    </div>
                    <span className="text-[10px] font-mono text-slate-500 group-hover:text-indigo-400 flex items-center gap-1">
                      Inspect <ArrowRight className="h-3 w-3" />
                    </span>
                  </button>
                ))}
              </div>
            </div>
          )}

          {/* Module Navigation */}
          {filteredModules.length > 0 && (
            <div>
              <div className="text-[10px] font-mono uppercase tracking-wider text-slate-500 px-2 mb-1.5">
                Vault Platform Modules
              </div>
              <div className="space-y-1">
                {filteredModules.map((m) => {
                  const Icon = m.icon;
                  return (
                    <button
                      key={m.href}
                      onClick={() => handleSelectModule(m.href)}
                      className="w-full flex items-center justify-between rounded-lg border border-white/5 bg-white/5 hover:bg-indigo-600/20 hover:border-indigo-500/30 p-2.5 text-left transition-all group"
                    >
                      <div className="flex items-center gap-3">
                        <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-indigo-500/10 text-indigo-400 border border-indigo-500/20">
                          <Icon className="h-4 w-4" />
                        </div>
                        <div>
                          <div className="font-semibold text-white group-hover:text-indigo-300">
                            {m.title}
                          </div>
                          <div className="text-[11px] text-slate-400 font-light">{m.desc}</div>
                        </div>
                      </div>
                      <ArrowRight className="h-4 w-4 text-slate-500 group-hover:text-white group-hover:translate-x-1 transition-all" />
                    </button>
                  );
                })}
              </div>
            </div>
          )}

          {filteredApps.length === 0 && filteredModules.length === 0 && !pathResult && (
            <div className="p-8 text-center text-slate-500 font-mono">
              No matching applications, modules, or paths found for &quot;{query}&quot;.
            </div>
          )}
        </div>

        {/* Footer shortcuts */}
        <div className="mt-4 pt-3 border-t border-white/10 flex items-center justify-between text-[10px] font-mono text-slate-500">
          <div className="flex items-center gap-3">
            <span><kbd className="text-slate-400 border border-white/10 px-1 rounded">↵</kbd> Select</span>
            <span><kbd className="text-slate-400 border border-white/10 px-1 rounded">ESC</kbd> Close</span>
          </div>
          <span className="text-indigo-400 font-medium">Zero-Config Vault OS • Global Search</span>
        </div>
      </div>
    </div>
  );
}
