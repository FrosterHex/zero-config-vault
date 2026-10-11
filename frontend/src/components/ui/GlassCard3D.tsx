"use client";

import React, { useRef, useState, MouseEvent } from "react";
import { useMotionAccessibility } from "@/components/motion/MotionProvider";

interface GlassCard3DProps {
  children: React.ReactNode;
  className?: string;
  glowColor?: "indigo" | "cyan" | "emerald" | "purple";
  intensity?: number;
}

export function GlassCard3D({
  children,
  className = "",
  glowColor = "indigo",
  intensity = 15,
}: GlassCard3DProps) {
  const cardRef = useRef<HTMLDivElement>(null);
  const { shouldReduceMotion } = useMotionAccessibility();

  const [rotateX, setRotateX] = useState(0);
  const [rotateY, setRotateY] = useState(0);
  const [glarePosition, setGlarePosition] = useState({ x: 50, y: 50, opacity: 0 });

  const glowStyles = {
    indigo: "hover:border-indigo-500/40 hover:shadow-[0_12px_40px_rgba(99,102,241,0.2)]",
    cyan: "hover:border-cyan-500/40 hover:shadow-[0_12px_40px_rgba(6,182,212,0.2)]",
    emerald: "hover:border-emerald-500/40 hover:shadow-[0_12px_40px_rgba(16,185,129,0.2)]",
    purple: "hover:border-purple-500/40 hover:shadow-[0_12px_40px_rgba(168,85,247,0.2)]",
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
      className="relative transition-transform duration-200"
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
        className={`relative overflow-hidden rounded-2xl border border-white/10 bg-slate-900/50 backdrop-blur-xl transition-all duration-300 ${glowStyles[glowColor]} ${className}`}
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
        <div className="relative z-10">{children}</div>
      </div>
    </div>
  );
}
