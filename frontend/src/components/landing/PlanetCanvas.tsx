"use client";

import React, { useEffect, useRef } from "react";
import { prefersReducedMotion } from "./useLandingMotion";

/**
 * Host for the fixed full-viewport WebGL planet. The scene module (three.js, loaders, post-processing)
 * is loaded only after the page has rendered, so the hero copy never waits for it.
 * Each mount creates its own <canvas>: a canvas whose context was released by dispose() cannot be
 * reused (React remounts in development would otherwise get a dead context).
 */
export function PlanetCanvas() {
  const host = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const el = host.current;
    if (!el) return;
    const reducedMotion = prefersReducedMotion();
    let cancelled = false;
    let cleanup: (() => void) | undefined;

    import("./planet/PlanetScene")
      .then(({ PlanetScene }) => {
        if (cancelled) return;
        const canvas = document.createElement("canvas");
        canvas.className = "planet-canvas";
        canvas.setAttribute("aria-hidden", "true");
        el.appendChild(canvas);
        const scene = new PlanetScene(canvas, { reducedMotion });
        scene.start();
        cleanup = () => {
          scene.dispose();
          canvas.remove();
        };
      })
      .catch((err) => console.warn("Planet scene could not be loaded; the page continues without it.", err));

    return () => {
      cancelled = true;
      cleanup?.();
    };
  }, []);

  return <div ref={host} />;
}
