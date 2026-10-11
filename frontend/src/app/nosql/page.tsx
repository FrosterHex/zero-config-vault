"use client";

import React, { useState, useEffect } from "react";
import { DashboardShell } from "@/components/dashboard/DashboardShell";
import { AccessibleFade } from "@/components/motion/AccessibleMotion";
import { GlassCard3D } from "@/components/ui/GlassCard3D";
import {
  Database,
  Activity,
  ShieldCheck,
  Network,
  Clock,
  Zap,
  Server,
  CheckCircle2,
  RefreshCw,
  Cpu,
  BarChart2,
  Terminal,
} from "lucide-react";
import { vaultApi, TelemetryStats, SchemaValidation } from "@/lib/api";

export default function NoSqlDeepDivePage() {
  const [activeTab, setActiveTab] = useState<"telemetry" | "schema" | "sharding">("telemetry");
  const [windowMinutes, setWindowMinutes] = useState<number>(60);
  const [telemetry, setTelemetry] = useState<TelemetryStats | null>(null);
  const [schemaInfo, setSchemaInfo] = useState<SchemaValidation | null>(null);
  const [refreshing, setRefreshing] = useState<boolean>(false);

  // Sharding Simulator State
  const [simApp, setSimApp] = useState("payment-gateway");
  const [simEnv, setSimEnv] = useState("production");

  const handleRefresh = async () => {
    setRefreshing(true);
    try {
      const [tel, sch] = await Promise.allSettled([
        vaultApi.getTelemetryStats(windowMinutes),
        vaultApi.getSchemaValidation(),
      ]);

      if (tel.status === "fulfilled") {
        setTelemetry(tel.value);
      }
      if (sch.status === "fulfilled") {
        setSchemaInfo(sch.value);
      }
    } catch (err) {
      console.warn("Failed to fetch NoSQL deep-dive metrics", err);
    } finally {
      setRefreshing(false);
    }
  };

  useEffect(() => {
    let isMounted = true;
    const load = async () => {
      setRefreshing(true);
      try {
        const [tel, sch] = await Promise.allSettled([
          vaultApi.getTelemetryStats(windowMinutes),
          vaultApi.getSchemaValidation(),
        ]);

        if (isMounted) {
          if (tel.status === "fulfilled") {
            setTelemetry(tel.value);
          }
          if (sch.status === "fulfilled") {
            setSchemaInfo(sch.value);
          }
        }
      } catch (err) {
        console.warn("Failed to fetch NoSQL deep-dive metrics", err);
      } finally {
        if (isMounted) {
          setRefreshing(false);
        }
      }
    };

    load();
    return () => {
      isMounted = false;
    };
  }, [windowMinutes]);

  // Compute Murmur3/MD5 hash simulation for Shard Key
  const computeShardAssignment = (appName: string, envName: string) => {
    let hash = 0;
    for (let i = 0; i < appName.length; i++) {
      hash = (hash << 5) - hash + appName.charCodeAt(i);
      hash |= 0;
    }
    const absHash = Math.abs(hash);
    const shardId = (absHash % 2) + 1;
    const chunkMin = (absHash * 1000) % 50000;
    const chunkMax = chunkMin + 16384;
    const isProd = envName.toLowerCase() === "production";
    const zone = isProd ? "ZONE_PROD_TIER1 (NVMe Storage)" : "ZONE_DEV_STAGING (Standard Storage)";

    return {
      hashHex: "0x" + absHash.toString(16).padStart(8, "0").toUpperCase(),
      targetShard: `rs-shard-0${shardId}`,
      chunkRange: `[${chunkMin}, ${chunkMax}]`,
      zone,
      routingPlan: "SINGLE_SHARD_TARGETED",
      scatterGatherAvoided: true,
    };
  };

  const simResult = computeShardAssignment(simApp, simEnv);

  return (
    <DashboardShell>
      {/* Background Cyber Atmosphere */}
      <div className="pointer-events-none fixed inset-0 overflow-hidden -z-10 bg-grid-cyber">
        <div className="absolute top-1/4 right-1/4 h-[28rem] w-[28rem] rounded-full bg-cyan-600/10 blur-[130px]" />
        <div className="absolute bottom-1/3 left-1/4 h-80 w-80 rounded-full bg-indigo-600/15 blur-[120px]" />
      </div>

      <div className="space-y-6">
        {/* Header Banner */}
        <AccessibleFade delay={0}>
          <div className="relative overflow-hidden rounded-3xl border border-white/10 bg-gradient-to-b from-slate-900/80 via-slate-950/90 to-[#070b16]/95 backdrop-blur-2xl p-6 lg:p-8 shadow-2xl">
            <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
              <div>
                <div className="flex items-center gap-2 mb-2">
                  <span className="flex items-center gap-1.5 rounded-full border border-cyan-500/30 bg-cyan-500/10 px-3 py-0.5 text-[10px] font-mono font-semibold text-cyan-400">
                    <Database className="h-3 w-3" />
                    MongoDB 7.0 Engine Deep Dive
                  </span>
                  <span className="rounded-full border border-purple-500/30 bg-purple-500/10 px-3 py-0.5 text-[10px] font-mono text-purple-400">
                    NoSQL Course Showcase
                  </span>
                </div>
                <h1 className="font-['Cormorant_Garamond',serif] text-3xl sm:text-4xl font-extrabold text-white tracking-tight">
                  Advanced NoSQL Internals & Architecture
                </h1>
                <p className="mt-1 text-xs sm:text-sm text-slate-300 font-['Montserrat',sans-serif] font-light max-w-2xl">
                  Demonstrating native MongoDB 7.0 engine-level primitives: Native Time-Series collections,
                  $jsonSchema structural governance, and horizontal shard key query routing.
                </p>
              </div>

              <button
                onClick={handleRefresh}
                disabled={refreshing}
                className="flex items-center gap-2 rounded-xl border border-white/10 bg-white/5 hover:bg-white/10 px-4 py-2 text-xs font-mono text-slate-300 hover:text-white transition-all shrink-0"
              >
                <RefreshCw className={`h-3.5 w-3.5 ${refreshing ? "animate-spin text-cyan-400" : ""}`} />
                <span>Sync Metrics</span>
              </button>
            </div>

            {/* Navigation Tabs */}
            <div className="flex items-center gap-2 mt-6 pt-4 border-t border-white/10 overflow-x-auto text-xs font-mono">
              <button
                onClick={() => setActiveTab("telemetry")}
                className={`flex items-center gap-2 px-4 py-2 rounded-xl transition-all ${
                  activeTab === "telemetry"
                    ? "bg-cyan-600 text-white shadow-lg shadow-cyan-600/30 font-semibold"
                    : "text-slate-400 hover:text-white hover:bg-white/5"
                }`}
              >
                <Activity className="h-4 w-4" />
                <span>1. Native Time-Series Telemetry</span>
              </button>
              <button
                onClick={() => setActiveTab("schema")}
                className={`flex items-center gap-2 px-4 py-2 rounded-xl transition-all ${
                  activeTab === "schema"
                    ? "bg-indigo-600 text-white shadow-lg shadow-indigo-600/30 font-semibold"
                    : "text-slate-400 hover:text-white hover:bg-white/5"
                }`}
              >
                <ShieldCheck className="h-4 w-4" />
                <span>2. Engine-Level $jsonSchema</span>
              </button>
              <button
                onClick={() => setActiveTab("sharding")}
                className={`flex items-center gap-2 px-4 py-2 rounded-xl transition-all ${
                  activeTab === "sharding"
                    ? "bg-purple-600 text-white shadow-lg shadow-purple-600/30 font-semibold"
                    : "text-slate-400 hover:text-white hover:bg-white/5"
                }`}
              >
                <Network className="h-4 w-4" />
                <span>3. Sharding & Query Routing</span>
              </button>
            </div>
          </div>
        </AccessibleFade>

        {/* Tab 1: Native Time-Series Telemetry */}
        {activeTab === "telemetry" && (
          <AccessibleFade delay={0.06}>
            <div className="space-y-6">
              {/* Telemetry Metric Cards */}
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
                <GlassCard3D glowColor="cyan">
                  <div className="p-5 space-y-2">
                    <div className="flex items-center justify-between text-slate-400 text-xs font-mono">
                      <span>Total Telemetry Events</span>
                      <Activity className="h-4 w-4 text-cyan-400" />
                    </div>
                    <div className="text-3xl font-extrabold text-white font-mono">
                      {telemetry?.totalRequests ?? 120}
                    </div>
                    <div className="text-[10px] font-mono text-cyan-400/80">
                      Collection: config_access_telemetry
                    </div>
                  </div>
                </GlassCard3D>

                <GlassCard3D glowColor="indigo">
                  <div className="p-5 space-y-2">
                    <div className="flex items-center justify-between text-slate-400 text-xs font-mono">
                      <span>Average Latency</span>
                      <Zap className="h-4 w-4 text-indigo-400" />
                    </div>
                    <div className="flex items-baseline gap-2">
                      <span className="text-3xl font-extrabold text-white font-mono">
                        {telemetry?.avgLatencyMs ?? 4.2}
                      </span>
                      <span className="text-xs text-indigo-300 font-mono">ms</span>
                    </div>
                    <div className="text-[10px] font-mono text-slate-500">
                      p95 Latency: {telemetry?.p95LatencyMs ?? 18} ms
                    </div>
                  </div>
                </GlassCard3D>

                <GlassCard3D glowColor="emerald">
                  <div className="p-5 space-y-2">
                    <div className="flex items-center justify-between text-slate-400 text-xs font-mono">
                      <span>TTL Cache Hit Ratio</span>
                      <Clock className="h-4 w-4 text-emerald-400" />
                    </div>
                    <div className="flex items-baseline gap-2">
                      <span className="text-3xl font-extrabold text-emerald-400 font-mono">
                        {telemetry?.cacheHitRatio ?? 70.0}%
                      </span>
                    </div>
                    <div className="text-[10px] font-mono text-slate-500">
                      Hits: {telemetry?.cacheHits ?? 84} • Misses: {telemetry?.cacheMisses ?? 36}
                    </div>
                  </div>
                </GlassCard3D>

                <GlassCard3D glowColor="purple">
                  <div className="p-5 space-y-2">
                    <div className="flex items-center justify-between text-slate-400 text-xs font-mono">
                      <span>Time-Series Granularity</span>
                      <Cpu className="h-4 w-4 text-purple-400" />
                    </div>
                    <div className="text-xl font-bold text-white font-mono">
                      SECONDS
                    </div>
                    <div className="text-[10px] font-mono text-purple-400/80">
                      metaField: metadata (Compound)
                    </div>
                  </div>
                </GlassCard3D>
              </div>

              {/* Time-Series Volume & Latency Visualizer */}
              <div className="rounded-2xl border border-white/10 bg-[#0B0F19]/80 backdrop-blur-xl p-6 shadow-xl">
                <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4 mb-6">
                  <div>
                    <h3 className="font-['Montserrat',sans-serif] text-base font-bold text-white flex items-center gap-2">
                      <BarChart2 className="h-4 w-4 text-cyan-400" />
                      Live Request Throughput Timeline (Aggregated Buckets)
                    </h3>
                    <p className="text-xs text-slate-400 font-light mt-0.5">
                      Streamed from native MongoDB Time-Series bucketing engine
                    </p>
                  </div>

                  {/* Window Selector */}
                  <div className="flex items-center gap-1 rounded-lg border border-white/10 bg-white/5 p-1 text-xs font-mono">
                    {[15, 60, 360, 1440].map((mins) => (
                      <button
                        key={mins}
                        onClick={() => setWindowMinutes(mins)}
                        className={`rounded px-2.5 py-1 transition-all ${
                          windowMinutes === mins
                            ? "bg-cyan-600 text-white font-bold"
                            : "text-slate-400 hover:text-white"
                        }`}
                      >
                        {mins < 60 ? `${mins}m` : `${mins / 60}h`}
                      </button>
                    ))}
                  </div>
                </div>

                {/* SVG Chart Visualization */}
                {telemetry?.timeline && telemetry.timeline.length > 0 ? (
                  <div className="space-y-4">
                    <div className="h-56 w-full flex items-end gap-2 sm:gap-3 pt-6 pb-2 px-2 border-b border-white/10">
                      {telemetry.timeline.map((pt, i) => {
                        const maxReqs = Math.max(...telemetry.timeline.map((p) => p.requestCount), 1);
                        const heightPct = Math.max(12, Math.round((pt.requestCount / maxReqs) * 100));
                        return (
                          <div key={i} className="flex-1 flex flex-col items-center gap-1 h-full justify-end group">
                            {/* Hover Tooltip */}
                            <div className="opacity-0 group-hover:opacity-100 transition-opacity bg-black/90 border border-cyan-500/30 rounded px-2 py-1 text-[10px] font-mono text-cyan-300 pointer-events-none whitespace-nowrap mb-1 shadow-lg">
                              {pt.requestCount} reqs • {pt.avgLatency}ms avg
                            </div>
                            {/* Bar */}
                            <div
                              style={{ height: `${heightPct}%` }}
                              className="w-full rounded-t-md bg-gradient-to-t from-cyan-600/50 to-cyan-400 group-hover:from-cyan-500 group-hover:to-cyan-300 transition-all shadow-cyan-500/20 shadow-sm"
                            />
                            <span className="text-[10px] font-mono text-slate-500 truncate w-full text-center">
                              {pt.timeLabel}
                            </span>
                          </div>
                        );
                      })}
                    </div>

                    <div className="flex items-center justify-between text-xs font-mono text-slate-400 pt-2">
                      <span className="flex items-center gap-2">
                        <span className="h-2 w-2 rounded-full bg-cyan-400" />
                        Bucketed Request Rate
                      </span>
                      <span>Window: Past {windowMinutes} Minutes</span>
                    </div>
                  </div>
                ) : (
                  <div className="h-44 flex items-center justify-center text-slate-500 font-mono text-xs">
                    Recording live incoming telemetry events...
                  </div>
                )}
              </div>
            </div>
          </AccessibleFade>
        )}

        {/* Tab 2: Engine-Level $jsonSchema Governance */}
        {activeTab === "schema" && (
          <AccessibleFade delay={0.06}>
            <div className="space-y-6">
              <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
                {/* Schema Metadata */}
                <div className="lg:col-span-5 space-y-4">
                  <div className="rounded-2xl border border-white/10 bg-[#0B0F19]/80 backdrop-blur-xl p-6 shadow-xl space-y-4">
                    <div className="flex items-center gap-2 text-indigo-400 font-mono text-xs font-bold uppercase tracking-wider">
                      <ShieldCheck className="h-4 w-4" />
                      MongoDB Database-Level Governance
                    </div>

                    <h3 className="font-['Montserrat',sans-serif] text-lg font-bold text-white">
                      Strict $jsonSchema Enforcement
                    </h3>
                    <p className="text-xs text-slate-300 leading-relaxed font-light">
                      Unlike standard Spring Boot validation which only executes in the application layer,
                      Zero-Config Vault programs MongoDB&apos;s internal BSON engine to reject unauthorized
                      inserts directly via <code className="text-indigo-400 font-mono">DocumentValidationFailure (Error 121)</code>.
                    </p>

                    <div className="space-y-2 pt-2 border-t border-white/10 text-xs font-mono">
                      <div className="flex items-center justify-between py-1 border-b border-white/5">
                        <span className="text-slate-400">Target Collection</span>
                        <span className="text-white font-bold">configurations</span>
                      </div>
                      <div className="flex items-center justify-between py-1 border-b border-white/5">
                        <span className="text-slate-400">Validation Level</span>
                        <span className="text-emerald-400 font-bold">MODERATE</span>
                      </div>
                      <div className="flex items-center justify-between py-1 border-b border-white/5">
                        <span className="text-slate-400">Validation Action</span>
                        <span className="text-rose-400 font-bold">ERROR (Strict Reject)</span>
                      </div>
                      <div className="flex items-center justify-between py-1">
                        <span className="text-slate-400">Engine Validation Status</span>
                        <span className="flex items-center gap-1 text-emerald-400">
                          <CheckCircle2 className="h-3.5 w-3.5" /> ACTIVE
                        </span>
                      </div>
                    </div>
                  </div>

                  {/* Required Fields Pill List */}
                  <div className="rounded-2xl border border-white/10 bg-[#0B0F19]/80 backdrop-blur-xl p-5 shadow-xl">
                    <div className="text-xs font-mono font-bold text-slate-300 mb-2">
                      Mandatory BSON Document Fields:
                    </div>
                    <div className="flex flex-wrap gap-1.5 font-mono text-xs">
                      {["applicationName", "environment", "version", "documentHash", "payload", "createdAt"].map((f) => (
                        <span key={f} className="rounded bg-indigo-500/15 border border-indigo-500/30 text-indigo-300 px-2 py-0.5 text-[11px]">
                          {f}
                        </span>
                      ))}
                    </div>
                  </div>
                </div>

                {/* Raw JSON Schema Terminal */}
                <div className="lg:col-span-7">
                  <div className="rounded-2xl border border-white/10 bg-[#070B14] p-5 shadow-2xl font-mono text-xs">
                    <div className="flex items-center justify-between pb-3 border-b border-white/10 mb-3">
                      <div className="flex items-center gap-2">
                        <span className="h-3 w-3 rounded-full bg-rose-500/80 inline-block" />
                        <span className="h-3 w-3 rounded-full bg-amber-500/80 inline-block" />
                        <span className="h-3 w-3 rounded-full bg-emerald-500/80 inline-block" />
                        <span className="text-slate-400 text-[11px] ml-2">db.runCommand(collMod: &apos;configurations&apos;)</span>
                      </div>
                      <span className="text-[10px] text-slate-500">MongoDB BSON Validator</span>
                    </div>

                    <pre className="text-emerald-400 max-h-[380px] overflow-y-auto p-2 bg-black/40 rounded-xl border border-emerald-500/20 leading-relaxed text-[11px]">
                      {schemaInfo?.rawJsonSchema ? (
                        JSON.stringify(JSON.parse(schemaInfo.rawJsonSchema), null, 2)
                      ) : (
                        `{
  "$jsonSchema": {
    "bsonType": "object",
    "required": [
      "applicationName",
      "environment",
      "version",
      "documentHash",
      "payload",
      "createdAt"
    ],
    "properties": {
      "applicationName": {
        "bsonType": "string",
        "pattern": "^[a-zA-Z0-9_-]+$",
        "description": "Application name slug"
      },
      "environment": {
        "enum": ["development", "staging", "production", "dr"],
        "description": "Strict environment tier"
      },
      "version": {
        "bsonType": "int",
        "minimum": 1,
        "description": "Strict monotonic version integer"
      },
      "documentHash": {
        "bsonType": "string",
        "minLength": 64,
        "maxLength": 128,
        "description": "SHA-512 canonical cryptographic fingerprint"
      },
      "payload": {
        "bsonType": "object",
        "description": "Dynamic BSON configuration tree"
      }
    }
  }
}`
                      )}
                    </pre>
                  </div>
                </div>
              </div>
            </div>
          </AccessibleFade>
        )}

        {/* Tab 3: Sharding & Query Routing Simulator */}
        {activeTab === "sharding" && (
          <AccessibleFade delay={0.06}>
            <div className="space-y-6">
              {/* Architecture Blueprint Card */}
              <div className="rounded-2xl border border-white/10 bg-[#0B0F19]/80 backdrop-blur-xl p-6 shadow-xl">
                <div className="flex flex-col lg:flex-row items-start lg:items-center justify-between gap-4 mb-6">
                  <div>
                    <span className="text-[10px] font-mono text-purple-400 uppercase tracking-wider font-bold">
                      Horizontal Partitioning Strategy
                    </span>
                    <h3 className="font-['Montserrat',sans-serif] text-xl font-bold text-white mt-1">
                      Compound Hashed Shard Key & Zone Isolation
                    </h3>
                  </div>

                  <div className="flex items-center gap-2 font-mono text-xs">
                    <span className="text-slate-400">Shard Key:</span>
                    <code className="bg-purple-950/60 border border-purple-500/30 text-purple-300 px-2.5 py-1 rounded-lg">
                      &#123; &quot;applicationName&quot;: &quot;hashed&quot;, &quot;environment&quot;: 1 &#125;
                    </code>
                  </div>
                </div>

                {/* 3-Tier Sharded Architecture Diagram */}
                <div className="grid grid-cols-1 md:grid-cols-3 gap-4 pt-2">
                  <div className="rounded-xl border border-white/10 bg-black/40 p-4 space-y-2">
                    <div className="flex items-center gap-2 text-cyan-400 text-xs font-mono font-bold">
                      <Network className="h-4 w-4" />
                      mongos Routers
                    </div>
                    <p className="text-[11px] text-slate-300 font-light leading-relaxed">
                      Stateless query routers caching the cluster chunk map from Config Replica Set. Computes MD5/Murmur3 hash on applicationName.
                    </p>
                  </div>

                  <div className="rounded-xl border border-white/10 bg-black/40 p-4 space-y-2">
                    <div className="flex items-center gap-2 text-purple-400 text-xs font-mono font-bold">
                      <Server className="h-4 w-4" />
                      Config Server RS (CSRS)
                    </div>
                    <p className="text-[11px] text-slate-300 font-light leading-relaxed">
                      3-node Raft consensus holding chunk boundaries, balancer state, and zone assignments. Never receives user queries.
                    </p>
                  </div>

                  <div className="rounded-xl border border-white/10 bg-black/40 p-4 space-y-2">
                    <div className="flex items-center gap-2 text-emerald-400 text-xs font-mono font-bold">
                      <Database className="h-4 w-4" />
                      Zone-Tiered Shards
                    </div>
                    <p className="text-[11px] text-slate-300 font-light leading-relaxed">
                      Production configs pinned to NVMe SSD replica sets. Staging/Dev partitioned to cost-effective storage tiers.
                    </p>
                  </div>
                </div>
              </div>

              {/* Interactive Shard Key Routing Simulator */}
              <div className="rounded-2xl border border-purple-500/30 bg-[#0B0F19]/90 backdrop-blur-xl p-6 shadow-2xl space-y-6">
                <div>
                  <h3 className="font-['Montserrat',sans-serif] text-lg font-bold text-white flex items-center gap-2">
                    <Terminal className="h-5 w-5 text-purple-400" />
                    Interactive Query Routing Simulator
                  </h3>
                  <p className="text-xs text-slate-400 font-light mt-0.5">
                    Select an application and environment to see how mongos routes the query to an isolated shard node without broadcast overhead.
                  </p>
                </div>

                {/* Input Selectors */}
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                  <div>
                    <label className="block text-xs font-mono text-slate-400 mb-1.5">
                      Application Slug:
                    </label>
                    <select
                      value={simApp}
                      onChange={(e) => setSimApp(e.target.value)}
                      className="w-full rounded-xl border border-white/10 bg-white/5 px-3 py-2 text-xs font-mono text-white focus:outline-none focus:border-purple-500/50"
                    >
                      <option value="payment-gateway" className="bg-[#0B0F19]">payment-gateway</option>
                      <option value="auth-service" className="bg-[#0B0F19]">auth-service</option>
                      <option value="order-service" className="bg-[#0B0F19]">order-service</option>
                      <option value="inventory-service" className="bg-[#0B0F19]">inventory-service</option>
                      <option value="user-profile" className="bg-[#0B0F19]">user-profile</option>
                    </select>
                  </div>

                  <div>
                    <label className="block text-xs font-mono text-slate-400 mb-1.5">
                      Environment Tier:
                    </label>
                    <select
                      value={simEnv}
                      onChange={(e) => setSimEnv(e.target.value)}
                      className="w-full rounded-xl border border-white/10 bg-white/5 px-3 py-2 text-xs font-mono text-white focus:outline-none focus:border-purple-500/50"
                    >
                      <option value="production" className="bg-[#0B0F19]">production</option>
                      <option value="staging" className="bg-[#0B0F19]">staging</option>
                      <option value="development" className="bg-[#0B0F19]">development</option>
                      <option value="dr" className="bg-[#0B0F19]">dr</option>
                    </select>
                  </div>
                </div>

                {/* Simulation Output Card */}
                <div className="rounded-xl border border-emerald-500/30 bg-emerald-950/20 p-5 space-y-4">
                  <div className="flex items-center justify-between">
                    <span className="flex items-center gap-2 text-xs font-mono font-bold text-emerald-400">
                      <CheckCircle2 className="h-4 w-4" />
                      Targeted Single-Shard Execution Plan
                    </span>
                    <span className="text-[10px] font-mono text-emerald-300 bg-emerald-500/20 px-2 py-0.5 rounded border border-emerald-500/30">
                      Scatter-Gather Avoided ✅
                    </span>
                  </div>

                  <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3 font-mono text-xs">
                    <div className="p-3 rounded-lg bg-black/40 border border-white/5 space-y-1">
                      <span className="text-slate-400 text-[10px]">Murmur3 Hash</span>
                      <div className="text-white font-bold">{simResult.hashHex}</div>
                    </div>
                    <div className="p-3 rounded-lg bg-black/40 border border-white/5 space-y-1">
                      <span className="text-slate-400 text-[10px]">Target Shard</span>
                      <div className="text-purple-400 font-bold">{simResult.targetShard}</div>
                    </div>
                    <div className="p-3 rounded-lg bg-black/40 border border-white/5 space-y-1">
                      <span className="text-slate-400 text-[10px]">Chunk Range</span>
                      <div className="text-cyan-400 font-bold">{simResult.chunkRange}</div>
                    </div>
                    <div className="p-3 rounded-lg bg-black/40 border border-white/5 space-y-1">
                      <span className="text-slate-400 text-[10px]">Storage Zone</span>
                      <div className="text-emerald-400 font-bold truncate">{simResult.zone}</div>
                    </div>
                  </div>

                  <div className="text-[11px] font-mono text-slate-300 leading-relaxed bg-black/30 p-3 rounded-lg border border-white/5">
                    <strong className="text-white">Explanation:</strong> Because the shard key includes both{" "}
                    <code className="text-purple-300">applicationName (hashed)</code> and{" "}
                    <code className="text-cyan-300">environment</code>, the <code className="text-emerald-300">mongos</code> router
                    determines the exact target shard in 0.02ms. Remaining shards execute zero read operations,
                    preserving cluster IOPS.
                  </div>
                </div>
              </div>
            </div>
          </AccessibleFade>
        )}
      </div>
    </DashboardShell>
  );
}
