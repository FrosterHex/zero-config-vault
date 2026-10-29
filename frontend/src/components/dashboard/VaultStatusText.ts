import type { VaultStatus } from "@/context/VaultContext";

/** Wording and colour for the vault connection indicators (sidebar footer, overview badge). */
export const VAULT_STATUS_DISPLAY: Record<VaultStatus, { label: string; detail: string; tone: "ok" | "wait" | "error" }> = {
  connected: { label: "MongoDB connected", detail: "Vault API answering", tone: "ok" },
  connecting: { label: "Connecting…", detail: "Waiting for the vault API", tone: "wait" },
  unreachable: { label: "Vault unreachable", detail: "Retry with the refresh button", tone: "error" },
};
