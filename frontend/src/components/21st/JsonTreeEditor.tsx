"use client";

import React, { useState, useMemo } from "react";
import {
  ChevronRight,
  ChevronDown,
  Lock,
  Copy,
  Check,
  Search,
  Code2,
  ListTree,
  RotateCcw,
  Sparkles,
} from "lucide-react";

interface JsonTreeEditorProps {
  initialValue: Record<string, unknown>;
  onChange?: (val: Record<string, unknown>) => void;
  onSave?: (val: Record<string, unknown>) => void;
  readOnly?: boolean;
  /** When set, the save button stays visible but disabled, with this text as its explanation. */
  saveDisabledReason?: string;
}

export function JsonTreeEditor({
  initialValue,
  onChange,
  onSave,
  readOnly = false,
  saveDisabledReason,
}: JsonTreeEditorProps) {
  const [viewMode, setViewMode] = useState<"tree" | "raw">("tree");
  const [rawText, setRawText] = useState(() => JSON.stringify(initialValue, null, 2));
  const [parseError, setParseError] = useState<string | null>(null);
  const [searchQuery, setSearchQuery] = useState("");
  const [expandedPaths, setExpandedPaths] = useState<Set<string>>(() => new Set(["$"]));
  const [copiedPath, setCopiedPath] = useState<string | null>(null);

  // Sync if initialValue changes externally
  React.useEffect(() => {
    setRawText(JSON.stringify(initialValue, null, 2));
    setParseError(null);
  }, [initialValue]);

  const parsedValue = useMemo(() => {
    try {
      return JSON.parse(rawText);
    } catch {
      return initialValue;
    }
  }, [rawText, initialValue]);

  const handleRawChange = (text: string) => {
    setRawText(text);
    try {
      const parsed = JSON.parse(text);
      setParseError(null);
      if (onChange) onChange(parsed);
    } catch (e: unknown) {
      const err = e as Error;
      setParseError(err.message);
    }
  };

  const handleFormat = () => {
    try {
      const parsed = JSON.parse(rawText);
      setRawText(JSON.stringify(parsed, null, 2));
      setParseError(null);
    } catch (e: unknown) {
      const err = e as Error;
      setParseError("Cannot format invalid JSON: " + err.message);
    }
  };

  const handleCopyPath = (path: string, e: React.MouseEvent) => {
    e.stopPropagation();
    navigator.clipboard.writeText(path.replace(/^\$\.?/, ""));
    setCopiedPath(path);
    setTimeout(() => setCopiedPath(null), 2000);
  };

  const togglePath = (path: string) => {
    setExpandedPaths((prev) => {
      const next = new Set(prev);
      if (next.has(path)) {
        next.delete(path);
      } else {
        next.add(path);
      }
      return next;
    });
  };

  const expandAll = () => {
    const paths = new Set<string>();
    const recurse = (val: unknown, currPath: string) => {
      if (val && typeof val === "object") {
        paths.add(currPath);
        Object.entries(val as Record<string, unknown>).forEach(([k, v]) => {
          recurse(v, `${currPath}.${k}`);
        });
      }
    };
    recurse(parsedValue, "$");
    setExpandedPaths(paths);
  };

  const collapseAll = () => {
    setExpandedPaths(new Set(["$"]));
  };

  return (
    <div className="flex flex-col h-full rounded-xl border border-white/10 bg-slate-900/60 backdrop-blur-md overflow-hidden">
      {/* Action Bar Header */}
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-white/10 bg-slate-950/40 px-4 py-2.5">
        <div className="flex items-center gap-2">
          {/* Mode Switcher */}
          <div className="inline-flex rounded-lg border border-white/10 bg-black/40 p-0.5 text-xs font-mono">
            <button
              type="button"
              onClick={() => setViewMode("tree")}
              className={`inline-flex items-center gap-1.5 rounded-md px-2.5 py-1 transition-all ${
                viewMode === "tree"
                  ? "bg-indigo-600 text-white font-medium shadow"
                  : "text-slate-400 hover:text-white"
              }`}
            >
              <ListTree className="h-3.5 w-3.5" />
              <span>Interactive Tree</span>
            </button>
            <button
              type="button"
              onClick={() => setViewMode("raw")}
              className={`inline-flex items-center gap-1.5 rounded-md px-2.5 py-1 transition-all ${
                viewMode === "raw"
                  ? "bg-indigo-600 text-white font-medium shadow"
                  : "text-slate-400 hover:text-white"
              }`}
            >
              <Code2 className="h-3.5 w-3.5" />
              <span>Raw JSON</span>
            </button>
          </div>

          {viewMode === "tree" && (
            <div className="flex items-center gap-1.5 text-[11px] font-mono text-slate-400">
              <button
                type="button"
                onClick={expandAll}
                className="hover:text-white transition-colors px-1.5 py-0.5 rounded border border-white/5 hover:border-white/20"
              >
                Expand All
              </button>
              <span>•</span>
              <button
                type="button"
                onClick={collapseAll}
                className="hover:text-white transition-colors px-1.5 py-0.5 rounded border border-white/5 hover:border-white/20"
              >
                Collapse All
              </button>
            </div>
          )}
        </div>

        {/* Search or Formatting controls */}
        <div className="flex items-center gap-2">
          {viewMode === "tree" ? (
            <div className="relative">
              <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-slate-400" />
              <input
                type="text"
                placeholder="Filter keys or values..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                className="h-7 w-48 sm:w-60 rounded-md border border-white/10 bg-black/40 pl-8 pr-3 text-xs text-white placeholder-slate-500 font-mono focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500"
              />
            </div>
          ) : (
            <>
              <button
                type="button"
                onClick={handleFormat}
                className="inline-flex items-center gap-1.5 rounded-md border border-white/10 bg-white/5 px-2.5 py-1 text-xs font-mono text-slate-300 hover:bg-white/10 hover:text-white transition-colors"
              >
                <Sparkles className="h-3.5 w-3.5 text-indigo-400" />
                <span>Prettify</span>
              </button>
              <button
                type="button"
                onClick={() => setRawText(JSON.stringify(initialValue, null, 2))}
                className="inline-flex items-center gap-1.5 rounded-md border border-white/10 bg-white/5 px-2.5 py-1 text-xs font-mono text-slate-300 hover:bg-white/10 hover:text-white transition-colors"
              >
                <RotateCcw className="h-3.5 w-3.5" />
                <span>Reset</span>
              </button>
            </>
          )}

          {!readOnly && onSave && (
            <button
              type="button"
              disabled={!!parseError || !!saveDisabledReason}
              title={saveDisabledReason}
              onClick={() => {
                try {
                  const val = JSON.parse(rawText);
                  onSave(val);
                } catch {
                  // error handled
                }
              }}
              className="rounded-md bg-indigo-600 px-3 py-1 text-xs font-semibold text-white hover:bg-indigo-500 transition-colors disabled:opacity-50 disabled:cursor-not-allowed disabled:hover:bg-indigo-600 shadow-md shadow-indigo-600/20"
            >
              Commit New Revision
            </button>
          )}
        </div>
      </div>

      {/* Parse Error Notification */}
      {parseError && (
        <div className="border-b border-rose-500/30 bg-rose-950/40 px-4 py-2 text-xs font-mono text-rose-300 flex items-center justify-between">
          <span>JSON Syntax Error: {parseError}</span>
          <span className="text-[10px] text-rose-400 uppercase tracking-widest font-bold">Invalid</span>
        </div>
      )}

      {/* Main Body */}
      <div className="flex-1 overflow-auto p-4 font-mono text-xs">
        {viewMode === "raw" ? (
          <textarea
            value={rawText}
            readOnly={readOnly}
            onChange={(e) => handleRawChange(e.target.value)}
            spellCheck={false}
            className="w-full h-full min-h-[420px] bg-transparent text-slate-200 resize-none font-mono text-xs focus:outline-none leading-relaxed selection:bg-indigo-600/30"
          />
        ) : (
          <div className="space-y-1 select-text">
            <TreeNode
              nodeKey="$"
              value={parsedValue}
              path="$"
              depth={0}
              expandedPaths={expandedPaths}
              togglePath={togglePath}
              searchQuery={searchQuery}
              copiedPath={copiedPath}
              handleCopyPath={handleCopyPath}
            />
          </div>
        )}
      </div>
    </div>
  );
}

