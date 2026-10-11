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
  Lock,
  Radio,
} from "lucide-react";
import { cn } from "@/lib/utils";
import { useMotionAccessibility } from "@/components/motion/MotionProvider";

export interface NavItem {
  id: string;
  title: string;
  href: string;
  icon: React.ElementType;
  badge?: string | number;
}

const navItems: NavItem[] = [
  { id: "dashboard", title: "Overview", href: "/", icon: LayoutDashboard },
  { id: "configs", title: "Configurations", href: "/configs", icon: Layers, badge: "v8" },
  { id: "diff", title: "Diff Engine", href: "/diff", icon: GitCompare },
  { id: "binaries", title: "GridFS Binaries", href: "/binaries", icon: HardDrive },
  { id: "export", title: "Export Studio", href: "/export", icon: FileCode },
  { id: "audit", title: "Live Audit Log", href: "/audit", icon: Radio, badge: "LIVE" },
  { id: "nosql", title: "NoSQL Deep Dive", href: "/nosql", icon: Database, badge: "PRO" },
  { id: "access", title: "Scoped API Keys", href: "/access", icon: KeyRound },
];

export function Sidebar() {
  const [collapsed, setCollapsed] = useState(false);
  const pathname = usePathname();
  const { shouldReduceMotion, getAccessibleTransition } = useMotionAccessibility();

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
      className="relative flex flex-col h-full border-r border-white/10 bg-[#0B0F19] select-none shrink-0 overflow-hidden"
    >
      {/* Brand Header */}
      <div className="flex h-16 items-center justify-between px-4 border-b border-white/10 shrink-0">
        <div className="flex items-center gap-3 overflow-hidden">
          <motion.div
            whileHover={shouldReduceMotion ? undefined : { scale: 1.05 }}
            whileTap={shouldReduceMotion ? undefined : { scale: 0.95 }}
            className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-indigo-600/20 text-indigo-400 border border-indigo-500/30"
          >
            <Lock className="h-5 w-5" />
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
                <span className="font-['Cormorant_Garamond',serif] text-xl font-bold tracking-tight text-white leading-none">
                  Zero-Config
                </span>
                <span className="text-[10px] font-mono tracking-widest text-indigo-400 font-semibold uppercase mt-0.5">
                  Vault OS
                </span>
              </motion.div>
            )}
          </AnimatePresence>
        </div>
        <button
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
              className={cn(
                "group relative flex items-center gap-3 rounded-lg px-3 py-2.5 text-xs font-medium font-['Montserrat',sans-serif] transition-colors",
                isActive
                  ? "text-indigo-300 font-semibold"
                  : "text-slate-400 hover:text-slate-200 hover:bg-white/5"
              )}
            >
              {/* Animated active pill background */}
              {isActive && (
                <motion.div
                  layoutId={shouldReduceMotion ? undefined : "active-nav-pill"}
                  className="absolute inset-0 rounded-lg bg-indigo-600/15 border border-indigo-500/30"
                  transition={springTransition}
                />
              )}

              <Icon
                className={cn(
                  "relative z-10 h-4 w-4 shrink-0 transition-transform",
                  !shouldReduceMotion && "group-hover:scale-110",
                  isActive ? "text-indigo-400" : "text-slate-400"
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
          whileHover={shouldReduceMotion ? undefined : { scale: 1.02 }}
          transition={getAccessibleTransition({ duration: 0.15 })}
          className={cn(
            "flex items-center gap-2 rounded-lg p-2 bg-emerald-500/10 border border-emerald-500/20",
            collapsed && "justify-center"
          )}
        >
          <Database className="h-4 w-4 text-emerald-400 shrink-0" />
          <AnimatePresence initial={false}>
            {!collapsed && (
              <motion.div
                initial={shouldReduceMotion ? false : { opacity: 0, x: -6 }}
                animate={{ opacity: 1, x: 0 }}
                exit={shouldReduceMotion ? { opacity: 0 } : { opacity: 0, x: -6 }}
                transition={getAccessibleTransition({ duration: 0.15 })}
                className="flex flex-col text-[11px] whitespace-nowrap overflow-hidden"
              >
                <span className="font-semibold text-emerald-300 font-['Montserrat',sans-serif]">
                  MongoDB Replica Set
                </span>
                <span className="text-[9px] text-emerald-400/80 font-mono">
                  Change Streams Active
                </span>
              </motion.div>
            )}
          </AnimatePresence>
        </motion.div>
      </div>
    </motion.aside>
  );
}
