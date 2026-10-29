"use client";

import React, { useState } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { motion, AnimatePresence } from "framer-motion";
import {
  LayoutDashboard,
  Layers,
  GitCompare,
  FileCode,
  HardDrive,
  KeyRound,
  ChevronLeft,
  ChevronRight,
  Database,
  Radio,
} from "lucide-react";
import { cn } from "@/lib/utils";
import { useMotionAccessibility } from "@/components/motion/MotionProvider";
import { useVault } from "@/context/VaultContext";
import { VAULT_STATUS_DISPLAY } from "./VaultStatusText";

const STATUS_TONE = {
  ok: { box: "bg-emerald-500/10 border-emerald-500/20", icon: "text-emerald-400", label: "text-emerald-300" },
  wait: { box: "bg-sky-500/10 border-sky-500/20", icon: "text-sky-400", label: "text-sky-300" },
  error: { box: "bg-rose-500/10 border-rose-500/20", icon: "text-rose-400", label: "text-rose-300" },
} as const;

export interface NavItem {
  id: string;
  title: string;
  href: string;
  icon: React.ElementType;
  badge?: string | number;
}

const navItems: NavItem[] = [
  { id: "dashboard", title: "Overview", href: "/dashboard", icon: LayoutDashboard },
  { id: "configs", title: "Configurations", href: "/configs", icon: Layers },
  { id: "diff", title: "Diff Engine", href: "/diff", icon: GitCompare },
  { id: "binaries", title: "GridFS Binaries", href: "/binaries", icon: HardDrive },
  { id: "export", title: "Export Studio", href: "/export", icon: FileCode },
  { id: "audit", title: "Live Audit Log", href: "/audit", icon: Radio, badge: "LIVE" },
  { id: "nosql", title: "NoSQL Deep Dive", href: "/nosql", icon: Database },
  { id: "access", title: "Scoped API Keys", href: "/access", icon: KeyRound },
];

