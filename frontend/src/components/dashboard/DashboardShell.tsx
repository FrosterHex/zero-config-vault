"use client";

import React from "react";
import { Sidebar } from "./Sidebar";
import { Header } from "./Header";
import { CommandPalette } from "./CommandPalette";

export function DashboardShell({ children }: { children: React.ReactNode }) {
  // Transparent so each page's fixed starfield backdrop (bg-grid-cyber, -z-10) shows through
  return (
    <div className="flex h-screen w-screen overflow-hidden bg-transparent">
      <Sidebar />
      <div className="flex flex-1 flex-col overflow-hidden">
        <Header />
        <main className="flex-1 overflow-y-auto p-6 space-y-6">
          {children}
        </main>
      </div>
      <CommandPalette />
    </div>
  );
}
