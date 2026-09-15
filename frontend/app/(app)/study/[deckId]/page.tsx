"use client";

import { Suspense, useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { useParams, useSearchParams } from "next/navigation";
import { CheckCircle2, RotateCcw, Sparkles } from "lucide-react";
import { RatingBar } from "@/components/study/RatingBar";
import { useToast } from "@/components/providers/ToastProvider";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { ProgressBar } from "@/components/ui/StatTile";
import { EmptyState, ErrorState, LoadingState } from "@/components/ui/States";
import { useApiQuery } from "@/hooks/useApiQuery";
import { errorMessage } from "@/lib/api";
import { api } from "@/lib/endpoints";
import { pluralize, relativeDay } from "@/lib/format";
import type { ReviewResult } from "@/types";

const SESSION_LIMIT = 100;

interface Graded {
  cardId: number;
  quality: number;
  result: ReviewResult;
}

export default function StudyPage() {
  return (
    <Suspense fallback={<LoadingState label="Preparing your review…" />}>
      <StudySession />
    </Suspense>
  );
}

/**
 * Two modes share one screen: the due queue (`/study/all` or `/study/{deckId}`) and topic practice
 * (`?topic=`), which pulls every card on the topic, hardest first. Grading is identical in both.
 */
function StudySession() {
  const params = useParams<{ deckId: string }>();
  const deckId = params.deckId === "all" ? undefined : Number(params.deckId);
  const topic = useSearchParams().get("topic")?.trim() || null;
  const toast = useToast();
  const queue = useApiQuery(
    () => (topic ? api.reviews.practice({ topic, limit: SESSION_LIMIT }) : api.reviews.due({ deckId, limit: SESSION_LIMIT })),
    [deckId, topic],
  );

  const [index, setIndex] = useState(0);
  const [revealed, setRevealed] = useState(false);
  const [graded, setGraded] = useState<Graded[]>([]);
  const [grading, setGrading] = useState(false);
  const [remaining, setRemaining] = useState<number | null>(null);

  const cards = queue.data?.cards ?? [];
  const current = cards[index];
  const finished = queue.data !== null && index >= cards.length;

  const rate = useCallback(
    async (quality: number) => {
      if (!current || grading) return;
      setGrading(true);
      try {
        const result = await api.reviews.grade(current.id, quality);
        setGraded((g) => [...g, { cardId: current.id, quality, result }]);
        setRemaining(result.remainingDue);
        setRevealed(false);
        setIndex((i) => i + 1);
      } catch (error) {
        toast.error(errorMessage(error));
      } finally {
        setGrading(false);
      }
    },
    [current, grading, toast],
  );

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.target instanceof HTMLInputElement || event.target instanceof HTMLTextAreaElement) return;
      if (!current) return;
      if (!revealed && (event.key === " " || event.code === "Space" || event.key === "Enter")) {
        event.preventDefault();
        setRevealed(true);
        return;
      }
      if (revealed && /^[0-5]$/.test(event.key)) {
        event.preventDefault();
        void rate(Number(event.key));
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [current, revealed, rate]);

  const restart = () => {
    setIndex(0);
    setRevealed(false);
    setGraded([]);
    setRemaining(null);
    queue.refetch();
  };

  const backHref = topic ? "/dashboard" : deckId ? `/decks/${deckId}` : "/dashboard";

  if (queue.loading && !queue.data) return <LoadingState label="Preparing your review…" />;
  if (queue.error) return <ErrorState message={queue.error} onRetry={queue.refetch} />;

  if (cards.length === 0) {
    return (
      <EmptyState
        icon={CheckCircle2}
        title={topic ? "No cards on this topic" : "Nothing due"}
        description={
          topic
            ? `No card carries the topic "${topic}" yet. Generate or add cards with that topic to practise it.`
            : "Every card here is scheduled for a later day. Come back when the queue fills up."
        }
        action={
          <Link href={backHref}>
            <Button variant="secondary">Back</Button>
          </Link>
        }
      />
    );
  }

  if (finished) {
    const successes = graded.filter((g) => g.result.successful).length;
    const mastered = graded.filter((g) => g.result.mastered).length;
    return (
      <div className="mx-auto max-w-xl animate-in">
        <Card className="text-center">
          <Sparkles className="mx-auto h-8 w-8 text-primary" />
          <h1 className="mt-3 text-2xl font-bold">Session complete</h1>
          <p className="mt-1 text-sm text-muted">
            You reviewed {pluralize(graded.length, "card")} and recalled {successes} of them
            {graded.length > 0 ? ` (${Math.round((successes * 100) / graded.length)}%)` : ""}.
            {mastered > 0 ? ` ${pluralize(mastered, "card")} reached mastery.` : ""}
          </p>
          <div className="mt-6 flex flex-wrap justify-center gap-2">
            {remaining !== null && remaining > 0 && (
              <Button onClick={restart}>
                <RotateCcw className="h-4 w-4" />
                Review {remaining} more
              </Button>
            )}
            <Link href={backHref}>
              <Button variant="secondary">Done</Button>
            </Link>
          </div>
        </Card>
      </div>
    );
  }

  const total = cards.length;
  return (
    <div className="mx-auto max-w-2xl">
      <div className="mb-4 flex items-center justify-between text-sm text-muted">
        <Link href={backHref} className="hover:underline">
          Exit
        </Link>
        <span>
          {topic ? `Practice: ${topic} · ` : ""}
          {index + 1} of {total}
          {remaining !== null ? ` · ${pluralize(remaining, "card")} remaining today` : ""}
        </span>
      </div>
      <ProgressBar value={(index / total) * 100} className="mb-6" label="Session progress" />

      <Card key={current.id} className="animate-flip min-h-64 p-6 sm:p-8">
        <div className="mb-3 flex flex-wrap items-center gap-2 text-xs text-muted">
          <span>{current.deckName}</span>
          {current.topic && <Badge tone="primary">{current.topic}</Badge>}
          {current.daysOverdue > 0 && <Badge tone="warning">{current.daysOverdue}d overdue</Badge>}
        </div>
        <p className="text-xl font-semibold leading-snug sm:text-2xl">{current.question}</p>

        {revealed ? (
          <div className="mt-6 animate-in border-t border-border pt-6">
            <p className="text-xs font-semibold uppercase tracking-wide text-muted">Answer</p>
            <p className="mt-1 text-lg">{current.answer}</p>
            {current.explanation && (
              <div className="mt-4 rounded-lg bg-background p-3 text-sm text-muted">{current.explanation}</div>
            )}
          </div>
        ) : (
          <div className="mt-8">
            <Button size="lg" className="w-full" onClick={() => setRevealed(true)}>
              Show answer
            </Button>
            <p className="mt-2 text-center text-xs text-muted">
              Press <kbd className="rounded border border-border px-1">Space</kbd> to reveal
            </p>
          </div>
        )}
      </Card>

      {revealed && (
        <div className="mt-4 animate-in">
          <p className="mb-2 text-center text-sm text-muted">How well did you remember it?</p>
          <RatingBar onRate={rate} disabled={grading} />
          <p className="mt-2 text-center text-xs text-muted">Next review was {relativeDay(current.dueDate)} · interval {current.interval}d</p>
        </div>
      )}
    </div>
  );
}
