"use client";

import React, { useState } from "react";
import { ShieldCheck, Copy, Check } from "lucide-react";

interface IntegrityBadgeProps {
  hash: string;
  verified?: boolean;
}

export function IntegrityBadge({ hash, verified = true }: IntegrityBadgeProps) {
  const [copied, setCopied] = useState(false);

  const handleCopy = (e: React.MouseEvent) => {
    e.stopPropagation();
    if (hash) {
      navigator.clipboard.writeText(hash);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    }
  };

  const truncatedHash =
    hash && hash.length > 18
      ? `${hash.substring(0, 8)}...${hash.substring(hash.length - 8)}`
      : hash || "UNVERIFIED";

  return (
    <div
      onClick={handleCopy}
      title={`Full SHA-512 Hash: ${hash}\nClick to copy`}
      className="group inline-flex items-center gap-2 rounded-lg border border-emerald-500/20 bg-emerald-950/20 px-2.5 py-1 text-xs transition-all hover:border-emerald-500/40 hover:bg-emerald-950/40 cursor-pointer"
    >
      <div className="flex items-center gap-1.5 text-emerald-400">
        <ShieldCheck className="h-3.5 w-3.5 shrink-0" />
        <span className="font-mono text-[10px] font-semibold uppercase tracking-wider">
          {verified ? "SHA-512 Enclave" : "Pending"}
        </span>
      </div>

      <div className="h-3 w-px bg-white/10" />

      <div className="flex items-center gap-1">
        <code className="font-mono text-[11px] text-slate-300 group-hover:text-white transition-colors">
          {truncatedHash}
        </code>
        {copied ? (
          <Check className="h-3 w-3 text-emerald-400" />
        ) : (
          <Copy className="h-3 w-3 text-slate-500 group-hover:text-slate-300 transition-colors" />
        )}
      </div>
    </div>
  );
}
