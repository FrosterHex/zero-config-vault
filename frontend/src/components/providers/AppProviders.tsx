"use client";

import React from "react";
import { VaultProvider } from "@/context/VaultContext";
import { MotionProvider } from "@/components/motion/MotionProvider";

export function AppProviders({ children }: { children: React.ReactNode }) {
  return (
    <VaultProvider>
      <MotionProvider>
        {children}
      </MotionProvider>
    </VaultProvider>
  );
}
