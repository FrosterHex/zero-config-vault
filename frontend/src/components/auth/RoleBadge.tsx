"use client";

import React from "react";
import { Eye, ShieldCheck } from "lucide-react";
import type { Role } from "@/lib/api";

/** Pill showing who the dashboard is acting as. Announced politely when the role changes. */
export function RoleBadge({ role }: { role: Role }) {
  const isAdmin = role === "admin";
  const Icon = isAdmin ? ShieldCheck : Eye;

  return (
    <span
      role="status"
      aria-atomic="true"
      className={`inline-flex items-center gap-1.5 rounded-full border px-2.5 py-1 text-[11px] font-mono font-medium whitespace-nowrap ${
        isAdmin
          ? "border-emerald-500/40 bg-emerald-500/10 text-emerald-300"
          : "border-amber-500/30 bg-amber-500/10 text-amber-200"
      }`}
    >
      <span className="relative flex h-1.5 w-1.5" aria-hidden="true">
        {isAdmin && (
          <span className="absolute inline-flex h-full w-full rounded-full bg-emerald-400 opacity-60 motion-safe:animate-ping" />
        )}
        <span className={`relative inline-flex h-1.5 w-1.5 rounded-full ${isAdmin ? "bg-emerald-400" : "bg-amber-400"}`} />
      </span>
      <Icon className="h-3 w-3" aria-hidden="true" />
      <span>{isAdmin ? "Admin" : "Guest · read-only"}</span>
    </span>
  );
}
