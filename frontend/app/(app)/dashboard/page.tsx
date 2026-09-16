"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { ArrowRight, BookOpen, CheckCircle2, Flame, Layers, Sparkles } from "lucide-react";
import { useAuth } from "@/components/providers/AuthProvider";
import { Button } from "@/components/ui/Button";
import { Card, CardTitle } from "@/components/ui/Card";
import { Badge } from "@/components/ui/Badge";
import { StatTile } from "@/components/ui/StatTile";
import { EmptyState, ErrorState, Skeleton } from "@/components/ui/States";
import { ActivityChart } from "@/components/analytics/Charts";
import { WeakTopicsPanel } from "@/components/topics/WeakTopicsPanel";
import { ReviewMistakeButton } from "@/components/mistakes/ReviewMistakeButton";
import { useApiQuery } from "@/hooks/useApiQuery";
import { api } from "@/lib/endpoints";
import { formatDateTime, greeting, pluralize } from "@/lib/format";

export default function DashboardPage() {
  const { user } = useAuth();
  const router = useRouter();
  const summary = useApiQuery(() => api.analytics.summary());
  const queue = useApiQuery(() => api.reviews.due({ limit: 5 }));
  const insights = useApiQuery(() => api.analytics.topicInsights());
  const mistakes = useApiQuery(() => api.mistakes.recent());
  const history = useApiQuery(() => api.reviews.history({ size: 6 }));
  const activity = useApiQuery(() => api.analytics.activity(14));

  const due = summary.data?.dueToday ?? queue.data?.totalDue ?? 0;

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">
            {greeting()}, {user?.name.split(" ")[0] ?? "there"}
          </h1>
          <p className="mt-1 text-sm text-muted">
            {summary.loading
              ? "Checking what is due…"
              : due > 0
                ? `You have ${pluralize(due, "card")} due today.`
                : "Nothing is due right now. Nice work."}
          </p>
        </div>
        <Button size="lg" disabled={due === 0} onClick={() => router.push("/study/all")}>
          Start review
          <ArrowRight className="h-4 w-4" />
        </Button>
      </div>

      {summary.error ? (
        <ErrorState message={summary.error} onRetry={summary.refetch} />
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {summary.loading || !summary.data ? (
            Array.from({ length: 4 }).map((_, i) => <Skeleton key={i} className="h-24" />)
          ) : (
            <>
              <StatTile label="Due today" value={summary.data.dueToday} icon={Layers} tone="primary" />
              <StatTile
                label="Reviewed today"
                value={summary.data.reviewedToday}
                icon={CheckCircle2}
                sublabel={summary.data.retentionRate !== null ? `${summary.data.retentionRate}% retention (30d)` : undefined}
              />
              <StatTile
                label="Current streak"
                value={`${summary.data.currentStreak} ${summary.data.currentStreak === 1 ? "day" : "days"}`}
                icon={Flame}
                tone={summary.data.currentStreak > 0 ? "warning" : "neutral"}
                sublabel={`Longest ${summary.data.longestStreak}`}
              />
              <StatTile
                label="Mastered"
                value={summary.data.cardsMastered}
                icon={Sparkles}
                tone="success"
                sublabel={`${summary.data.masteredPercent}% of ${pluralize(summary.data.totalCards, "card")}`}
              />
            </>
          )}
        </div>
      )}

      <div className="grid gap-6 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <div className="mb-4 flex items-center justify-between">
            <CardTitle>Today&apos;s review queue</CardTitle>
            {queue.data && queue.data.totalDue > 0 && (
              <Link href="/study/all" className="text-sm font-medium text-primary hover:underline">
                Review all {queue.data.totalDue}
              </Link>
            )}
          </div>
          {queue.loading ? (
            <div className="space-y-2">
              {Array.from({ length: 3 }).map((_, i) => <Skeleton key={i} className="h-12" />)}
            </div>
          ) : queue.error ? (
            <ErrorState message={queue.error} onRetry={queue.refetch} />
          ) : queue.data && queue.data.cards.length > 0 ? (
            <ul className="divide-y divide-border">
              {queue.data.cards.map((card) => (
                <li key={card.id} className="flex items-center justify-between gap-3 py-3">
                  <div className="min-w-0">
                    <p className="truncate text-sm font-medium">{card.question}</p>
                    <p className="text-xs text-muted">
                      {card.deckName}
                      {card.topic ? ` · ${card.topic}` : ""}
                    </p>
                  </div>
                  {card.daysOverdue > 0 ? (
                    <Badge tone="warning">{card.daysOverdue}d overdue</Badge>
                  ) : (
                    <Badge tone="primary">due today</Badge>
                  )}
                </li>
              ))}
            </ul>
          ) : (
            <EmptyState
              icon={BookOpen}
              title="All caught up"
              description="Generate cards from your notes or wait for scheduled reviews to come due."
              action={
                <Link href="/decks">
                  <Button variant="secondary" size="sm">Go to decks</Button>
                </Link>
              }
            />
          )}
        </Card>

        <Card>
          <CardTitle className="mb-4">Your weak topics</CardTitle>
          <WeakTopicsPanel
            insights={insights.data}
            loading={insights.loading}
            error={insights.error}
            onRetry={insights.refetch}
            compact
            limit={5}
          />
          <Link href="/analytics" className="mt-4 inline-block text-sm font-medium text-primary hover:underline">
            See all topics
          </Link>
        </Card>
      </div>

      <div className="grid gap-6 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <CardTitle className="mb-4">Reviews, last 14 days</CardTitle>
          {activity.loading ? (
            <Skeleton className="h-56" />
          ) : activity.error ? (
            <ErrorState message={activity.error} onRetry={activity.refetch} />
          ) : (
            <ActivityChart data={activity.data ?? []} height={220} />
          )}
        </Card>

        <Card>
          <div className="mb-4 flex items-center justify-between">
            <CardTitle>Mistakes to review</CardTitle>
            <Link href="/mistakes" className="text-sm font-medium text-primary hover:underline">
              All mistakes
            </Link>
          </div>
          {mistakes.loading && !mistakes.data ? (
            <Skeleton className="h-32" />
          ) : mistakes.error ? (
            <ErrorState message={mistakes.error} onRetry={mistakes.refetch} />
          ) : mistakes.data && mistakes.data.length > 0 ? (
            <ul className="divide-y divide-border">
              {mistakes.data.map((m) => (
                <li key={m.id} className="py-2.5">
                  <p className="truncate text-sm font-medium">{m.question}</p>
                  <p className="text-xs text-muted">
                    {m.topic ? `${m.topic} · ` : ""}you answered {m.givenAnswer ?? "nothing"} · correct: {m.correctAnswer}
                  </p>
                  <div className="mt-1.5">
                    <ReviewMistakeButton mistakeId={m.id} status={m.status} cardId={m.cardId} onConverted={() => mistakes.refetch()} />
                  </div>
                </li>
              ))}
            </ul>
          ) : (
            <p className="text-sm text-muted">No open mistakes. Quiz answers you get wrong will show up here.</p>
          )}
        </Card>

        <Card>
          <CardTitle className="mb-4">Recent activity</CardTitle>
          {history.loading ? (
            <Skeleton className="h-40" />
          ) : history.error ? (
            <ErrorState message={history.error} onRetry={history.refetch} />
          ) : history.data && history.data.content.length > 0 ? (
            <ul className="space-y-3">
              {history.data.content.map((item) => (
                <li key={item.id} className="flex items-start gap-3">
                  <span
                    className={`mt-1 h-2 w-2 shrink-0 rounded-full ${item.successful ? "bg-success" : "bg-danger"}`}
                    aria-hidden
                  />
                  <div className="min-w-0">
                    <p className="truncate text-sm">{item.question}</p>
                    <p className="text-xs text-muted">
                      {item.deckName} · rated {item.quality} · {formatDateTime(item.reviewedAt)}
                    </p>
                  </div>
                </li>
              ))}
            </ul>
          ) : (
            <p className="text-sm text-muted">Your reviews will show up here.</p>
          )}
        </Card>
      </div>
    </div>
  );
}