interface TreeNodeProps {
  nodeKey: string;
  value: unknown;
  path: string;
  depth: number;
  expandedPaths: Set<string>;
  togglePath: (path: string) => void;
  searchQuery: string;
  copiedPath: string | null;
  handleCopyPath: (path: string, e: React.MouseEvent) => void;
  isParentSecure?: boolean;
}

function TreeNode({
  nodeKey,
  value,
  path,
  depth,
  expandedPaths,
  togglePath,
  searchQuery,
  copiedPath,
  handleCopyPath,
  isParentSecure = false,
}: TreeNodeProps) {
  const isObject = value !== null && typeof value === "object" && !Array.isArray(value);
  const isArray = Array.isArray(value);
  const isContainer = isObject || isArray;

  const isExpanded = expandedPaths.has(path);
  const isSecure = nodeKey === "secure_settings" || isParentSecure;

  const matchesSearch = useMemo(() => {
    if (!searchQuery) return true;
    const q = searchQuery.toLowerCase();
    if (nodeKey.toLowerCase().includes(q)) return true;
    if (!isContainer && String(value).toLowerCase().includes(q)) return true;
    return false;
  }, [nodeKey, value, isContainer, searchQuery]);

  if (!matchesSearch && !isContainer) {
    return null;
  }

  const entries = isContainer ? Object.entries(value as Record<string, unknown>) : [];

  return (
    <div className={`relative ${depth > 0 ? "ml-4 border-l border-white/5 pl-2" : ""}`}>
      <div
        onClick={() => (isContainer ? togglePath(path) : null)}
        className={`group flex items-center gap-2 py-0.5 rounded px-1.5 transition-colors ${
          isContainer ? "cursor-pointer hover:bg-white/5" : "hover:bg-white/5"
        }`}
      >
        {/* Toggle Icon */}
        {isContainer ? (
          <button
            type="button"
            className="text-slate-400 hover:text-white p-0.5 rounded"
            onClick={(e) => {
              e.stopPropagation();
              togglePath(path);
            }}
          >
            {isExpanded ? (
              <ChevronDown className="h-3.5 w-3.5 text-indigo-400" />
            ) : (
              <ChevronRight className="h-3.5 w-3.5 text-slate-500" />
            )}
          </button>
        ) : (
          <span className="w-4" />
        )}

        {/* Node Key */}
        <span
          className={`font-semibold ${
            nodeKey === "secure_settings"
              ? "text-amber-400 flex items-center gap-1"
              : isSecure
              ? "text-purple-300"
              : "text-slate-300"
          }`}
        >
          {nodeKey === "secure_settings" && <Lock className="h-3 w-3 text-amber-400 inline" />}
          {nodeKey !== "$" && `${nodeKey}:`}
        </span>

        {/* Node Value Preview */}
        {isContainer ? (
          <span className="text-slate-500 text-[11px]">
            {isArray ? `Array[${entries.length}]` : `{${entries.length} keys}`}
            {nodeKey === "secure_settings" && (
              <span className="ml-2 rounded bg-amber-950/40 border border-amber-500/20 px-1.5 py-0.2 text-[10px] text-amber-400 font-mono">
                Envelope Encrypted
              </span>
            )}
          </span>
        ) : (
          <span className="font-mono">
            {typeof value === "string" ? (
              <span className={isSecure ? "text-amber-300" : "text-emerald-400"}>
                &quot;{String(value)}&quot;
              </span>
            ) : typeof value === "number" ? (
              <span className="text-sky-400">{String(value)}</span>
            ) : typeof value === "boolean" ? (
              <span className="text-purple-400">{String(value)}</span>
            ) : value === null ? (
              <span className="text-slate-500 italic">null</span>
            ) : (
              <span className="text-slate-400">{String(value)}</span>
            )}
          </span>
        )}

        {/* Copy Path Action Button on Hover */}
        {nodeKey !== "$" && (
          <button
            type="button"
            title="Copy dot-notation path"
            onClick={(e) => handleCopyPath(path, e)}
            className="opacity-0 group-hover:opacity-100 transition-opacity ml-auto text-slate-500 hover:text-slate-300 p-0.5 rounded"
          >
            {copiedPath === path ? (
              <Check className="h-3 w-3 text-emerald-400" />
            ) : (
              <Copy className="h-3 w-3" />
            )}
          </button>
        )}
      </div>

      {/* Recursive Children */}
      {isContainer && isExpanded && (
        <div className="space-y-0.5">
          {entries.map(([k, v]) => (
            <TreeNode
              key={k}
              nodeKey={k}
              value={v}
              path={`${path}.${k}`}
              depth={depth + 1}
              expandedPaths={expandedPaths}
              togglePath={togglePath}
              searchQuery={searchQuery}
              copiedPath={copiedPath}
              handleCopyPath={handleCopyPath}
              isParentSecure={isSecure}
            />
          ))}
        </div>
      )}
    </div>
  );
}
