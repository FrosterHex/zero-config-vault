"use client";

import React, { useState, useEffect, useCallback, useRef } from "react";
import { DashboardShell } from "@/components/dashboard/DashboardShell";
import { AccessibleFade } from "@/components/motion/AccessibleMotion";
import { GlassCard3D } from "@/components/ui/GlassCard3D";
import { useVault, useSelectedScope, Environment } from "@/context/VaultContext";
import { vaultApi } from "@/lib/api";
import { GuestWriteNotice, GUEST_WRITE_HINT } from "@/components/auth/GuestWriteNotice";
import {
  Download,
  FileCheck,
  Clock,
  RefreshCw,
  UploadCloud,
  FileText,
  ShieldCheck,
  CheckCircle2,
  AlertCircle,
  HardDrive,
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

export default function BinariesPage() {
  const { refreshKey, apps, envs, role, canWrite } = useVault();
  const { selectedApp, selectedEnv, selectApp, selectEnv } = useSelectedScope(null, null);
  const [binaries, setBinaries] = useState<BinaryItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);
  const loadSeq = useRef(0);

  // Upload State
  const [uploadFile, setUploadFile] = useState<File | null>(null);
  const [uploading, setUploading] = useState(false);
  const [uploadSuccess, setUploadSuccess] = useState<string | null>(null);
  const [uploadError, setUploadError] = useState<string | null>(null);
  const [isDragOver, setIsDragOver] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const loadBinaries = useCallback(async () => {
    const seq = ++loadSeq.current;
    setLoading(true);
    try {
      const data = await vaultApi.listBinaries(selectedApp, selectedEnv);
      if (seq !== loadSeq.current) return;
      setBinaries(data || []);
      setLoadError(null);
    } catch (err) {
      if (seq !== loadSeq.current) return;
      setBinaries([]);
      setLoadError(`Could not load files for ${selectedApp} [${selectedEnv}]: ${err instanceof Error ? err.message : "unknown error"}`);
    } finally {
      if (seq === loadSeq.current) setLoading(false);
    }
  }, [selectedApp, selectedEnv]);

  useEffect(() => {
    loadBinaries();
  }, [loadBinaries, refreshKey]);

  // Handle file drop
  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault();
    setIsDragOver(false);
    if (e.dataTransfer.files && e.dataTransfer.files.length > 0) {
      setUploadFile(e.dataTransfer.files[0]);
    }
  };

  // Handle file upload
  const handleUploadSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!uploadFile || !canWrite(selectedApp)) return;

    setUploading(true);
    setUploadSuccess(null);
    setUploadError(null);

    try {
      const res = await vaultApi.uploadBinary(selectedApp, selectedEnv, uploadFile, {
        uploadedBy: role === "admin" ? "console-admin" : "console-guest",
      });
      setUploadSuccess(`Successfully uploaded "${res.filename}" as revision v${res.version} to GridFS!`);
      setUploadFile(null);
      if (fileInputRef.current) fileInputRef.current.value = "";
      await loadBinaries();
    } catch (e: unknown) {
      const err = e as Error;
      setUploadError(err.message || "Upload failed");
    } finally {
      setUploading(false);
    }
  };

  const writable = canWrite(selectedApp);
  const isAdmin = role === "admin";

  const switchToPlayground = () => {
    selectApp("playground");
    selectEnv("development");
  };

  const formatBytes = (bytes: number) => {
    if (!bytes || bytes === 0) return "0 Bytes";
    const k = 1024;
    const sizes = ["Bytes", "KB", "MB", "GB"];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + " " + sizes[i];
  };

  return (
    <DashboardShell>
      {/* 3D Cyber Atmosphere Background Elements */}
      <div className="pointer-events-none fixed inset-0 overflow-hidden -z-10 bg-grid-cyber">
        <div className="absolute top-1/4 right-1/4 h-80 w-80 rounded-full bg-emerald-600/15 blur-[120px]" />
        <div className="absolute top-1/3 left-1/4 h-80 w-80 rounded-full bg-cyan-600/10 blur-[130px]" />
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
              Store, version and download binary files (TLS certificates, Java .jks keystores, PEM bundles) in GridFS.
              Each upload is a new version with a SHA-512 hash; files are stored as uploaded and are not encrypted by the vault.
            </p>
          </div>

          <div className="flex flex-wrap items-center gap-3">
            <div className="flex items-center gap-1.5 rounded-lg border border-white/10 bg-slate-900/80 px-2.5 py-1 text-xs">
              <span className="text-slate-400 font-mono text-[11px]">App:</span>
              <select
                value={selectedApp}
                onChange={(e) => selectApp(e.target.value)}
                className="bg-transparent font-mono text-white text-xs font-semibold focus:outline-none cursor-pointer"
              >
                {apps.map((a) => (
                  <option key={a} value={a} className="bg-slate-900 text-white">
                    {a}
                  </option>
                ))}
              </select>
            </div>

            <div className="flex items-center gap-1.5 rounded-lg border border-white/10 bg-slate-900/80 px-2.5 py-1 text-xs">
              <span className="text-slate-400 font-mono text-[11px]">Env:</span>
              <select
                value={selectedEnv}
                onChange={(e) => selectEnv(e.target.value as Environment)}
                className="bg-transparent font-mono text-emerald-400 text-xs font-semibold focus:outline-none cursor-pointer"
              >
                {envs.map((env) => (
                  <option key={env} value={env} className="bg-slate-900 text-white">
                    {env}
                  </option>
                ))}
              </select>
            </div>

            <button
              type="button"
              onClick={loadBinaries}
              title="Reload files"
              aria-label="Reload files"
              className="rounded-lg border border-white/10 bg-white/5 p-2 text-slate-400 hover:text-white transition-colors"
            >
              <RefreshCw className={`h-4 w-4 ${loading ? "animate-spin text-emerald-400" : ""}`} />
            </button>
          </div>
        </div>
      </AccessibleFade>

      {/* 3D Glassmorphic Drag & Drop Upload Zone */}
      <AccessibleFade delay={0.05}>
        {!writable && (
          <div className="mb-3">
            <GuestWriteNotice onSwitchToPlayground={switchToPlayground} />
          </div>
        )}
        <GlassCard3D glowColor="emerald">
          <form onSubmit={handleUploadSubmit} className="p-6 space-y-4">
            <div className="flex items-center justify-between border-b border-white/10 pb-3">
              <div className="flex items-center gap-2">
                <UploadCloud className="h-4 w-4 text-emerald-400" />
                <h3 className="font-['Montserrat',sans-serif] text-xs font-bold uppercase tracking-wider text-white">
                  Upload Binary Keystore or Certificate to GridFS
                </h3>
              </div>
              <span className="text-[10px] font-mono text-slate-400">
                Target: {selectedApp} [{selectedEnv}]
              </span>
            </div>

            <div
              onDragOver={(e) => {
                e.preventDefault();
                setIsDragOver(true);
              }}
              onDragLeave={() => setIsDragOver(false)}
              onDrop={handleDrop}
              onClick={() => fileInputRef.current?.click()}
              className={`border-2 border-dashed rounded-2xl p-6 text-center cursor-pointer transition-all ${
                isDragOver
                  ? "border-emerald-400 bg-emerald-500/10 shadow-lg shadow-emerald-500/20"
                  : "border-white/15 bg-black/20 hover:border-emerald-500/40 hover:bg-black/30"
              }`}
            >
              <input
                ref={fileInputRef}
                type="file"
                className="hidden"
                onChange={(e) => {
                  if (e.target.files && e.target.files.length > 0) {
                    setUploadFile(e.target.files[0]);
                  }
                }}
              />
              <div className="flex flex-col items-center justify-center space-y-2">
                <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
                  <FileText className="h-6 w-6" />
                </div>
                {uploadFile ? (
                  <div className="space-y-0.5">
                    <p className="font-mono text-sm font-bold text-emerald-300">{uploadFile.name}</p>
                    <p className="font-mono text-xs text-slate-400">
                      {formatBytes(uploadFile.size)} • Ready for GridFS ingestion
                    </p>
                  </div>
                ) : (
                  <div>
                    <p className="font-['Montserrat',sans-serif] text-xs font-semibold text-white">
                      Click to browse or drag & drop binary files here
                    </p>
                    <p className="text-[11px] font-mono text-slate-500 mt-1">
                      Supports: .pem, .crt, .jks, .p12, .key, .json, .env, .bin (chunked to 255KB BSON chunks)
                    </p>
                  </div>
                )}
              </div>
            </div>

            <div className="flex flex-wrap items-center justify-between gap-3 pt-1">
              <div className="flex items-center gap-2 text-[11px] font-mono text-slate-400">
                <ShieldCheck className="h-4 w-4 text-emerald-400" />
                <span>Each version gets a SHA-512 hash • files are stored as uploaded, not encrypted</span>
              </div>

              <button
                type="submit"
                disabled={!uploadFile || uploading || !writable}
                title={writable ? undefined : GUEST_WRITE_HINT}
                className="glass-button flex items-center gap-2 rounded-xl px-4 py-2 text-xs font-semibold text-white transition-all disabled:opacity-40 disabled:cursor-not-allowed"
              >
                {uploading ? (
                  <>
                    <RefreshCw className="h-3.5 w-3.5 animate-spin" />
                    <span>Streaming to MongoDB GridFS...</span>
                  </>
                ) : (
                  <>
                    <UploadCloud className="h-3.5 w-3.5" />
                    <span>Commit to GridFS Vault</span>
                  </>
                )}
              </button>
            </div>

            {uploadSuccess && (
              <div className="rounded-lg border border-emerald-500/30 bg-emerald-950/40 p-3 text-xs font-mono text-emerald-300 flex items-center gap-2">
                <CheckCircle2 className="h-4 w-4 text-emerald-400 shrink-0" />
                <span>{uploadSuccess}</span>
              </div>
            )}

            {uploadError && (
              <div className="rounded-lg border border-rose-500/30 bg-rose-950/40 p-3 text-xs font-mono text-rose-300 flex items-center gap-2">
                <AlertCircle className="h-4 w-4 text-rose-400 shrink-0" />
                <span>{uploadError}</span>
              </div>
            )}
          </form>
        </GlassCard3D>
      </AccessibleFade>

      {/* GridFS Binaries Content Grid or Empty State */}
      <AccessibleFade delay={0.08}>
        {loadError ? (
          <div role="alert" className="flex items-center gap-2 rounded-xl border border-rose-500/30 bg-rose-950/40 p-4 text-xs font-mono text-rose-300">
            <AlertCircle className="h-4 w-4 shrink-0 text-rose-400" aria-hidden="true" />
            <span>{loadError}</span>
          </div>
        ) : binaries.length === 0 ? (
          <GlassCard3D glowColor="emerald">
            <div className="p-10 flex flex-col items-center justify-center text-center space-y-3 font-mono text-xs">
              <HardDrive className="h-10 w-10 text-slate-500" />
              <h3 className="font-bold text-white text-sm">No Binary Keystores in {selectedApp} [{selectedEnv}]</h3>
              <p className="text-slate-400 max-w-md">
                No certificate or keystore blobs have been stored in GridFS for this scope yet.
                Use the upload form above to add a TLS certificate or Java keystore!
              </p>
              <button
                type="button"
                onClick={() => {
                  selectApp("payment-gateway");
                  selectEnv("production");
                }}
                className="rounded-lg border border-white/10 bg-white/5 hover:bg-white/10 px-4 py-2 text-xs font-mono text-emerald-300 transition-colors mt-2"
              >
                Switch to payment-gateway [production] (Contains 2 verified certificates)
              </button>
            </div>
          </GlassCard3D>
        ) : (
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
                      <span className="text-white truncate max-w-[140px]">{bin.gridFsFileId}</span>
                    </div>
                    <div className="flex justify-between text-slate-400">
                      <span>SHA-512 Hash:</span>
                      <span className="text-emerald-400 truncate max-w-[140px]" title={bin.documentHash}>
                        {bin.documentHash ? `${bin.documentHash.substring(0, 16)}...` : "—"}
                      </span>
                    </div>
                  </div>

                  <div className="pt-2 flex items-center justify-between border-t border-white/5">
                    <span className="text-[10px] font-mono text-slate-500 flex items-center gap-1">
                      <Clock className="h-3 w-3" />
                      {new Date(bin.createdAt).toLocaleDateString()}
                    </span>
                    {isAdmin && (
                      <a
                        href={vaultApi.getBinaryDownloadUrl(bin.applicationName, bin.environment, bin.filename, bin.version)}
                        target="_blank"
                        rel="noreferrer"
                        className="glass-button flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-xs font-semibold text-white transition-all"
                      >
                        <Download className="h-3.5 w-3.5" />
                        <span>Download</span>
                      </a>
                    )}
                  </div>
                </div>
              </GlassCard3D>
            ))}
          </div>
        )}
      </AccessibleFade>
    </DashboardShell>
  );
}
