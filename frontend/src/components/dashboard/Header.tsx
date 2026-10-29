"use client";

import React, { useState } from "react";
import { Search, RefreshCw, LogIn, LogOut, Loader2 } from "lucide-react";
import { useVault, Environment } from "@/context/VaultContext";
import { authApi } from "@/lib/api";
import { RoleBadge } from "@/components/auth/RoleBadge";
import { LoginDialog } from "@/components/auth/LoginDialog";

export function Header() {
  const { activeEnv, setActiveEnv, triggerRefresh, setIsSearchOpen, role, refreshRole, isWaking } = useVault();
  const [spinning, setSpinning] = useState(false);
  const [loginOpen, setLoginOpen] = useState(false);
  const [loggingOut, setLoggingOut] = useState(false);

  const handleLogout = async () => {
    setLoggingOut(true);
    try {
      await authApi.logout();
    } catch (err) {
      console.warn("Logout failed", err);
    } finally {
      await refreshRole();
      triggerRefresh();
      setLoggingOut(false);
    }
  };

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
        {/* Hosted vault cold-start indicator */}
        {isWaking && (
          <span
            role="status"
            className="hidden md:inline-flex items-center gap-1.5 rounded-full border border-sky-500/30 bg-sky-500/10 px-2.5 py-1 text-[11px] font-mono text-sky-300"
          >
            <Loader2 className="h-3 w-3 animate-spin" aria-hidden="true" />
            <span>Waking up the vault…</span>
          </span>
        )}

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

        {/* Sync Indicator / Refresh Button */}
        <button
          type="button"
          onClick={handleRefreshClick}
          className="flex h-8 w-8 items-center justify-center rounded-lg border border-white/10 bg-white/5 text-slate-400 hover:text-white hover:bg-white/10 transition-colors"
          title="Refresh Vault Telemetry & Live Data"
        >
          <RefreshCw className={`h-3.5 w-3.5 ${spinning ? "animate-spin text-indigo-400" : ""}`} />
        </button>

        {/* Role and sign-in */}
        <div className="flex items-center gap-2 border-l border-white/10 pl-3 sm:pl-4">
          <RoleBadge role={role} />
          {role === "admin" ? (
            <button
              type="button"
              onClick={handleLogout}
              disabled={loggingOut}
              className="inline-flex h-8 items-center gap-1.5 rounded-lg border border-white/10 bg-white/5 px-2.5 text-xs font-medium text-slate-300 transition-colors hover:bg-white/10 hover:text-white focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 disabled:cursor-not-allowed disabled:opacity-50"
            >
              {loggingOut ? <Loader2 className="h-3.5 w-3.5 animate-spin" aria-hidden="true" /> : <LogOut className="h-3.5 w-3.5" aria-hidden="true" />}
              <span>Log out</span>
            </button>
          ) : (
            <button
              type="button"
              onClick={() => setLoginOpen(true)}
              aria-haspopup="dialog"
              className="inline-flex h-8 items-center gap-1.5 rounded-lg bg-indigo-600 px-3 text-xs font-semibold text-white shadow-sm shadow-indigo-600/30 transition-colors hover:bg-indigo-500 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-400"
            >
              <LogIn className="h-3.5 w-3.5" aria-hidden="true" />
              <span>Admin login</span>
            </button>
          )}
        </div>
      </div>

      <LoginDialog open={loginOpen} onClose={() => setLoginOpen(false)} />
    </header>
  );
}
