"use client";

import { useState } from "react";
import { AlertTriangle, BookPlus, CheckCircle2, EyeOff } from "lucide-react";
import { ReviewMistakeButton } from "@/components/mistakes/ReviewMistakeButton";
import { useToast } from "@/components/providers/ToastProvider";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { PageHeader } from "@/components/ui/PageHeader";
import { StatTile } from "@/components/ui/StatTile";
import { EmptyState, ErrorState, Skeleton } from "@/components/ui/States";
import { useApiQuery } from "@/hooks/useApiQuery";
import { errorMessage } from "@/lib/api";
import { api } from "@/lib/endpoints";
import { cn } from "@/lib/cn";
import { formatDateTime, pluralize } from "@/lib/format";
import type { MistakeStatus } from "@/types";

const TABS: { status: MistakeStatus; label: string }[] = [
  { status: "OPEN", label: "To review" },
  { status: "CONVERTED", label: "Turned into cards" },
  { status: "DISMISSED", label: "Dismissed" },
];

export default function MistakesPage() {
  const toast = useToast();
  const [status, setStatus] = useState<MistakeStatus>("OPEN");
  const [page, setPage] = useState(0);
  const summary = useApiQuery(() => api.mistakes.summary());
  const list = useApiQuery(() => api.mistakes.list({ status, page, size: 20 }), [status, page]);

  const refresh = () => {
    summary.refetch();
    list.refetch();
  };

  const act = async (action: () => Promise<unknown>, message: string) => {
    try {
      await action();
      toast.success(message);
      refresh();
    } catch (error) {
      toast.error(errorMessage(error));
    }
  };

  return (
    <div className="space-y-6">
      <PageHeader
        title="Mistakes"
        description="Every question you got wrong in a quiz or mock exam. Review one to turn it into an explanatory flashcard that spaced repetition then schedules."
      />

      {summary.error ? (
        <ErrorState message={summary.error} onRetry={summary.refetch} />
      ) : (
        <div className="grid gap-4 sm:grid-cols-3">
          {summary.loading || !summary.data ? (
            Array.from({ length: 3 }).map((_, i) => <Skeleton key={i} className="h-24" />)
          ) : (
            <>
              <StatTile label="To review" value={summary.data.open} icon={AlertTriangle} tone={summary.data.open > 0 ? "warning" : "neutral"} />
              <StatTile label="Turned into cards" value={summary.data.converted} icon={BookPlus} tone="success" />
              <StatTile
                label="Most missed topic"
                value={summary.data.openByTopic[0]?.topic ?? "—"}
                icon={CheckCircle2}
                sublabel={summary.data.openByTopic[0] ? pluralize(summary.data.openByTopic[0].count, "open mistake") : "Nothing open"}
              />
            </>
          )}
        </div>
      )}

      <div className="flex gap-1 border-b border-border" role="tablist">
        {TABS.map((tab) => (
          <button
            key={tab.status}
            role="tab"
            aria-selected={status === tab.status}
            onClick={() => {
              setStatus(tab.status);
              setPage(0);
            }}
            className={cn(
              "-mb-px border-b-2 px-3 py-2 text-sm font-medium",
              status === tab.status ? "border-primary text-primary" : "border-transparent text-muted hover:text-foreground",
            )}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {list.loading && !list.data ? (
        <div className="space-y-3">
          {Array.from({ length: 3 }).map((_, i) => <Skeleton key={i} className="h-28" />)}
        </div>
      ) : list.error ? (
        <ErrorState message={list.error} onRetry={list.refetch} />
      ) : !list.data || list.data.content.length === 0 ? (
        <EmptyState
          icon={CheckCircle2}
          title={status === "OPEN" ? "No mistakes to review" : "Nothing here"}
          description={status === "OPEN" ? "Take a quiz or a mock exam; anything you get wrong lands here." : undefined}
        />
      ) : (
        <ul className="space-y-3">
          {list.data.content.map((m) => (
            <li key={m.id}>
              <Card className="border-l-4 border-l-danger">
                <div className="flex flex-col gap-3 md:flex-row md:items-start md:justify-between">
                  <div className="min-w-0 flex-1">
                    <div className="mb-1 flex flex-wrap items-center gap-2 text-xs text-muted">
                      <Badge tone="neutral">{m.source === "QUIZ" ? "Quiz" : "Mock exam"}</Badge>
                      {m.topic && <Badge tone="primary">{m.topic}</Badge>}
                      {m.deckName && <span>{m.deckName}</span>}
                      <span>· missed {pluralize(m.occurrences, "time")}</span>
                      <span>· last {formatDateTime(m.lastMissedAt)}</span>
                    </div>
                    <p className="font-medium">{m.question}</p>
                    <p className="mt-1 text-sm">
                      <span className="text-muted">Your answer: </span>
                      <span className="text-danger">{m.givenAnswer ?? "skipped"}</span>
                      <span className="mx-2 text-muted">·</span>
                      <span className="text-muted">Correct: </span>
                      <span className="text-success">{m.correctAnswer}</span>
                    </p>
                    {m.explanation && <p className="mt-2 rounded-lg bg-background p-3 text-sm text-muted">{m.explanation}</p>}
                  </div>
                  <div className="flex shrink-0 flex-wrap items-center gap-2">
                    {m.status !== "DISMISSED" && (
                      <ReviewMistakeButton mistakeId={m.id} status={m.status} cardId={m.cardId} onConverted={refresh} />
                    )}
                    {m.status === "OPEN" && (
                      <Button size="sm" variant="ghost" onClick={() => act(() => api.mistakes.dismiss(m.id), "Mistake dismissed")}>
                        <EyeOff className="h-4 w-4" />
                        Dismiss
                      </Button>
                    )}
                    {m.status === "DISMISSED" && (
                      <Button size="sm" variant="secondary" onClick={() => act(() => api.mistakes.reopen(m.id), "Mistake reopened")}>
                        Reopen
                      </Button>
                    )}
                  </div>
                </div>
              </Card>
            </li>
          ))}
        </ul>
      )}

      {list.data && list.data.totalPages > 1 && (
        <div className="flex items-center justify-between text-sm text-muted">
          <span>
            Page {list.data.page + 1} of {list.data.totalPages}
          </span>
          <div className="flex gap-2">
            <Button size="sm" variant="secondary" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>
              Previous
            </Button>
            <Button size="sm" variant="secondary" disabled={page + 1 >= list.data.totalPages} onClick={() => setPage((p) => p + 1)}>
              Next
            </Button>
          </div>
        </div>
      )}
    </div>
  );
}
