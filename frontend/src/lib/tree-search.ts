/**
 * True when a JSON node's key, its value (for leaves), or anything below it contains the query
 * (case-insensitive). The tree editor uses it to show only the branches that lead to a match.
 */
export function subtreeMatches(key: string, value: unknown, query: string): boolean {
  if (!query) return true;
  const q = query.toLowerCase();
  if (key.toLowerCase().includes(q)) return true;
  if (value !== null && typeof value === "object") {
    return Object.entries(value as Record<string, unknown>).some(([k, v]) => subtreeMatches(k, v, q));
  }
  return String(value).toLowerCase().includes(q);
}
