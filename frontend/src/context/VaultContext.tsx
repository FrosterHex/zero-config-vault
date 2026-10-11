"use client";

import React, { createContext, useContext, useState, useEffect, useCallback } from "react";
import { vaultApi, StatsResponse } from "@/lib/api";

export type Environment = "production" | "staging" | "development";

interface VaultContextType {
  activeEnv: Environment;
  setActiveEnv: (env: Environment) => void;
  activeApp: string;
  setActiveApp: (app: string) => void;
  refreshKey: number;
  triggerRefresh: () => void;
  isSearchOpen: boolean;
  setIsSearchOpen: (open: boolean) => void;
  apps: string[];
  envs: Environment[];
  stats: StatsResponse | null;
  loadingStats: boolean;
}

const DEFAULT_APPS = ["payment-gateway", "order-service", "auth-service", "inventory-service"];
const DEFAULT_ENVS: Environment[] = ["production", "staging", "development"];

const VaultContext = createContext<VaultContextType | undefined>(undefined);

export function VaultProvider({ children }: { children: React.ReactNode }) {
  const [activeEnv, setActiveEnvState] = useState<Environment>("production");
  const [activeApp, setActiveAppState] = useState<string>("payment-gateway");
  const [refreshKey, setRefreshKey] = useState<number>(0);
  const [isSearchOpen, setIsSearchOpen] = useState<boolean>(false);
  const [apps, setApps] = useState<string[]>(DEFAULT_APPS);
  const [stats, setStats] = useState<StatsResponse | null>(null);
  const [loadingStats, setLoadingStats] = useState<boolean>(false);

  // Load environment from localStorage if previously selected
  useEffect(() => {
    if (typeof window !== "undefined") {
      const savedEnv = localStorage.getItem("vault_active_env") as Environment | null;
      if (savedEnv && DEFAULT_ENVS.includes(savedEnv)) {
        setActiveEnvState(savedEnv);
      }
      const savedApp = localStorage.getItem("vault_active_app");
      if (savedApp) {
        setActiveAppState(savedApp);
      }
    }
  }, []);

  const setActiveEnv = (env: Environment) => {
    setActiveEnvState(env);
    if (typeof window !== "undefined") {
      localStorage.setItem("vault_active_env", env);
    }
  };

  const setActiveApp = (app: string) => {
    setActiveAppState(app);
    if (typeof window !== "undefined") {
      localStorage.setItem("vault_active_app", app);
    }
  };

  const triggerRefresh = useCallback(() => {
    setRefreshKey((k) => k + 1);
  }, []);

  // Fetch live stats & discover apps
  useEffect(() => {
    let isMounted = true;
    setLoadingStats(true);
    vaultApi
      .getStats()
      .then((data) => {
        if (!isMounted) return;
        setStats(data);
        if (data.versionCounts) {
          const discovered = Object.keys(data.versionCounts)
            .map((k) => k.split(":")[0])
            .filter((v, i, a) => a.indexOf(v) === i);
          const combined = Array.from(new Set([...DEFAULT_APPS, ...discovered]));
          setApps(combined);
        }
      })
      .catch((err) => {
        console.warn("Could not load stats for app discovery", err);
      })
      .finally(() => {
        if (isMounted) setLoadingStats(false);
      });

    return () => {
      isMounted = false;
    };
  }, [refreshKey]);

  return (
    <VaultContext.Provider
      value={{
        activeEnv,
        setActiveEnv,
        activeApp,
        setActiveApp,
        refreshKey,
        triggerRefresh,
        isSearchOpen,
        setIsSearchOpen,
        apps,
        envs: DEFAULT_ENVS,
        stats,
        loadingStats,
      }}
    >
      {children}
    </VaultContext.Provider>
  );
}

export function useVault() {
  const context = useContext(VaultContext);
  if (!context) {
    throw new Error("useVault must be used within a VaultProvider");
  }
  return context;
}
