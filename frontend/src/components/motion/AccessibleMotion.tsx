"use client";

import React from "react";
import { motion, HTMLMotionProps } from "framer-motion";
import { useMotionAccessibility } from "./MotionProvider";

export interface AccessibleMotionProps extends HTMLMotionProps<"div"> {
  children?: React.ReactNode;
}

export function AccessibleMotionDiv({
  children,
  transition,
  animate,
  initial,
  exit,
  ...props
}: AccessibleMotionProps) {
  const { shouldReduceMotion } = useMotionAccessibility();

  if (shouldReduceMotion) {
    return (
      <div {...(props as React.HTMLAttributes<HTMLDivElement>)}>
        {children}
      </div>
    );
  }

  return (
    <motion.div
      initial={initial}
      animate={animate}
      exit={exit}
      transition={transition}
      {...props}
    >
      {children}
    </motion.div>
  );
}

export function AccessibleFade({
  children,
  className,
  delay = 0,
}: {
  children: React.ReactNode;
  className?: string;
  delay?: number;
}) {
  const { shouldReduceMotion } = useMotionAccessibility();

  if (shouldReduceMotion) {
    return <div className={className}>{children}</div>;
  }

  return (
    <motion.div
      initial={{ opacity: 0, y: 6 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.25, delay, ease: [0.16, 1, 0.3, 1] }}
      className={className}
    >
      {children}
    </motion.div>
  );
}
