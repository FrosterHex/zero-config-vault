"use client";

import React from "react";
import { Sidebar } from "./Sidebar";
import { Header } from "./Header";
import { MotionProvider } from "@/components/motion/MotionProvider";

export function DashboardShell({ children }: { children: React.ReactNode }) {
  return (
    <MotionProvider>
      <div className="flex h-screen w-screen overflow-hidden bg-[#090D16]">
        <Sidebar />
        <div className="flex flex-1 flex-col overflow-hidden">
          <Header />
          <main className="flex-1 overflow-y-auto p-6 space-y-6">
            {children}
          </main>
        </div>
      </div>
    </MotionProvider>
  );
}