export function Sidebar() {
  const [collapsed, setCollapsed] = useState(false);
  const pathname = usePathname();
  const { shouldReduceMotion, getAccessibleTransition } = useMotionAccessibility();
  const { vaultStatus } = useVault();
  const status = VAULT_STATUS_DISPLAY[vaultStatus];

  const sidebarWidth = collapsed ? 76 : 256;
  const springTransition = getAccessibleTransition({
    type: "spring" as const,
    stiffness: 350,
    damping: 32,
  });

  return (
    <motion.aside
      animate={{ width: sidebarWidth }}
      transition={springTransition}
      className="relative flex flex-col h-full border-r border-[rgba(150,175,230,0.12)] bg-[#060a18] select-none shrink-0 overflow-hidden"
    >
      {/* Brand Header */}
      <div className="flex h-16 items-center justify-between px-4 border-b border-[rgba(150,175,230,0.12)] shrink-0">
        <div className="flex items-center gap-3 overflow-hidden">
          <motion.div
            whileHover={shouldReduceMotion ? undefined : { scale: 1.05 }}
            whileTap={shouldReduceMotion ? undefined : { scale: 0.95 }}
            className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-mint-400/10 text-mint-400 border border-mint-400/20"
            aria-hidden="true"
          >
            <span className="text-sm leading-none -translate-y-px">▲</span>
          </motion.div>
          <AnimatePresence initial={false}>
            {!collapsed && (
              <motion.div
                initial={shouldReduceMotion ? false : { opacity: 0, x: -10 }}
                animate={{ opacity: 1, x: 0 }}
                exit={shouldReduceMotion ? { opacity: 0 } : { opacity: 0, x: -10 }}
                transition={getAccessibleTransition({ duration: 0.15 })}
                className="flex flex-col whitespace-nowrap overflow-hidden"
              >
                <span className="text-[15px] font-extrabold tracking-[0.12em] text-white leading-none">
                  Zero-Config
                </span>
                <span className="text-[10px] font-mono tracking-widest text-mint-400 font-semibold uppercase mt-0.5">
                  Vault
                </span>
              </motion.div>
            )}
          </AnimatePresence>
        </div>
        <button
          type="button"
          onClick={() => setCollapsed(!collapsed)}
          className="flex h-6 w-6 items-center justify-center rounded border border-white/10 bg-white/5 text-slate-400 hover:text-white hover:bg-white/10 transition-colors"
          aria-label={collapsed ? "Expand sidebar" : "Collapse sidebar"}
        >
          {collapsed ? <ChevronRight className="h-3.5 w-3.5" /> : <ChevronLeft className="h-3.5 w-3.5" />}
        </button>
      </div>

      {/* Navigation Groups */}
      <div className="flex-1 py-4 px-3 space-y-1 overflow-y-auto overflow-x-hidden">
        <AnimatePresence initial={false}>
          {!collapsed && (
            <motion.div
              initial={shouldReduceMotion ? false : { opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              transition={getAccessibleTransition({ duration: 0.15 })}
              className="px-3 pb-2 text-[10px] font-mono uppercase tracking-wider text-slate-500"
            >
              Platform Modules
            </motion.div>
          )}
        </AnimatePresence>

        {navItems.map((item) => {
          const Icon = item.icon;
          const isActive = pathname === item.href;

          return (
            <Link
              key={item.id}
              href={item.href}
              aria-current={isActive ? "page" : undefined}
              // When collapsed only the icon is visible, so the title becomes the link's name and tooltip.
              title={collapsed ? item.title : undefined}
              aria-label={collapsed ? item.title : undefined}
              className={cn(
                "group relative flex items-center gap-3 rounded-lg px-3 py-2.5 text-xs font-medium font-sans transition-colors",
                isActive
                  ? "text-mint-300 font-semibold"
                  : "text-slate-400 hover:text-slate-200 hover:bg-white/5"
              )}
            >
              {/* Animated active pill background */}
              {isActive && (
                <motion.div
                  layoutId={shouldReduceMotion ? undefined : "active-nav-pill"}
                  className="absolute inset-0 rounded-lg bg-mint-600/15 border border-mint-500/30"
                  transition={springTransition}
                />
              )}

              <Icon
                className={cn(
                  "relative z-10 h-4 w-4 shrink-0 transition-transform",
                  !shouldReduceMotion && "group-hover:scale-110",
                  isActive ? "text-mint-400" : "text-slate-400"
                )}
              />

              <AnimatePresence initial={false}>
                {!collapsed && (
                  <motion.div
                    initial={shouldReduceMotion ? false : { opacity: 0, x: -8 }}
                    animate={{ opacity: 1, x: 0 }}
                    exit={shouldReduceMotion ? { opacity: 0 } : { opacity: 0, x: -8 }}
                    transition={getAccessibleTransition({ duration: 0.15 })}
                    className="relative z-10 flex flex-1 items-center justify-between whitespace-nowrap overflow-hidden"
                  >
                    <span>{item.title}</span>
                    {item.badge && (
                      <span
                        className={cn(
                          "rounded px-1.5 py-0.5 text-[9px] font-mono font-semibold",
                          item.badge === "LIVE"
                            ? "bg-emerald-500/20 text-emerald-400 border border-emerald-500/30 animate-pulse"
                            : "bg-white/10 text-slate-300"
                        )}
                      >
                        {item.badge}
                      </span>
                    )}
                  </motion.div>
                )}
              </AnimatePresence>
            </Link>
          );
        })}
      </div>

      {/* System Status Footer */}
      <div className="border-t border-white/10 p-3 bg-black/20 shrink-0">
        <motion.div
          role="status"
          title={`${status.label}: ${status.detail}`}
          whileHover={shouldReduceMotion ? undefined : { scale: 1.02 }}
          transition={getAccessibleTransition({ duration: 0.15 })}
          className={cn(
            "flex items-center gap-2 rounded-lg p-2 border",
            STATUS_TONE[status.tone].box,
            collapsed && "justify-center"
          )}
        >
          <Database className={cn("h-4 w-4 shrink-0", STATUS_TONE[status.tone].icon)} aria-hidden="true" />
          {collapsed && <span className="sr-only">{status.label}</span>}
          <AnimatePresence initial={false}>
            {!collapsed && (
              <motion.div
                initial={shouldReduceMotion ? false : { opacity: 0, x: -6 }}
                animate={{ opacity: 1, x: 0 }}
                exit={shouldReduceMotion ? { opacity: 0 } : { opacity: 0, x: -6 }}
                transition={getAccessibleTransition({ duration: 0.15 })}
                className="flex flex-col text-[11px] whitespace-nowrap overflow-hidden"
              >
                <span className={cn("font-semibold font-sans", STATUS_TONE[status.tone].label)}>
                  {status.label}
                </span>
                <span className="text-[9px] text-slate-400 font-mono">{status.detail}</span>
              </motion.div>
            )}
          </AnimatePresence>
        </motion.div>
      </div>
    </motion.aside>
  );
}
