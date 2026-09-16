"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { ArrowRight, BookOpen, CalendarDays, CheckCircle2, ClipboardCheck, Flame, Layers, Sparkles, Trophy } from "lucide-react";
import { useAuth } from "@/components/providers/AuthProvider";
import { Button } from "@/components/ui/Button";
import { Card, CardTitle } from "@/components/ui/Card";
import { Badge } from "@/components/ui/Badge";
import { StatTile } from "@/components/ui/StatTile";
import { EmptyState, ErrorState, Skeleton } from "@/components/ui/States";
import { ActivityChart } from "@/components/analytics/Charts";
import { DifficultyDistributionCard } from "@/components/analytics/DifficultyDistributionCard";
import { ReadinessCard } from "@/components/dashboard/ReadinessCard";
import { UpcomingReviewsCard } from "@/components/dashboard/UpcomingReviewsCard";
import { ReviewMistakeButton } from "@/components/mistakes/ReviewMistakeButton";
import { TaskRow } from "@/components/plan/TaskRow";
import { TopicBadge } from "@/components/topics/TopicBadge";
import { WeakTopicsPanel } from "@/components/topics/WeakTopicsPanel";
import { DIFFICULTY_LABELS } from "@/components/exams/examLabels";
import { useApiQuery } from "@/hooks/useApiQuery";
import { api } from "@/lib/endpoints";
import { formatDateTime, greeting, pluralize } from "@/lib/format";

