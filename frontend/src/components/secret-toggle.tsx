"use client";

import React from "react";
import { Lock, Unlock } from "lucide-react";

interface SecretToggleProps {
  masked: boolean;
  onToggle: (masked: boolean) => void;
  /** When set, the reveal button stays visible but disabled, with this text as its explanation. */
  revealDisabledReason?: string;
}

export function SecretToggle({ masked, onToggle, revealDisabledReason }: SecretToggleProps) {
  return (
    <div className="inline-flex items-center rounded-lg border border-white/10 bg-black/40 p-0.5 text-xs font-mono">
      {/* Masked Button */}
      <button
        type="button"
        onClick={() => onToggle(true)}
        aria-pressed={masked}
        className={`inline-flex items-center gap-1.5 rounded-md px-2.5 py-1 transition-all ${
          masked
            ? "bg-amber-600/30 border border-amber-500/40 text-amber-300 font-semibold shadow-sm"
            : "text-slate-400 hover:text-white"
        }`}
        title="Mask sensitive keys with ********"
      >
        <Lock className="h-3 w-3 text-amber-400" />
        <span>Masked (••••)</span>
      </button>

      {/* Decrypted Button */}
      <button
        type="button"
        onClick={() => onToggle(false)}
        aria-pressed={!masked}
        disabled={!!revealDisabledReason}
        className={`inline-flex items-center gap-1.5 rounded-md px-2.5 py-1 transition-all disabled:cursor-not-allowed disabled:opacity-40 ${
          !masked
            ? "bg-emerald-600/30 border border-emerald-500/40 text-emerald-300 font-semibold shadow-sm"
            : "text-slate-400 hover:text-white disabled:hover:text-slate-400"
        }`}
        title={revealDisabledReason ?? "Reveal plaintext decrypted secrets via AES-256-GCM envelope key"}
      >
        <Unlock className="h-3 w-3 text-emerald-400" />
        <span>Decrypted Plaintext</span>
      </button>
    </div>
  );
}
