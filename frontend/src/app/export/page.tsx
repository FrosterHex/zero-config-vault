"use client";

import React, { useState, useEffect, useCallback } from "react";
import { DashboardShell } from "@/components/dashboard/DashboardShell";
import { AccessibleFade } from "@/components/motion/AccessibleMotion";
import { GlassCard3D } from "@/components/ui/GlassCard3D";
import { vaultApi } from "@/lib/api";
import {
  FileCode,
  Download,
  Copy,
  Check,
  RefreshCw,
} from "lucide-react";

const APPS = ["payment-gateway", "order-service", "auth-service"];
const ENVS = ["production", "staging", "development"];
const FORMATS = ["json", "yaml", "env"];

export default function ExportPage() {
  const [selectedApp, setSelectedApp] = useState("payment-gateway");
  const [selectedEnv, setSelectedEnv] = useState("production");
  const [format, setFormat] = useState("yaml");
  const [exportedText, setExportedText] = useState<string>("");
  const [loading, setLoading] = useState(false);
  const [copied, setCopied] = useState(false);

  const loadExport = useCallback(async () => {
    setLoading(true);
    try {
      const exportUrl = vaultApi.exportConfig(selectedApp, selectedEnv, format);
      const res = await fetch(exportUrl, {
        headers: { "X-API-Key": "sk-dev-key-001" },
      });
      if (res.ok) {
        const text = await res.text();
        setExportedText(text);
      } else {
        setExportedText(`# Error loading export for ${selectedApp} in ${selectedEnv}`);
      }
    } catch {
      setExportedText(`# Sample YAML Export for ${selectedApp}
server:
  port: 8443
  timeoutMs: 3000
  ssl:
    enabled: true
    protocol: TLSv1.3
payment:
  currency: USD
  maxRetryAttempts: 5
  gateways:
    - stripe
    - adyen
    - paypal
database:
  host: db-cluster.internal.payment.prod
  port: 27017
  name: prod_payments`);
    } finally {
      setLoading(false);
    }
  }, [selectedApp, selectedEnv, format]);

  useEffect(() => {
    loadExport();
  }, [loadExport]);

  const handleCopy = () => {
    navigator.clipboard.writeText(exportedText);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const handleDownload = () => {
    const ext = format === "env" ? "env" : format;
    const blob = new Blob([exportedText], { type: "text/plain" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `${selectedApp}-${selectedEnv}.${ext}`;
    a.click();
    URL.revokeObjectURL(url);
  };

  return (
    <DashboardShell>
      <div className="pointer-events-none fixed inset-0 overflow-hidden -z-10 bg-grid-cyber">
        <div className="absolute top-1/4 left-1/3 h-80 w-80 rounded-full bg-purple-600/10 blur-[130px]" />
      </div>

      <AccessibleFade delay={0}>
        <div className="flex flex-col sm:flex-row sm:items-end justify-between gap-4 pb-4 border-b border-white/10">
          <div>
            <div className="flex items-center gap-2 mb-1">
              <span className="flex h-2 w-2 rounded-full bg-purple-400" />
              <span className="text-[11px] font-mono uppercase tracking-widest text-purple-400 font-semibold">
                Multi-Format Transformation Engine
              </span>
            </div>
            <h1 className="font-['Cormorant_Garamond',serif] text-4xl font-bold tracking-tight text-white">
              Export Studio
            </h1>
            <p className="text-xs sm:text-sm text-slate-400 font-['Montserrat',sans-serif] font-light mt-1 max-w-2xl">
              Transform live Vault configuration documents on-the-fly into structured JSON, YAML, or flat .env files
              for containerized microservices and CI/CD pipelines.
            </p>
          </div>

          <div className="flex flex-wrap items-center gap-3">
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
              className="rounded-lg border border-white/10 bg-slate-900/80 px-3 py-1.5 text-xs font-mono text-purple-400 focus:outline-none focus:border-purple-500"
            >
              {ENVS.map((e) => (
                <option key={e} value={e}>{e}</option>
              ))}
            </select>
            <div className="flex rounded-lg border border-white/10 bg-slate-900/80 p-0.5">
              {FORMATS.map((f) => (
                <button
                  key={f}
                  onClick={() => setFormat(f)}
                  className={`rounded-md px-2.5 py-1 text-xs font-mono font-bold uppercase transition-all ${
                    format === f
                      ? "bg-purple-600 text-white shadow-sm"
                      : "text-slate-400 hover:text-white"
                  }`}
                >
                  .{f}
                </button>
              ))}
            </div>
            <button
              onClick={loadExport}
              className="rounded-lg border border-white/10 bg-white/5 p-2 text-slate-400 hover:text-white transition-colors"
            >
              <RefreshCw className={`h-4 w-4 ${loading ? "animate-spin text-purple-400" : ""}`} />
            </button>
          </div>
        </div>
      </AccessibleFade>

      <AccessibleFade delay={0.08}>
        <GlassCard3D glowColor="purple">
          <div className="p-6 space-y-4">
            <div className="flex items-center justify-between border-b border-white/10 pb-4">
              <div className="flex items-center gap-3">
                <FileCode className="h-5 w-5 text-purple-400" />
                <span className="font-mono text-sm font-bold text-white">
                  {selectedApp}-{selectedEnv}.{format === "env" ? "env" : format}
                </span>
              </div>
              <div className="flex items-center gap-2">
                <button
                  onClick={handleCopy}
                  className="flex items-center gap-1.5 rounded-lg border border-white/10 bg-white/5 px-3 py-1.5 text-xs font-mono text-slate-300 hover:text-white hover:bg-white/10 transition-colors"
                >
                  {copied ? <Check className="h-3.5 w-3.5 text-emerald-400" /> : <Copy className="h-3.5 w-3.5" />}
                  <span>{copied ? "Copied" : "Copy"}</span>
                </button>
                <button
                  onClick={handleDownload}
                  className="glass-button flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-xs font-semibold text-white transition-all"
                >
                  <Download className="h-3.5 w-3.5" />
                  <span>Download</span>
                </button>
              </div>
            </div>

            <pre className="overflow-x-auto rounded-xl bg-slate-950/80 p-5 font-mono text-xs text-slate-300 border border-white/5 max-h-[500px] leading-relaxed">
              <code>{exportedText}</code>
            </pre>
          </div>
        </GlassCard3D>
      </AccessibleFade>
    </DashboardShell>
  );
}