export default function DashboardPage() {
  const { user } = useAuth();
  const router = useRouter();
  const summary = useApiQuery(() => api.analytics.summary());
  const readiness = useApiQuery(() => api.analytics.readiness());
  const queue = useApiQuery(() => api.reviews.due({ limit: 5 }));
  const upcoming = useApiQuery(() => api.reviews.upcoming(7));
  const insights = useApiQuery(() => api.analytics.topicInsights());
  const today = useApiQuery(() => api.studyPlans.today());
  const mistakes = useApiQuery(() => api.mistakes.recent());
  const difficulty = useApiQuery(() => api.analytics.difficulty());
  const exams = useApiQuery(() => api.mockExams.stats());
  const history = useApiQuery(() => api.reviews.history({ size: 6 }));
  const activity = useApiQuery(() => api.analytics.activity(14));

  const due = summary.data?.dueToday ?? queue.data?.totalDue ?? 0;
  const strong = (insights.data ?? []).filter((t) => t.category === "STRONG").slice(0, 5);

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
                label="Retention (30d)"
                value={summary.data.retentionRate === null ? "—" : `${summary.data.retentionRate}%`}
                icon={CheckCircle2}
                sublabel={summary.data.averageRecall !== null ? `Average recall ${summary.data.averageRecall.toFixed(1)} / 5` : `${summary.data.reviewedToday} reviewed today`}
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
        <div className="lg:col-span-2">
          <ReadinessCard data={readiness.data} loading={readiness.loading} error={readiness.error} onRetry={readiness.refetch} />
        </div>
        <Card>
          <div className="mb-3 flex items-center justify-between">
            <CardTitle>Today&apos;s plan</CardTitle>
            <Link href="/plan" className="text-sm font-medium text-primary hover:underline">
              Open plan
            </Link>
          </div>
          {today.loading && !today.data ? (
            <Skeleton className="h-32" />
          ) : today.error ? (
            <ErrorState message={today.error} onRetry={today.refetch} />
          ) : today.data && today.data.activePlans === 0 ? (
            <EmptyState
              icon={CalendarDays}
              title="No active study plan"
              description="Tell RecallAI your exam date and topics to get a daily schedule."
              action={
                <Link href="/plan/new">
                  <Button variant="secondary" size="sm">Create a plan</Button>
                </Link>
              }
            />
          ) : today.data && today.data.tasks.length === 0 ? (
            <p className="text-sm text-muted">Nothing scheduled today. {today.data.carriedOver.length > 0 ? `${pluralize(today.data.carriedOver.length, "task")} carried over from earlier days.` : "Rest day."}</p>
          ) : today.data ? (
            <>
              <p className="mb-1 text-xs text-muted">
                {today.data.doneMinutes} of {today.data.totalMinutes} minutes done
              </p>
              <ul className="divide-y divide-border">
                {today.data.tasks.map((t) => (
                  <TaskRow key={t.id} task={t} onChange={() => today.refetch()} />
                ))}
              </ul>
            </>
          ) : null}
        </Card>
      </div>

      <div className="grid gap-6 lg:grid-cols-3">
        <Card>
          <CardTitle className="mb-4">Your weak topics</CardTitle>
          <WeakTopicsPanel insights={insights.data} loading={insights.loading} error={insights.error} onRetry={insights.refetch} compact limit={5} />
          <Link href="/analytics" className="mt-4 inline-block text-sm font-medium text-primary hover:underline">
            See all topics
          </Link>
        </Card>
        <Card>
          <CardTitle className="mb-4">Strong topics</CardTitle>
          {insights.loading && !insights.data ? (
            <Skeleton className="h-24" />
          ) : strong.length === 0 ? (
            <p className="text-sm text-muted">Topics reach &quot;strong&quot; at 85% accuracy over at least three attempts.</p>
          ) : (
            <ul className="divide-y divide-border">
              {strong.map((t) => (
                <li key={t.topic} className="flex items-center justify-between gap-3 py-2.5">
                  <div className="min-w-0">
                    <p className="truncate text-sm font-medium">{t.topic}</p>
                    <p className="text-xs text-muted">{t.accuracyPercent}% over {pluralize(t.attempts, "attempt")}</p>
                  </div>
                  <TopicBadge category={t.category} />
                </li>
              ))}
            </ul>
          )}
        </Card>
        <UpcomingReviewsCard data={upcoming.data} loading={upcoming.loading} error={upcoming.error} onRetry={upcoming.refetch} />
      </div>

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
                      {card.topic ? ` · ${card.topic}` : ""} · {card.difficulty.toLowerCase()}
                    </p>
                  </div>
                  {card.daysOverdue > 0 ? <Badge tone="warning">{card.daysOverdue}d overdue</Badge> : <Badge tone="primary">due today</Badge>}
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
            <p className="text-sm text-muted">No open mistakes. Quiz and exam answers you get wrong will show up here.</p>
          )}
        </Card>
      </div>

      <div className="grid gap-6 lg:grid-cols-2">
        <DifficultyDistributionCard title="Adaptive difficulty progress" data={difficulty.data} loading={difficulty.loading} error={difficulty.error} onRetry={difficulty.refetch} />
        <Card>
          <div className="mb-4 flex items-center justify-between">
            <CardTitle>Mock exam performance</CardTitle>
            <Link href="/exams" className="text-sm font-medium text-primary hover:underline">
              Mock exams
            </Link>
          </div>
          {exams.loading && !exams.data ? (
            <Skeleton className="h-32" />
          ) : exams.error ? (
            <ErrorState message={exams.error} onRetry={exams.refetch} />
          ) : exams.data && exams.data.submitted === 0 ? (
            <EmptyState
              icon={ClipboardCheck}
              title="No mock exam yet"
              description="A timed exam on your own cards shows how you perform under pressure and feeds the readiness estimate."
              action={
                <Link href="/exams">
                  <Button variant="secondary" size="sm">Take one</Button>
                </Link>
              }
            />
          ) : exams.data ? (
            <>
              <div className="mb-3 grid grid-cols-3 gap-2 text-center">
                <div className="rounded-lg bg-background p-2">
                  <p className="text-xs text-muted">Average</p>
                  <p className="text-xl font-bold tabular-nums">{exams.data.averagePercent}%</p>
                </div>
                <div className="rounded-lg bg-background p-2">
                  <p className="text-xs text-muted">Best</p>
                  <p className="text-xl font-bold tabular-nums text-success">{exams.data.bestPercent}%</p>
                </div>
                <div className="rounded-lg bg-background p-2">
                  <p className="text-xs text-muted">Latest</p>
                  <p className="text-xl font-bold tabular-nums">{exams.data.latestPercent}%</p>
                </div>
              </div>
              <ul className="divide-y divide-border">
                {exams.data.recent.filter((e) => e.status === "SUBMITTED").slice(0, 4).map((e) => (
                  <li key={e.id} className="flex items-center justify-between gap-2 py-2 text-sm">
                    <Link href={`/exams/${e.id}/results`} className="min-w-0 truncate hover:underline">
                      {e.title}
                    </Link>
                    <span className="flex shrink-0 items-center gap-2 text-xs text-muted">
                      {DIFFICULTY_LABELS[e.difficulty]}
                      <Badge tone={(e.percent ?? 0) >= 70 ? "success" : "warning"}>{e.percent}%</Badge>
                    </span>
                  </li>
                ))}
              </ul>
            </>
          ) : null}
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
          <CardTitle className="mb-4">Recent activity</CardTitle>
          {history.loading ? (
            <Skeleton className="h-40" />
          ) : history.error ? (
            <ErrorState message={history.error} onRetry={history.refetch} />
          ) : history.data && history.data.content.length > 0 ? (
            <ul className="space-y-3">
              {history.data.content.map((item) => (
                <li key={item.id} className="flex items-start gap-3">
                  <span className={`mt-1 h-2 w-2 shrink-0 rounded-full ${item.successful ? "bg-success" : "bg-danger"}`} aria-hidden />
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
      <p className="text-center text-xs text-muted">
        <Trophy className="mr-1 inline h-3 w-3" aria-hidden />
        Readiness, topic categories and difficulty tiers are computed from your own results; only summaries, cards and questions are model-generated, and all of it is validated before it is saved.
      </p>
    </div>
  );
}
