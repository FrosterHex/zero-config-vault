import React from "react";
import type { IconName } from "./landing-content";

/** 24×24 line icons from the design (stroke 1.6, round caps and joins). */
export function FeatureIcon({ name }: { name: IconName }) {
  return (
    <svg className="icon" viewBox="0 0 24 24" aria-hidden="true">
      {name === "workflow" && (
        <>
          <rect x="3" y="3" width="6" height="6" rx="1.5" />
          <rect x="15" y="15" width="6" height="6" rx="1.5" />
          <path d="M6 9v3a3 3 0 0 0 3 3h3" />
        </>
      )}
      {name === "analytics" && (
        <>
          <path d="M4 19V5" />
          <path d="M4 19h16" />
          <path d="M8 16v-4" />
          <path d="M13 16V8" />
          <path d="M18 16v-7" />
        </>
      )}
      {name === "leads" && (
        <>
          <path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2" />
          <circle cx="9" cy="7" r="4" />
          <path d="M22 21v-2a4 4 0 0 0-3-3.87" />
          <path d="M16 3.13a4 4 0 0 1 0 7.75" />
        </>
      )}
      {name === "shield" && (
        <>
          <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10Z" />
          <path d="m9 12 2 2 4-4" />
        </>
      )}
      {name === "bolt" && <path d="M13 2 3 14h9l-1 8 10-12h-9l1-8Z" />}
      {name === "globe" && (
        <>
          <circle cx="12" cy="12" r="10" />
          <path d="M2 12h20" />
          <path d="M12 2a15 15 0 0 1 0 20 15 15 0 0 1 0-20Z" />
        </>
      )}
    </svg>
  );
}

export function ArrowIcon() {
  return (
    <svg className="icon arrow" viewBox="0 0 24 24" aria-hidden="true">
      <path d="M5 12h14" />
      <path d="m12 5 7 7-7 7" />
    </svg>
  );
}
