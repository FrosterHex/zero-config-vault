"use client";

import React from "react";
import { ArrowRight, Lock } from "lucide-react";

export const GUEST_WRITE_HINT = "Guests can write only in the playground app";

/** Banner shown where a guest cannot write, with a shortcut to the writable sandbox. */
export function GuestWriteNotice({ onSwitchToPlayground }: { onSwitchToPlayground: () => void }) {
  return (
    <div className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-amber-500/25 bg-amber-950/20 px-3.5 py-2.5">
      <div className="flex items-center gap-2 text-xs text-amber-200">
        <Lock className="h-3.5 w-3.5 shrink-0 text-amber-400" aria-hidden="true" />
        <span>{GUEST_WRITE_HINT}</span>
      </div>
      <button
        type="button"
        onClick={onSwitchToPlayground}
        className="inline-flex items-center gap-1.5 rounded-lg border border-amber-500/30 bg-amber-500/10 px-3 py-1.5 text-xs font-mono text-amber-200 transition-colors hover:bg-amber-500/20 hover:text-white focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-amber-400"
      >
        <span>Switch to playground/development</span>
        <ArrowRight className="h-3.5 w-3.5" aria-hidden="true" />
      </button>
    </div>
  );
}
