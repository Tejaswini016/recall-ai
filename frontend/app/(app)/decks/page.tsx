"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { BookOpen, Plus, Search } from "lucide-react";
import { DeckFormModal } from "@/components/decks/DeckForm";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { PageHeader } from "@/components/ui/PageHeader";
import { ProgressBar } from "@/components/ui/StatTile";
import { EmptyState, ErrorState, Skeleton } from "@/components/ui/States";
import { useApiQuery } from "@/hooks/useApiQuery";
import { api } from "@/lib/endpoints";
import { pluralize } from "@/lib/format";
import type { Deck } from "@/types";

const PAGE_SIZE = 12;

export default function DecksPage() {
  const [q, setQ] = useState("");
  const [debounced, setDebounced] = useState("");
  const [tag, setTag] = useState("");
  const [page, setPage] = useState(0);
  const [creating, setCreating] = useState(false);

  useEffect(() => {
    const handle = window.setTimeout(() => {
      setDebounced(q.trim());
      setPage(0);
    }, 250);
    return () => window.clearTimeout(handle);
  }, [q]);

  const decks = useApiQuery(() => api.decks.list({ q: debounced, tag, page, size: PAGE_SIZE }), [debounced, tag, page]);
  const tags = useApiQuery(() => api.tags());

  return (
    <div>
      <PageHeader
        title="Decks"
        description="Organize what you are learning. Each deck schedules its own cards."
        actions={
          <Button onClick={() => setCreating(true)}>
            <Plus className="h-4 w-4" />
            New deck
          </Button>
        }
      />

      <div className="mb-6 flex flex-col gap-3 sm:flex-row">
        <div className="relative flex-1">
          <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted" aria-hidden />
          <input
            value={q}
            onChange={(e) => setQ(e.target.value)}
            placeholder="Search by name, subject or tag…"
            aria-label="Search decks"
            className="h-10 w-full rounded-lg border border-border bg-card pl-9 pr-3 text-sm placeholder:text-muted focus:outline-none focus-visible:ring-2 focus-visible:ring-primary"
          />
        </div>
        <select
          value={tag}
          onChange={(e) => {
            setTag(e.target.value);
            setPage(0);
          }}
          aria-label="Filter by tag"
          className="h-10 rounded-lg border border-border bg-card px-3 text-sm sm:w-48"
        >
          <option value="">All tags</option>
          {(tags.data ?? []).map((t) => (
            <option key={t} value={t}>
              {t}
            </option>
          ))}
        </select>
      </div>

      {decks.loading && !decks.data ? (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {Array.from({ length: 6 }).map((_, i) => <Skeleton key={i} className="h-40" />)}
        </div>
      ) : decks.error ? (
        <ErrorState message={decks.error} onRetry={decks.refetch} />
      ) : decks.data && decks.data.content.length === 0 ? (
        <EmptyState
          icon={BookOpen}
          title={debounced || tag ? "No decks match" : "No decks yet"}
          description={debounced || tag ? "Try a different search or clear the filter." : "Create a deck, then paste notes or upload a PDF to generate cards."}
          action={!debounced && !tag ? <Button onClick={() => setCreating(true)}>Create your first deck</Button> : undefined}
        />
      ) : (
        <>
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {decks.data?.content.map((deck) => <DeckCard key={deck.id} deck={deck} />)}
          </div>
          {decks.data && decks.data.totalPages > 1 && (
            <div className="mt-6 flex items-center justify-center gap-3 text-sm">
              <Button variant="secondary" size="sm" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>
                Previous
              </Button>
              <span className="text-muted">
                Page {page + 1} of {decks.data.totalPages}
              </span>
              <Button variant="secondary" size="sm" disabled={page + 1 >= decks.data.totalPages} onClick={() => setPage((p) => p + 1)}>
                Next
              </Button>
            </div>
          )}
        </>
      )}

      <DeckFormModal open={creating} onClose={() => setCreating(false)} onSaved={() => decks.refetch()} />
    </div>
  );
}

function DeckCard({ deck }: { deck: Deck }) {
  return (
    <Link href={`/decks/${deck.id}`} className="block rounded-xl focus:outline-none focus-visible:ring-2 focus-visible:ring-primary">
      <Card className="h-full transition hover:border-primary/40 hover:shadow">
        <div className="flex items-start justify-between gap-2">
          <div className="min-w-0">
            <h2 className="truncate font-semibold">{deck.name}</h2>
            {deck.subject && <p className="text-xs text-muted">{deck.subject}</p>}
          </div>
          {deck.dueCount > 0 && <Badge tone="primary">{deck.dueCount} due</Badge>}
        </div>
        {deck.description && <p className="mt-2 line-clamp-2 text-sm text-muted">{deck.description}</p>}
        <div className="mt-4">
          <div className="mb-1 flex justify-between text-xs text-muted">
            <span>{pluralize(deck.cardCount, "card")}</span>
            <span>{deck.progressPercent}% mastered</span>
          </div>
          <ProgressBar value={deck.progressPercent} label={`${deck.name} progress`} />
        </div>
        {deck.tags.length > 0 && (
          <div className="mt-3 flex flex-wrap gap-1">
            {deck.tags.slice(0, 4).map((t) => (
              <Badge key={t}>{t}</Badge>
            ))}
          </div>
        )}
      </Card>
    </Link>
  );
}
