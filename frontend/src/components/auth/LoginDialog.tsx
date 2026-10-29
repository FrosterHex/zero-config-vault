"use client";

import React, { useEffect, useId, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { AnimatePresence, motion } from "framer-motion";
import { AlertCircle, KeyRound, Loader2, Lock, X } from "lucide-react";
import { authApi } from "@/lib/api";
import { useVault } from "@/context/VaultContext";

interface LoginDialogProps {
  open: boolean;
  onClose: () => void;
  /** Called after a successful sign-in, just before onClose. */
  onSignedIn?: () => void;
}

/**
 * Modal admin sign-in. Focus moves to the password field on open and stays inside the dialog;
 * Escape or the close button dismisses it, and focus returns to whatever opened it.
 * Rendered into document.body so a blurred ancestor (the header) cannot clip its fixed overlay.
 */
export function LoginDialog({ open, onClose, onSignedIn }: LoginDialogProps) {
  const { refreshRole, triggerRefresh } = useVault();
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);
  const dialogRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  const titleId = useId();
  const descriptionId = useId();
  const passwordId = useId();
  const errorId = useId();
  const [mounted, setMounted] = useState(false);

  useEffect(() => setMounted(true), []);

  // Reset the form, focus the password field, and restore focus to the opener on close.
  useEffect(() => {
    if (!open) return;
    const opener = document.activeElement as HTMLElement | null;
    setPassword("");
    setError(null);
    setPending(false);
    const frame = requestAnimationFrame(() => inputRef.current?.focus());
    return () => {
      cancelAnimationFrame(frame);
      // After sign-in the opener is replaced; the parent moves focus via onSignedIn instead.
      if (opener?.isConnected) opener.focus();
    };
  }, [open]);

  const handleKeyDown = (e: React.KeyboardEvent<HTMLDivElement>) => {
    if (e.key === "Escape") {
      e.stopPropagation();
      onClose();
      return;
    }
    if (e.key !== "Tab" || !dialogRef.current) return;
    const focusable = Array.from(
      dialogRef.current.querySelectorAll<HTMLElement>("button:not([disabled]), input:not([disabled])")
    );
    if (focusable.length === 0) return;
    const first = focusable[0];
    const last = focusable[focusable.length - 1];
    if (e.shiftKey && document.activeElement === first) {
      e.preventDefault();
      last.focus();
    } else if (!e.shiftKey && document.activeElement === last) {
      e.preventDefault();
      first.focus();
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!password || pending) return;
    setPending(true);
    setError(null);
    try {
      await authApi.login(password);
      await refreshRole();
      triggerRefresh();
      onSignedIn?.();
      onClose();
    } catch (err) {
      setError(err instanceof Error && err.message ? err.message : "Sign-in failed. Please try again.");
      setPending(false);
      requestAnimationFrame(() => {
        inputRef.current?.focus();
        inputRef.current?.select();
      });
    }
  };

  if (!mounted) return null;

  return createPortal(
    <AnimatePresence>
      {open && (
        <motion.div
          key="login-backdrop"
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 px-4 backdrop-blur-md"
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          transition={{ duration: 0.15 }}
          onMouseDown={(e) => {
            if (e.target === e.currentTarget) onClose();
          }}
        >
          <motion.div
            ref={dialogRef}
            role="dialog"
            aria-modal="true"
            aria-labelledby={titleId}
            aria-describedby={descriptionId}
            onKeyDown={handleKeyDown}
            initial={{ opacity: 0, y: 12, scale: 0.98 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: 8, scale: 0.98 }}
            transition={{ duration: 0.18, ease: "easeOut" }}
            className="relative w-full max-w-sm overflow-hidden rounded-2xl border border-white/10 bg-[#0B0F19]/95 shadow-2xl shadow-indigo-950/50"
          >
            <div className="pointer-events-none absolute -top-16 left-1/2 h-32 w-56 -translate-x-1/2 rounded-full bg-indigo-600/20 blur-3xl" />

            <button
              type="button"
              onClick={onClose}
              aria-label="Close sign-in dialog"
              className="absolute right-3 top-3 flex h-8 w-8 items-center justify-center rounded-lg text-slate-400 transition-colors hover:bg-white/10 hover:text-white focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500"
            >
              <X className="h-4 w-4" />
            </button>

            <form onSubmit={handleSubmit} aria-busy={pending} className="relative space-y-5 p-6">
              <div className="space-y-3">
                <div className="flex h-10 w-10 items-center justify-center rounded-xl border border-indigo-500/30 bg-indigo-500/10 text-indigo-300">
                  <Lock className="h-5 w-5" aria-hidden="true" />
                </div>
                <div>
                  <h2 id={titleId} className="font-['Montserrat',sans-serif] text-base font-semibold text-white">
                    Admin sign-in
                  </h2>
                  <p id={descriptionId} className="mt-1 text-xs text-slate-400">
                    Unlocks writes in every app, decrypted secrets, binary downloads and API key management.
                  </p>
                </div>
              </div>

              <div className="space-y-1.5">
                <label htmlFor={passwordId} className="block text-[11px] font-mono uppercase tracking-wider text-slate-300">
                  Admin password
                </label>
                <div className="relative">
                  <KeyRound className="pointer-events-none absolute left-3 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-slate-500" aria-hidden="true" />
                  <input
                    ref={inputRef}
                    id={passwordId}
                    type="password"
                    autoComplete="current-password"
                    required
                    value={password}
                    onChange={(e) => {
                      setPassword(e.target.value);
                      if (error) setError(null);
                    }}
                    readOnly={pending}
                    aria-invalid={error ? true : undefined}
                    aria-describedby={error ? errorId : undefined}
                    className={`h-10 w-full rounded-lg border bg-black/40 pl-9 pr-3 font-mono text-sm text-white placeholder-slate-600 transition-colors focus:outline-none focus:ring-2 read-only:opacity-60 ${
                      error
                        ? "border-rose-500/60 focus:border-rose-500 focus:ring-rose-500/40"
                        : "border-white/10 focus:border-indigo-500 focus:ring-indigo-500/40"
                    }`}
                  />
                </div>
                {error && (
                  <p id={errorId} role="alert" className="flex items-start gap-1.5 pt-0.5 text-xs text-rose-300">
                    <AlertCircle className="mt-px h-3.5 w-3.5 shrink-0 text-rose-400" aria-hidden="true" />
                    <span>{error}</span>
                  </p>
                )}
              </div>

              <div className="flex items-center justify-end gap-2 pt-1">
                <button
                  type="button"
                  onClick={onClose}
                  className="h-9 rounded-lg border border-white/10 bg-white/5 px-3.5 text-xs font-medium text-slate-300 transition-colors hover:bg-white/10 hover:text-white focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={!password}
                  aria-disabled={pending || undefined}
                  className="aria-disabled:cursor-wait aria-disabled:opacity-70 inline-flex h-9 min-w-[96px] items-center justify-center gap-2 rounded-lg bg-indigo-600 px-4 text-xs font-semibold text-white shadow-md shadow-indigo-600/25 transition-colors hover:bg-indigo-500 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-400 focus-visible:ring-offset-2 focus-visible:ring-offset-[#0B0F19] disabled:cursor-not-allowed disabled:opacity-50"
                >
                  {pending ? (
                    <>
                      <Loader2 className="h-3.5 w-3.5 animate-spin" aria-hidden="true" />
                      <span>Signing in…</span>
                    </>
                  ) : (
                    <span>Sign in</span>
                  )}
                </button>
              </div>
            </form>
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>,
    document.body
  );
}
