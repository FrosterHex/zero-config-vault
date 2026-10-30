"use client";

import React, { useRef, useState, MouseEvent } from "react";
import { useMotionAccessibility } from "@/components/motion/MotionProvider";

interface GlassCard3DProps {
  children: React.ReactNode;
  className?: string;
  glowColor?: "mint" | "cyan" | "emerald" | "blue";
  /**
   * Tilt toward the cursor. Off by default: a card that moves under the pointer makes its
   * buttons, inputs and text hard to hit and read, so only decorative cards should opt in.
   */
  tilt?: boolean;
  intensity?: number;
}

export function GlassCard3D({
  children,
  className = "",
  glowColor = "mint",
  tilt = false,
  intensity = 15,
}: GlassCard3DProps) {
  const cardRef = useRef<HTMLDivElement>(null);
  const { shouldReduceMotion: prefersReducedMotion } = useMotionAccessibility();
  const shouldReduceMotion = prefersReducedMotion || !tilt;

  const [rotateX, setRotateX] = useState(0);
  const [rotateY, setRotateY] = useState(0);
  const [glarePosition, setGlarePosition] = useState({ x: 50, y: 50, opacity: 0 });

  const glowStyles = {
    mint: "hover:border-mint-400/30 hover:shadow-[0_12px_40px_rgba(93,240,168,0.14)]",
    cyan: "hover:border-cyan-500/40 hover:shadow-[0_12px_40px_rgba(6,182,212,0.2)]",
    emerald: "hover:border-emerald-500/40 hover:shadow-[0_12px_40px_rgba(16,185,129,0.2)]",
    blue: "hover:border-blue-500/40 hover:shadow-[0_12px_40px_rgba(58,108,255,0.2)]",
  };

  const handleMouseMove = (e: MouseEvent<HTMLDivElement>) => {
    if (shouldReduceMotion || !cardRef.current) return;

    const rect = cardRef.current.getBoundingClientRect();
    const x = e.clientX - rect.left;
    const y = e.clientY - rect.top;

    const centerX = rect.width / 2;
    const centerY = rect.height / 2;

    const rX = ((y - centerY) / centerY) * -intensity;
    const rY = ((x - centerX) / centerX) * intensity;

    setRotateX(rX);
    setRotateY(rY);

    setGlarePosition({
      x: (x / rect.width) * 100,
      y: (y / rect.height) * 100,
      opacity: 0.18,
    });
  };

  const handleMouseLeave = () => {
    setRotateX(0);
    setRotateY(0);
    setGlarePosition((prev) => ({ ...prev, opacity: 0 }));
  };

  return (
    <div
      style={{ perspective: 1200 }}
      // h-full: the wrapper must take the column's height, or a card given h-full (editor, ledger) collapses to its content
      className="relative h-full transition-transform duration-200"
    >
      <div
        ref={cardRef}
        onMouseMove={handleMouseMove}
        onMouseLeave={handleMouseLeave}
        style={{
          transform: shouldReduceMotion
            ? "none"
            : `rotateX(${rotateX}deg) rotateY(${rotateY}deg) translateZ(0)`,
          transformStyle: "preserve-3d",
          transition: "transform 0.15s ease-out, box-shadow 0.3s ease, border-color 0.3s ease",
        }}
        // Glass card from the design: --glass background, --line border, --radius corners
        className={`relative overflow-hidden rounded-[18px] border border-[rgba(150,175,230,0.12)] bg-[rgba(12,18,38,0.55)] backdrop-blur-[14px] transition-all duration-300 hover:bg-[rgba(16,24,48,0.65)] ${glowStyles[glowColor]} ${className}`}
      >
        {/* Dynamic Specular Cursor Glare Layer */}
        {!shouldReduceMotion && (
          <div
            className="pointer-events-none absolute inset-0 transition-opacity duration-300"
            style={{
              opacity: glarePosition.opacity,
              background: `radial-gradient(circle 280px at ${glarePosition.x}% ${glarePosition.y}%, rgba(255,255,255,0.3), transparent 75%)`,
            }}
          />
        )}

        {/* Ambient Top Subtle Shimmer Border */}
        <div className="pointer-events-none absolute inset-x-0 top-0 h-[1px] bg-gradient-to-r from-transparent via-white/20 to-transparent" />

        {/* Content Container */}
        {/* h-full so panels given a fixed height (editor, revision ledger, diff) fill the card */}
        <div className="relative z-10 h-full">{children}</div>
      </div>
    </div>
  );
}
