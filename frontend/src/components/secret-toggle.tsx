import React from "react";
import { Eye, EyeOff } from "lucide-react";

interface SecretToggleProps {
  masked: boolean;
  onToggle: (masked: boolean) => void;
}

export function SecretToggle({ masked, onToggle }: SecretToggleProps) {
  return (
    <button
      type="button"
      onClick={() => onToggle(!masked)}
      className={`inline-flex items-center gap-2 rounded-lg border px-3 py-1.5 text-xs font-medium transition-all ${
        masked
          ? "border-amber-500/30 bg-amber-950/20 text-amber-300 hover:border-amber-500/50 hover:bg-amber-950/30"
          : "border-indigo-500/30 bg-indigo-950/20 text-indigo-300 hover:border-indigo-500/50 hover:bg-indigo-950/30"
      }`}
      title={masked ? "Click to reveal secrets" : "Click to mask secrets (********)"}
    >
      {masked ? (
        <>
          <EyeOff className="h-3.5 w-3.5" />
          <span className="font-mono text-[11px] font-semibold">Masked View</span>
        </>
      ) : (
        <>
          <Eye className="h-3.5 w-3.5" />
          <span className="font-mono text-[11px] font-semibold">Decrypted View</span>
        </>
      )}
    </button>
  );
}
