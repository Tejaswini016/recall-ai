"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useParams, useRouter, useSearchParams } from "next/navigation";
import { BrainCircuit, ClipboardList, GraduationCap, Pencil, Plus, Sparkles, Trash2 } from "lucide-react";
import { CardFormModal } from "@/components/decks/CardForm";
import { DeckFormModal } from "@/components/decks/DeckForm";
import { GenerateCardsModal, GenerateQuizModal } from "@/components/decks/GenerateModals";
import { useToast } from "@/components/providers/ToastProvider";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { ConfirmDialog } from "@/components/ui/Modal";
import { PageHeader } from "@/components/ui/PageHeader";
import { ProgressBar } from "@/components/ui/StatTile";
import { EmptyState, ErrorState, LoadingState, Skeleton } from "@/components/ui/States";
import { useApiQuery } from "@/hooks/useApiQuery";
import { errorMessage } from "@/lib/api";
import { api } from "@/lib/endpoints";
import { cn } from "@/lib/cn";
import { formatDate, pluralize, relativeDay } from "@/lib/format";
import type { Card as CardType, QuizSummary } from "@/types";

export default function DeckDetailPage() {
  const params = useParams<{ id: string }>();
  const deckId = Number(params.id);
  const router = useRouter();
  const search = useSearchParams();
  const toast = useToast();

  const deck = useApiQuery(() => api.decks.get(deckId), [deckId]);
  const [tab, setTab] = useState<"cards" | "quizzes">("cards");
  const [editing, setEditing] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [busy, setBusy] = useState(false);
  const [generating, setGenerating] = useState(false);
  const [quizzing, setQuizzing] = useState(false);
  const [refreshKey, setRefreshKey] = useState(0);

  const remove = async () => {
    setBusy(true);
    try {
      await api.decks.remove(deckId);
      toast.success("Deck deleted");
      router.replace("/decks");
    } catch (error) {
      toast.error(errorMessage(error));
      setBusy(false);
    }
  };

  if (deck.loading && !deck.data) return <LoadingState />;
  if (deck.error || !deck.data) return <ErrorState message={deck.error ?? "Deck not found"} onRetry={deck.refetch} />;
  const d = deck.data;

  return (
    <div>
      <PageHeader
        eyebrow={
          <Link href="/decks" className="hover:underline">
            Decks
          </Link>
        }
        title={d.name}
        description={d.description ?? undefined}
        actions={
          <>
            <Link href={`/study/${d.id}`}>
              <Button disabled={d.dueCount === 0}>
                <GraduationCap className="h-4 w-4" />
                Study{d.dueCount > 0 ? ` (${d.dueCount})` : ""}
              </Button>
            </Link>
            <Button variant="secondary" onClick={() => setGenerating(true)}>
              <Sparkles className="h-4 w-4" />
              Generate cards
            </Button>
            <Button variant="secondary" onClick={() => setQuizzing(true)}>
              <BrainCircuit className="h-4 w-4" />
              Generate quiz
            </Button>
            <Button variant="ghost" onClick={() => setEditing(true)} aria-label="Edit deck">
              <Pencil className="h-4 w-4" />
            </Button>
            <Button variant="ghost" onClick={() => setDeleting(true)} aria-label="Delete deck" className="text-danger">
              <Trash2 className="h-4 w-4" />
            </Button>
          </>
        }
      />

      <Card className="mb-6">
        <div className="grid gap-4 sm:grid-cols-4">
          <Stat label="Cards" value={d.cardCount} />
          <Stat label="Due" value={d.dueCount} />
          <Stat label="Mastered" value={d.masteredCount} />
          <Stat label="Created" value={formatDate(d.createdAt)} />
        </div>
        <div className="mt-4">
          <div className="mb-1 flex justify-between text-xs text-muted">
            <span>Progress</span>
            <span>{d.progressPercent}%</span>
          </div>
          <ProgressBar value={d.progressPercent} />
        </div>
        <div className="mt-3 flex flex-wrap gap-1.5">
          {d.subject && <Badge tone="primary">{d.subject}</Badge>}
          {d.tags.map((t) => (
            <Badge key={t}>{t}</Badge>
          ))}
        </div>
      </Card>

      <div className="mb-4 flex gap-1 border-b border-border" role="tablist">
        {(["cards", "quizzes"] as const).map((t) => (
          <button
            key={t}
            role="tab"
            aria-selected={tab === t}
            onClick={() => setTab(t)}
            className={cn(
              "-mb-px border-b-2 px-4 py-2 text-sm font-medium capitalize",
              tab === t ? "border-primary text-primary" : "border-transparent text-muted hover:text-foreground",
            )}
          >
            {t}
          </button>
        ))}
      </div>

      {tab === "cards" ? (
        <CardsTab deckId={d.id} refreshKey={refreshKey} onChanged={() => deck.refetch()} highlightCardId={Number(search.get("card")) || null} />
      ) : (
        <QuizzesTab deckId={d.id} refreshKey={refreshKey} onGenerate={() => setQuizzing(true)} />
      )}

      <DeckFormModal open={editing} onClose={() => setEditing(false)} deck={d} onSaved={() => deck.refetch()} />
      <ConfirmDialog
        open={deleting}
        onClose={() => setDeleting(false)}
        onConfirm={remove}
        loading={busy}
        title="Delete this deck?"
        description={`"${d.name}" and its ${pluralize(d.cardCount, "card")}, quizzes and review history will be removed permanently.`}
      />
      <GenerateCardsModal
        open={generating}
        onClose={() => setGenerating(false)}
        deckId={d.id}
        onGenerated={() => {
          setRefreshKey((k) => k + 1);
          setTab("cards");
          deck.refetch();
        }}
      />
      <GenerateQuizModal
        open={quizzing}
        onClose={() => setQuizzing(false)}
        deckId={d.id}
        deckName={d.name}
        hasCards={d.cardCount > 0}
        onGenerated={(quiz) => router.push(`/quiz/${quiz.id}`)}
      />
    </div>
  );
}

