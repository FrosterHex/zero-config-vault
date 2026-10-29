"use client";

import React, { useEffect, useRef } from "react";
import { PlanetScene } from "./planet/PlanetScene";

/**
 * Host for the fixed full-viewport WebGL planet. Each mount creates its own <canvas>: a canvas whose
 * context was released by dispose() cannot be reused (React remounts in development, or a change of
 * the reduced-motion preference, would otherwise get a dead context).
 */
export function PlanetCanvas({ reducedMotion }: { reducedMotion: boolean }) {
  const host = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const el = host.current;
    if (!el) return;
    const canvas = document.createElement("canvas");
    canvas.className = "planet-canvas";
    canvas.setAttribute("aria-hidden", "true");
    el.appendChild(canvas);
    const scene = new PlanetScene(canvas, { reducedMotion });
    scene.start();
    return () => {
      scene.dispose();
      canvas.remove();
    };
  }, [reducedMotion]);

  return <div ref={host} />;
}
