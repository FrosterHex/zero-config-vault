"use client";

import React, { useId, useState } from "react";
import { AlertCircle, Check, Copy, KeyRound, Loader2, Plus } from "lucide-react";
import { vaultApi } from "@/lib/api";

/** Mirrors the backend ApiKeyRole enum. */
const ROLE_OPTIONS = ["READ", "WRITE", "ROLLBACK", "EXPORT", "REVEAL_SECRETS", "ADMIN"] as const;

const splitList = (value: string) =>
  value
    .split(",")
    .map((v) => v.trim())
    .filter(Boolean);

const inputClass =
  "h-8 w-full rounded-lg border border-white/10 bg-black/40 px-3 text-xs font-mono text-white placeholder-slate-600 focus:border-mint-500 focus:outline-none focus:ring-1 focus:ring-mint-500";

/** Admin form for issuing a scoped API key. The raw key is shown once, right after creation. */
export function CreateKeyForm({ onCreated }: { onCreated: () => void }) {
  const [name, setName] = useState("");
  const [apps, setApps] = useState("");
  const [envs, setEnvs] = useState("");
  const [roles, setRoles] = useState<string[]>(["READ"]);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [rawKey, setRawKey] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);
  const ids = { name: useId(), apps: useId(), envs: useId() };

  const toggleRole = (role: string) =>
    setRoles((prev) => (prev.includes(role) ? prev.filter((r) => r !== role) : [...prev, role]));

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setPending(true);
    setError(null);
    setRawKey(null);
    try {
      const created = await vaultApi.createApiKey({
        name: name.trim(),
        allowedApps: splitList(apps),
        allowedEnvironments: splitList(envs),
        roles,
      });
      setRawKey(created.rawKey);
      setCopied(false);
      setName("");
      setApps("");
      setEnvs("");
      setRoles(["READ"]);
      onCreated();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not create the key");
    } finally {
      setPending(false);
    }
  };

  const copyRawKey = () => {
    if (!rawKey) return;
    void navigator.clipboard.writeText(rawKey);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <form
      onSubmit={handleSubmit}
      className="space-y-4 rounded-2xl border border-white/10 bg-slate-900/60 p-5 backdrop-blur-md"
    >
      <div className="flex items-center gap-2 border-b border-white/10 pb-3">
        <KeyRound className="h-4 w-4 text-mint-400" aria-hidden="true" />
        <h2 className="font-sans text-xs font-bold uppercase tracking-wider text-white">
          Issue a scoped API key
        </h2>
      </div>

      <div className="grid grid-cols-1 gap-3 md:grid-cols-3">
        <div className="space-y-1">
          <label htmlFor={ids.name} className="block text-[10px] font-mono uppercase tracking-wider text-slate-400">
            Key name
          </label>
          <input id={ids.name} required value={name} onChange={(e) => setName(e.target.value)} placeholder="billing-service-ci" className={inputClass} />
        </div>
        <div className="space-y-1">
          <label htmlFor={ids.apps} className="block text-[10px] font-mono uppercase tracking-wider text-slate-400">
            Allowed apps (comma separated, * for all)
          </label>
          <input id={ids.apps} required value={apps} onChange={(e) => setApps(e.target.value)} placeholder="payment-gateway, order-service" className={inputClass} />
        </div>
        <div className="space-y-1">
          <label htmlFor={ids.envs} className="block text-[10px] font-mono uppercase tracking-wider text-slate-400">
            Allowed environments (comma separated, * for all)
          </label>
          <input id={ids.envs} required value={envs} onChange={(e) => setEnvs(e.target.value)} placeholder="staging, production" className={inputClass} />
        </div>
      </div>

      <fieldset className="space-y-1.5">
        <legend className="text-[10px] font-mono uppercase tracking-wider text-slate-400">Roles</legend>
        <div className="flex flex-wrap gap-2">
          {ROLE_OPTIONS.map((role) => {
            const checked = roles.includes(role);
            return (
              <label
                key={role}
                className={`inline-flex cursor-pointer items-center gap-1.5 rounded-lg border px-2.5 py-1 text-[11px] font-mono transition-colors has-[:focus-visible]:ring-2 has-[:focus-visible]:ring-mint-500 ${
                  checked
                    ? "border-mint-500/50 bg-mint-500/20 text-mint-200"
                    : "border-white/10 bg-white/5 text-slate-400 hover:text-white"
                }`}
              >
                <input type="checkbox" className="sr-only" checked={checked} onChange={() => toggleRole(role)} />
                {checked && <Check className="h-3 w-3" aria-hidden="true" />}
                <span>{role}</span>
              </label>
            );
          })}
        </div>
      </fieldset>

      <div className="flex flex-wrap items-center justify-between gap-3">
        {error ? (
          <p role="alert" className="flex items-center gap-1.5 text-xs text-rose-300">
            <AlertCircle className="h-3.5 w-3.5 text-rose-400" aria-hidden="true" />
            <span>{error}</span>
          </p>
        ) : (
          <span />
        )}
        <button
          type="submit"
          disabled={pending || roles.length === 0}
          className="inline-flex h-8 items-center gap-1.5 rounded-lg bg-mint-600 px-3.5 text-xs font-semibold text-white shadow-md shadow-mint-600/20 transition-colors hover:bg-mint-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-mint-400 disabled:cursor-not-allowed disabled:opacity-50"
        >
          {pending ? <Loader2 className="h-3.5 w-3.5 animate-spin" aria-hidden="true" /> : <Plus className="h-3.5 w-3.5" aria-hidden="true" />}
          <span>{pending ? "Creating…" : "Create key"}</span>
        </button>
      </div>

      {rawKey && (
        <div role="status" className="space-y-1.5 rounded-lg border border-emerald-500/30 bg-emerald-950/30 p-3">
          <p className="text-[11px] text-emerald-300">Copy this key now. It is shown only once.</p>
          <div className="flex items-center gap-2">
            <code className="flex-1 break-all rounded bg-black/40 px-2 py-1 font-mono text-xs text-white">{rawKey}</code>
            <button
              type="button"
              onClick={copyRawKey}
              aria-label="Copy the new API key"
              className="rounded p-1.5 text-slate-300 transition-colors hover:bg-white/10 hover:text-white"
            >
              {copied ? <Check className="h-3.5 w-3.5 text-emerald-400" /> : <Copy className="h-3.5 w-3.5" />}
            </button>
          </div>
        </div>
      )}
    </form>
  );
}