function Stat({ label, value }: { label: string; value: string | number }) {
  return (
    <div>
      <p className="text-xs uppercase tracking-wide text-muted">{label}</p>
      <p className="text-lg font-semibold tabular-nums">{value}</p>
    </div>
  );
}

function CardsTab({
  deckId,
  refreshKey,
  onChanged,
  highlightCardId,
}: {
  deckId: number;
  refreshKey: number;
  onChanged: () => void;
  highlightCardId: number | null;
}) {
  const toast = useToast();
  const [q, setQ] = useState("");
  const [debounced, setDebounced] = useState("");
  const [page, setPage] = useState(0);
  const [editing, setEditing] = useState<CardType | null | "new">(null);
  const [removing, setRemoving] = useState<CardType | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    const handle = window.setTimeout(() => {
      setDebounced(q.trim());
      setPage(0);
    }, 250);
    return () => window.clearTimeout(handle);
  }, [q]);

  const cards = useApiQuery(() => api.decks.cards(deckId, { q: debounced, page, size: 20 }), [deckId, debounced, page, refreshKey]);

  const remove = async () => {
    if (!removing) return;
    setBusy(true);
    try {
      await api.cards.remove(removing.id);
      toast.success("Card deleted");
      setRemoving(null);
      cards.refetch();
      onChanged();
    } catch (error) {
      toast.error(errorMessage(error));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div>
      <div className="mb-4 flex flex-col gap-3 sm:flex-row">
        <input
          value={q}
          onChange={(e) => setQ(e.target.value)}
          placeholder="Search cards…"
          aria-label="Search cards"
          className="h-10 flex-1 rounded-lg border border-border bg-card px-3 text-sm placeholder:text-muted focus:outline-none focus-visible:ring-2 focus-visible:ring-primary"
        />
        <Button variant="secondary" onClick={() => setEditing("new")}>
          <Plus className="h-4 w-4" />
          Add card
        </Button>
      </div>

      {cards.loading && !cards.data ? (
        <div className="space-y-2">{Array.from({ length: 4 }).map((_, i) => <Skeleton key={i} className="h-20" />)}</div>
      ) : cards.error ? (
        <ErrorState message={cards.error} onRetry={cards.refetch} />
      ) : cards.data && cards.data.content.length === 0 ? (
        <EmptyState
          icon={ClipboardList}
          title={debounced ? "No cards match" : "No cards yet"}
          description={debounced ? "Try another search." : "Generate cards from your notes, or add one by hand."}
        />
      ) : (
        <>
          <ul className="space-y-2">
            {cards.data?.content.map((card) => (
              <li
                key={card.id}
                className={cn(
                  "rounded-xl border border-border bg-card p-4",
                  highlightCardId === card.id && "ring-2 ring-primary",
                )}
              >
                <div className="flex items-start justify-between gap-3">
                  <div className="min-w-0 flex-1">
                    <p className="font-medium">{card.question}</p>
                    <p className="mt-1 text-sm text-muted">{card.answer}</p>
                    <div className="mt-2 flex flex-wrap items-center gap-1.5 text-xs text-muted">
                      {card.topic && <Badge tone="primary">{card.topic}</Badge>}
                      {card.tags.map((t) => (
                        <Badge key={t}>{t}</Badge>
                      ))}
                      <span>
                        due {relativeDay(card.dueDate)} · interval {card.interval}d · ease {card.easeFactor.toFixed(2)}
                      </span>
                    </div>
                  </div>
                  <div className="flex shrink-0 gap-1">
                    <Button variant="ghost" size="sm" onClick={() => setEditing(card)} aria-label="Edit card">
                      <Pencil className="h-4 w-4" />
                    </Button>
                    <Button variant="ghost" size="sm" onClick={() => setRemoving(card)} aria-label="Delete card" className="text-danger">
                      <Trash2 className="h-4 w-4" />
                    </Button>
                  </div>
                </div>
              </li>
            ))}
          </ul>
          {cards.data && cards.data.totalPages > 1 && (
            <div className="mt-4 flex items-center justify-center gap-3 text-sm">
              <Button variant="secondary" size="sm" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>
                Previous
              </Button>
              <span className="text-muted">
                Page {page + 1} of {cards.data.totalPages}
              </span>
              <Button variant="secondary" size="sm" disabled={page + 1 >= cards.data.totalPages} onClick={() => setPage((p) => p + 1)}>
                Next
              </Button>
            </div>
          )}
        </>
      )}

      <CardFormModal
        open={editing !== null}
        onClose={() => setEditing(null)}
        deckId={deckId}
        card={editing === "new" ? null : editing}
        onSaved={() => {
          cards.refetch();
          onChanged();
        }}
      />
      <ConfirmDialog
        open={removing !== null}
        onClose={() => setRemoving(null)}
        onConfirm={remove}
        loading={busy}
        title="Delete this card?"
        description="Its review history will be removed too."
      />
    </div>
  );
}

