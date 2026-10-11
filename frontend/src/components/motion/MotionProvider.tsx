"use client";

import React, { createContext, useContext } from "react";
import { MotionConfig, useReducedMotion, type Transition } from "framer-motion";

interface MotionContextType {
  shouldReduceMotion: boolean;
  getAccessibleTransition: (transition?: Transition) => Transition | undefined;
}

const MotionContext = createContext<MotionContextType>({
  shouldReduceMotion: false,
  getAccessibleTransition: (t) => t || { duration: 0 },
});

export function useMotionAccessibility() {
  return useContext(MotionContext);
}

export function MotionProvider({ children }: { children: React.ReactNode }) {
  const prefersReducedMotion = useReducedMotion();
  const shouldReduceMotion = Boolean(prefersReducedMotion);

  const getAccessibleTransition = (transition?: Transition): Transition | undefined => {
    if (shouldReduceMotion) {
      return { duration: 0 };
    }
    return transition;
  };

  return (
    <MotionContext.Provider value={{ shouldReduceMotion, getAccessibleTransition }}>
      <MotionConfig
        reducedMotion={shouldReduceMotion ? "always" : "user"}
        transition={shouldReduceMotion ? { duration: 0 } : undefined}
      >
        {children}
      </MotionConfig>
    </MotionContext.Provider>
  );
}
