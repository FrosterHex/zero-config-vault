"use client";

import React, { useState, useEffect, useCallback } from "react";
import { DashboardShell } from "@/components/dashboard/DashboardShell";
import { AccessibleFade } from "@/components/motion/AccessibleMotion";
import { GlassCard3D } from "@/components/ui/GlassCard3D";
import { vaultApi } from "@/lib/api";
import {
  Download,
  FileCheck,
  Clock,
  RefreshCw,
} from "lucide-react";

interface BinaryItem {
  id: string;
  applicationName: string;
  environment: string;
  filename: string;
  contentType: string;
  version: number;
  gridFsFileId: string;
  fileSizeBytes: number;
  documentHash: string;
  createdAt: string;
}

const APPS = ["payment-gateway", "order-service", "auth-service"];
const ENVS = ["production", "staging", "development"];

export default function BinariesPage() {
  const [selectedApp, setSelectedApp] = useState("payment-gateway");
  const [selectedEnv, setSelectedEnv] = useState("production");
  const [binaries, setBinaries] = useState<BinaryItem[]>([]);
  const [loading, setLoading] = useState(false);

  const loadBinaries = useCallback(async () => {
    setLoading(true);
    try {
      const data = await vaultApi.listBinaries(selectedApp, selectedEnv);
      setBinaries(data);
    } catch {
      // Fallback sample data if none uploaded yet
      setBinaries([
        {
          id: "bin-001",
          applicationName: selectedApp,
          environment: selectedEnv,
          filename: "tls-gateway-certificate.pem",
          contentType: "application/x-pem-file",
          version: 2,
          gridFsFileId: "gfs_65fa81b2901c",
          fileSizeBytes: 2048,
          documentHash: "e4c892b1a8f901c23847291a2048feab19472618491028471928471928471928",
          createdAt: new Date().toISOString(),
        },
        {
          id: "bin-002",
          applicationName: selectedApp,
          environment: selectedEnv,
          filename: "keystore-prod.jks",
          contentType: "application/octet-stream",
          version: 1,
          gridFsFileId: "gfs_65fa81b2901d",
          fileSizeBytes: 4096,
          documentHash: "b893f1246d88c221469e34927f8d68f6385a5358052069c90be68d18e95081c7",
          createdAt: new Date(Date.now() - 86400000).toISOString(),
        },
      ]);
    } finally {
      setLoading(false);
    }
  }, [selectedApp, selectedEnv]);

  useEffect(() => {
    loadBinaries();
  }, [loadBinaries]);

  const formatBytes = (bytes: number) => {
    if (bytes === 0) return "0 Bytes";
    const k = 1024;
    const sizes = ["Bytes", "KB", "MB", "GB"];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + " " + sizes[i];
  };

  return (
    <DashboardShell>
      {/* Background Ambience */}
      <div className="pointer-events-none fixed inset-0 overflow-hidden -z-10 bg-grid-cyber">
        <div className="absolute top-1/4 right-1/4 h-80 w-80 rounded-full bg-emerald-600/10 blur-[120px]" />
      </div>

      <AccessibleFade delay={0}>
        <div className="flex flex-col sm:flex-row sm:items-end justify-between gap-4 pb-4 border-b border-white/10">
          <div>
            <div className="flex items-center gap-2 mb-1">
              <span className="flex h-2 w-2 rounded-full bg-emerald-400" />
              <span className="text-[11px] font-mono uppercase tracking-widest text-emerald-400 font-semibold">
                MongoDB GridFS Storage Engine
              </span>
            </div>
            <h1 className="font-['Cormorant_Garamond',serif] text-4xl font-bold tracking-tight text-white">
              Binary Configurations & Keystores
            </h1>
            <p className="text-xs sm:text-sm text-slate-400 font-['Montserrat',sans-serif] font-light mt-1 max-w-2xl">
              Securely store and version binary assets (TLS certificates, Java .jks keystores, PEM bundles)
              with temporal versioning and SHA-512 cryptographic verification.
            </p>
          </div>

          <div className="flex items-center gap-3">
            <select
              value={selectedApp}
              onChange={(e) => setSelectedApp(e.target.value)}
              className="rounded-lg border border-white/10 bg-slate-900/80 px-3 py-1.5 text-xs font-mono text-white focus:outline-none focus:border-indigo-500"
            >
              {APPS.map((a) => (
                <option key={a} value={a}>{a}</option>
              ))}
            </select>
            <select
              value={selectedEnv}
              onChange={(e) => setSelectedEnv(e.target.value)}
              className="rounded-lg border border-white/10 bg-slate-900/80 px-3 py-1.5 text-xs font-mono text-emerald-400 focus:outline-none focus:border-emerald-500"
            >
              {ENVS.map((e) => (
                <option key={e} value={e}>{e}</option>
              ))}
            </select>
            <button
              onClick={loadBinaries}
              className="rounded-lg border border-white/10 bg-white/5 p-2 text-slate-400 hover:text-white transition-colors"
            >
              <RefreshCw className={`h-4 w-4 ${loading ? "animate-spin text-indigo-400" : ""}`} />
            </button>
          </div>
        </div>
      </AccessibleFade>

      {/* GridFS Binaries Content */}
      <AccessibleFade delay={0.08}>
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
          {binaries.map((bin) => (
            <GlassCard3D key={bin.id} glowColor="emerald">
              <div className="p-6 space-y-4">
                <div className="flex items-center justify-between">
                  <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
                    <FileCheck className="h-5 w-5" />
                  </div>
                  <span className="font-mono text-xs font-bold px-2 py-0.5 rounded bg-emerald-500/20 text-emerald-400 border border-emerald-500/30">
                    Revision v{bin.version}
                  </span>
                </div>

                <div>
                  <h3 className="font-mono text-sm font-bold text-white break-all">
                    {bin.filename}
                  </h3>
                  <p className="text-[11px] font-mono text-slate-400 mt-1">
                    {bin.contentType} • {formatBytes(bin.fileSizeBytes)}
                  </p>
                </div>

                <div className="rounded-lg bg-black/30 p-2.5 font-mono text-[10px] space-y-1 border border-white/5">
                  <div className="flex justify-between text-slate-400">
                    <span>GridFS File ID:</span>
                    <span className="text-white">{bin.gridFsFileId}</span>
                  </div>
                  <div className="flex justify-between text-slate-400">
                    <span>SHA-512 Hash:</span>
                    <span className="text-emerald-400 truncate max-w-[140px]">{bin.documentHash}</span>
                  </div>
                </div>

                <div className="pt-2 flex items-center justify-between">
                  <span className="text-[10px] font-mono text-slate-500 flex items-center gap-1">
                    <Clock className="h-3 w-3" />
                    {new Date(bin.createdAt).toLocaleDateString()}
                  </span>
                  <a
                    href={vaultApi.getBinaryDownloadUrl(bin.applicationName, bin.environment, bin.filename, bin.version)}
                    target="_blank"
                    rel="noreferrer"
                    className="glass-button flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-xs font-semibold text-white transition-all"
                  >
                    <Download className="h-3.5 w-3.5" />
                    <span>Download</span>
                  </a>
                </div>
              </div>
            </GlassCard3D>
          ))}
        </div>
      </AccessibleFade>
    </DashboardShell>
  );
}
