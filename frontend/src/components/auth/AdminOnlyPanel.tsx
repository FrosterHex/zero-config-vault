"use client";

import React from "react";
import { Lock } from "lucide-react";

/** Locked state for a section that only an admin session may use. */
export function AdminOnlyPanel({ title, description }: { title: string; description: string }) {
  return (
    <section
      aria-labelledby="admin-only-title"
      className="relative overflow-hidden rounded-2xl border border-white/10 bg-slate-900/60 px-6 py-12 text-center backdrop-blur-md"
    >
      <div className="pointer-events-none absolute left-1/2 top-0 h-40 w-72 -translate-x-1/2 rounded-full bg-mint-600/10 blur-3xl" />
      <div className="relative mx-auto flex max-w-md flex-col items-center gap-3">
        <div className="flex h-12 w-12 items-center justify-center rounded-2xl border border-mint-500/30 bg-mint-500/10 text-mint-300">
          <Lock className="h-6 w-6" aria-hidden="true" />
        </div>
        <h2 id="admin-only-title" className="font-sans text-sm font-semibold text-white">
          {title}
        </h2>
        <p className="text-xs leading-relaxed text-slate-400">{description}</p>
      </div>
    </section>
  );
}
