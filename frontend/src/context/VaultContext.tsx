"use client";

import React, { createContext, useContext, useState, useEffect, useCallback, useRef } from "react";
import { vaultApi, authApi, onWakingChange, StatsResponse, Role } from "@/lib/api";

export type Environment = "production" | "staging" | "development";
export type VaultStatus = "connecting" | "connected" | "unreachable";

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
  /** Whether the last statistics request reached the vault; drives the connection indicators. */
  vaultStatus: VaultStatus;
  role: Role;
  /** False until the first session check finishes; until then `role` is only the "guest" default. */
  roleChecked: boolean;
  refreshRole: () => Promise<void>;
  isWaking: boolean;
  canWrite: (app: string) => boolean;
}

const DEFAULT_APPS = ["playground", "payment-gateway", "order-service", "auth-service", "inventory-service"];
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
  const [vaultStatus, setVaultStatus] = useState<VaultStatus>("connecting");
  const [role, setRole] = useState<Role>("guest");
  const [roleChecked, setRoleChecked] = useState<boolean>(false);
  const [isWaking, setIsWaking] = useState<boolean>(false);

  const refreshRole = useCallback(async () => {
    try {
      setRole(await authApi.session());
    } catch {
      setRole("guest");
    } finally {
      setRoleChecked(true);
    }
  }, []);

  const canWrite = useCallback((app: string) => role === "admin" || app === "playground", [role]);

  useEffect(() => onWakingChange(setIsWaking), []);

  useEffect(() => {
    void refreshRole();
  }, [refreshRole]);

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
        setVaultStatus("connected");
        if (data.versionCounts) {
          const discovered = Object.keys(data.versionCounts)
            .map((k) => k.split(":")[0])
            .filter((v, i, a) => a.indexOf(v) === i);
          const combined = Array.from(new Set([...DEFAULT_APPS, ...discovered, "playground"]));
          setApps(combined);
        }
      })
      .catch((err) => {
        console.warn("Could not load stats for app discovery", err);
        if (isMounted) setVaultStatus("unreachable");
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
        vaultStatus,
        role,
        roleChecked,
        refreshRole,
        isWaking,
        canWrite,
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

export function isEnvironment(value: string | null | undefined): value is Environment {
  return !!value && (DEFAULT_ENVS as string[]).includes(value);
}

/**
 * The app/environment a page works on. A link's ?app=&env= (search results, "diff with latest")
 * takes precedence and becomes the global selection; afterwards the page follows changes to the
 * global selection, such as the header's environment switcher.
 */
export function useSelectedScope(urlApp: string | null, urlEnv: string | null) {
  const { activeApp, setActiveApp, activeEnv, setActiveEnv } = useVault();
  const initialEnv = isEnvironment(urlEnv) ? urlEnv : null;
  const [selectedApp, setSelectedApp] = useState(() => urlApp || activeApp);
  const [selectedEnv, setSelectedEnv] = useState<Environment>(() => initialEnv || activeEnv);

  useEffect(() => {
    if (urlApp) setActiveApp(urlApp);
    if (initialEnv) setActiveEnv(initialEnv);
    // Applied once on arrival; later URL changes come with a remount of the page.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Compare with the last seen value rather than skipping the first run, so React's
  // double-invoked effects in development cannot overwrite the URL's scope.
  const lastActiveApp = useRef(activeApp);
  useEffect(() => {
    if (activeApp !== lastActiveApp.current) {
      lastActiveApp.current = activeApp;
      setSelectedApp(activeApp);
    }
  }, [activeApp]);

  const lastActiveEnv = useRef(activeEnv);
  useEffect(() => {
    if (activeEnv !== lastActiveEnv.current) {
      lastActiveEnv.current = activeEnv;
      setSelectedEnv(activeEnv);
    }
  }, [activeEnv]);

  const selectApp = (app: string) => {
    setSelectedApp(app);
    setActiveApp(app);
  };
  const selectEnv = (env: Environment) => {
    setSelectedEnv(env);
    setActiveEnv(env);
  };

  return { selectedApp, selectedEnv, selectApp, selectEnv };
}
