"use client";

import { useEffect } from "react";
import Lenis from "lenis";

/**
 * Read inside effects (not as React state): state would start as false during the first render, so
 * reduced-motion visitors would get Lenis and the planet entrance started and then torn down again.
 */
export function prefersReducedMotion(): boolean {
  return typeof window !== "undefined" && window.matchMedia("(prefers-reduced-motion: reduce)").matches;
}

/**
 * Staggered scroll reveal: adds `.in` to each `[data-reveal]` inside `root` once it scrolls into view.
 * Under reduced motion every element is shown immediately.
 */
export function useReveal(root: React.RefObject<HTMLElement | null>, deps: unknown[] = []) {
  useEffect(() => {
    const el = root.current;
    if (!el) return;
    const targets = Array.from(el.querySelectorAll<HTMLElement>("[data-reveal]:not(.in)"));
    if (prefersReducedMotion() || typeof IntersectionObserver === "undefined") {
      targets.forEach((t) => t.classList.add("in"));
      return;
    }
    const observer = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          if (entry.isIntersecting) {
            entry.target.classList.add("in");
            observer.unobserve(entry.target);
          }
        }
      },
      { threshold: 0.18, rootMargin: "0px 0px -8% 0px" }
    );
    targets.forEach((t) => observer.observe(t));
    return () => observer.disconnect();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [root, ...deps]);
}

/** Lenis smooth scrolling (including in-page anchor links) for the landing page; skipped under reduced motion. */
export function useLenis() {
  useEffect(() => {
    if (prefersReducedMotion()) return;
    const lenis = new Lenis({ duration: 1.15, smoothWheel: true, touchMultiplier: 1.5, anchors: true });
    let frame = requestAnimationFrame(function raf(t) {
      lenis.raf(t);
      frame = requestAnimationFrame(raf);
    });
    return () => {
      cancelAnimationFrame(frame);
      lenis.destroy();
    };
  }, []);
}
