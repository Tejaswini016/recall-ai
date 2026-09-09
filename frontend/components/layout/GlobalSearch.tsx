"use client";

import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import { Search } from "lucide-react";
import { api } from "@/lib/endpoints";
import type { SearchResponse } from "@/types";

const DEBOUNCE_MS = 250;

/** Debounced search box over decks and cards, with a keyboard-dismissable result panel. */
export function GlobalSearch() {
  const [q, setQ] = useState("");
  const [results, setResults] = useState<SearchResponse | null>(null);
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (q.trim().length < 2) return;
    const handle = window.setTimeout(() => {
      api
        .search(q.trim())
        .then((r) => {
          setResults(r);
          setOpen(true);
        })
        .catch(() => setResults(null));
    }, DEBOUNCE_MS);
    return () => window.clearTimeout(handle);
  }, [q]);

  useEffect(() => {
    const onClick = (e: MouseEvent) => {
      if (!ref.current?.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener("mousedown", onClick);
    return () => document.removeEventListener("mousedown", onClick);
  }, []);

  const empty = results && results.decks.length === 0 && results.cards.length === 0;

  return (
    <div ref={ref} className="relative">
      <label className="sr-only" htmlFor="global-search">
        Search decks and cards
      </label>
      <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted" aria-hidden />
      <input
        id="global-search"
        value={q}
        onChange={(e) => {
          setQ(e.target.value);
          if (e.target.value.trim().length < 2) {
            setResults(null);
            setOpen(false);
          }
        }}
        onFocus={() => results && setOpen(true)}
        onKeyDown={(e) => e.key === "Escape" && setOpen(false)}
        placeholder="Search decks and cards…"
        className="h-9 w-full rounded-lg border border-border bg-background pl-9 pr-3 text-sm placeholder:text-muted focus:outline-none focus-visible:ring-2 focus-visible:ring-primary"
        autoComplete="off"
      />
      {open && results && (
        <div className="absolute right-0 top-11 z-40 w-full min-w-72 overflow-hidden rounded-xl border border-border bg-card shadow-lg animate-in">
          {empty && <p className="p-4 text-sm text-muted">No matches.</p>}
          {results.decks.length > 0 && (
            <div className="border-b border-border p-2">
              <p className="px-2 pb-1 text-xs font-semibold uppercase text-muted">Decks</p>
              {results.decks.map((d) => (
                <Link
                  key={d.id}
                  href={`/decks/${d.id}`}
                  onClick={() => setOpen(false)}
                  className="block rounded-lg px-2 py-1.5 text-sm hover:bg-black/5 dark:hover:bg-white/10"
                >
                  {d.name}
                  {d.subject && <span className="ml-2 text-xs text-muted">{d.subject}</span>}
                </Link>
              ))}
            </div>
          )}
          {results.cards.length > 0 && (
            <div className="p-2">
              <p className="px-2 pb-1 text-xs font-semibold uppercase text-muted">Cards</p>
              {results.cards.map((c) => (
                <Link
                  key={c.id}
                  href={`/decks/${c.deckId}?card=${c.id}`}
                  onClick={() => setOpen(false)}
                  className="block truncate rounded-lg px-2 py-1.5 text-sm hover:bg-black/5 dark:hover:bg-white/10"
                >
                  {c.question}
                </Link>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  );
}