function QuizzesTab({ deckId, refreshKey, onGenerate }: { deckId: number; refreshKey: number; onGenerate: () => void }) {
  const toast = useToast();
  const quizzes = useApiQuery(() => api.quizzes.list({ deckId, size: 50 }), [deckId, refreshKey]);
  const [removing, setRemoving] = useState<QuizSummary | null>(null);
  const [busy, setBusy] = useState(false);

  const remove = async () => {
    if (!removing) return;
    setBusy(true);
    try {
      await api.quizzes.remove(removing.id);
      toast.success("Quiz deleted");
      setRemoving(null);
      quizzes.refetch();
    } catch (error) {
      toast.error(errorMessage(error));
    } finally {
      setBusy(false);
    }
  };

  if (quizzes.loading && !quizzes.data) return <LoadingState />;
  if (quizzes.error) return <ErrorState message={quizzes.error} onRetry={quizzes.refetch} />;
  if (!quizzes.data || quizzes.data.content.length === 0) {
    return (
      <EmptyState
        icon={BrainCircuit}
        title="No quizzes yet"
        description="Generate a multiple-choice quiz from this deck's cards to test yourself."
        action={<Button onClick={onGenerate}>Generate quiz</Button>}
      />
    );
  }
  return (
    <>
      <ul className="space-y-2">
        {quizzes.data.content.map((quiz) => (
          <li key={quiz.id} className="flex items-center justify-between gap-3 rounded-xl border border-border bg-card p-4">
            <div className="min-w-0">
              <p className="font-medium">{quiz.title}</p>
              <p className="text-xs text-muted">
                {pluralize(quiz.questionCount, "question")} · {pluralize(quiz.attemptCount, "attempt")}
                {quiz.bestScorePercent !== null ? ` · best ${quiz.bestScorePercent}%` : ""} · {formatDate(quiz.createdAt)}
              </p>
            </div>
            <div className="flex shrink-0 gap-1">
              <Link href={`/quiz/${quiz.id}`}>
                <Button size="sm">Take quiz</Button>
              </Link>
              <Button variant="ghost" size="sm" onClick={() => setRemoving(quiz)} aria-label="Delete quiz" className="text-danger">
                <Trash2 className="h-4 w-4" />
              </Button>
            </div>
          </li>
        ))}
      </ul>
      <ConfirmDialog
        open={removing !== null}
        onClose={() => setRemoving(null)}
        onConfirm={remove}
        loading={busy}
        title="Delete this quiz?"
        description="Past attempts will be removed too."
      />
    </>
  );
}
