"use client";

import { useState } from "react";
import { Brain, Flame, Sparkles, Target } from "lucide-react";
import { Card, CardTitle } from "@/components/ui/Card";
import { Badge } from "@/components/ui/Badge";
import { PageHeader } from "@/components/ui/PageHeader";
import { StatTile } from "@/components/ui/StatTile";
import { ErrorState, Skeleton } from "@/components/ui/States";
import { ActivityChart, MasteryChart, RetentionChart, TopicChart } from "@/components/analytics/Charts";
import { WeakTopicsPanel } from "@/components/topics/WeakTopicsPanel";
import { useApiQuery } from "@/hooks/useApiQuery";
import { api } from "@/lib/endpoints";
import { cn } from "@/lib/cn";
import { formatDate, pluralize } from "@/lib/format";

const RANGES = [14, 30, 90] as const;

export default function AnalyticsPage() {
  const [days, setDays] = useState<(typeof RANGES)[number]>(30);
  const summary = useApiQuery(() => api.analytics.summary());
  const activity = useApiQuery(() => api.analytics.activity(days), [days]);
  const mastery = useApiQuery(() => api.analytics.mastery(days), [days]);
  const topics = useApiQuery(() => api.analytics.topics());
  const insights = useApiQuery(() => api.analytics.topicInsights());

  const rangePicker = (
    <div className="flex rounded-lg border border-border bg-card p-0.5" role="group" aria-label="Time range">
      {RANGES.map((r) => (
        <button
          key={r}
          type="button"
          onClick={() => setDays(r)}
          className={cn(
            "rounded-md px-3 py-1 text-xs font-medium",
            days === r ? "bg-primary text-primary-foreground" : "text-muted hover:text-foreground",
          )}
          aria-pressed={days === r}
        >
          {r}d
        </button>
      ))}
    </div>
  );

  return (
    <div className="space-y-6">
      <PageHeader title="Analytics" description="How your memory is holding up, computed from every review." actions={rangePicker} />

      {summary.error ? (
        <ErrorState message={summary.error} onRetry={summary.refetch} />
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {summary.loading || !summary.data ? (
            Array.from({ length: 4 }).map((_, i) => <Skeleton key={i} className="h-24" />)
          ) : (
            <>
              <StatTile
                label="Average recall"
                value={summary.data.averageRecall === null ? "—" : `${summary.data.averageRecall.toFixed(1)} / 5`}
                icon={Brain}
                sublabel={pluralize(summary.data.totalReviews, "review")}
              />
              <StatTile
                label="Retention (30d)"
                value={summary.data.retentionRate === null ? "—" : `${summary.data.retentionRate}%`}
                icon={Target}
                tone="primary"
              />
              <StatTile
                label="Mastered"
                value={summary.data.cardsMastered}
                icon={Sparkles}
                tone="success"
                sublabel={`${summary.data.masteredPercent}% of ${pluralize(summary.data.totalCards, "card")}`}
              />
              <StatTile
                label="Longest streak"
                value={`${summary.data.longestStreak} ${summary.data.longestStreak === 1 ? "day" : "days"}`}
                icon={Flame}
                tone="warning"
                sublabel={summary.data.lastActiveDate ? `Last active ${formatDate(summary.data.lastActiveDate)}` : undefined}
              />
            </>
          )}
        </div>
      )}

      <div className="grid gap-6 lg:grid-cols-2">
        <Card>
          <CardTitle className="mb-4">Review activity</CardTitle>
          {activity.loading ? <Skeleton className="h-64" /> : activity.error ? (
            <ErrorState message={activity.error} onRetry={activity.refetch} />
          ) : (
            <ActivityChart data={activity.data ?? []} />
          )}
        </Card>
        <Card>
          <CardTitle className="mb-4">Retention</CardTitle>
          {activity.loading ? <Skeleton className="h-64" /> : activity.error ? null : (
            <RetentionChart data={activity.data ?? []} />
          )}
        </Card>
        <Card>
          <CardTitle className="mb-4">Cards mastered over time</CardTitle>
          {mastery.loading ? <Skeleton className="h-64" /> : mastery.error ? (
            <ErrorState message={mastery.error} onRetry={mastery.refetch} />
          ) : (
            <MasteryChart data={mastery.data ?? []} />
          )}
        </Card>
        <Card>
          <CardTitle className="mb-4">Topic performance</CardTitle>
          {topics.loading ? <Skeleton className="h-64" /> : topics.error ? (
            <ErrorState message={topics.error} onRetry={topics.refetch} />
          ) : (
            <TopicChart data={topics.data ?? []} />
          )}
        </Card>
      </div>

      <Card>
        <div className="mb-4 flex flex-wrap items-end justify-between gap-2">
          <CardTitle>All topics</CardTitle>
          <p className="text-xs text-muted">
            Accuracy combines flashcard reviews (rated 3 or higher) and quiz answers. Critical below 50%, weak below 70%,
            good below 85%, strong from 85%.
          </p>
        </div>
        <WeakTopicsPanel insights={insights.data} loading={insights.loading} error={insights.error} onRetry={insights.refetch} />
      </Card>

      <Card>
        <CardTitle className="mb-4">Flashcard recall by topic</CardTitle>
        {topics.data && topics.data.length > 0 ? (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="text-left text-xs uppercase text-muted">
                <tr>
                  <th className="py-2 pr-4">Topic</th>
                  <th className="py-2 pr-4">Cards</th>
                  <th className="py-2 pr-4">Reviews</th>
                  <th className="py-2 pr-4">Average</th>
                  <th className="py-2 pr-4">Recent</th>
                  <th className="py-2 pr-4">Success</th>
                  <th className="py-2">Status</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {topics.data.map((t) => (
                  <tr key={t.topic}>
                    <td className="py-2 pr-4 font-medium">{t.topic}</td>
                    <td className="py-2 pr-4 tabular-nums">{t.cardCount}</td>
                    <td className="py-2 pr-4 tabular-nums">{t.reviews}</td>
                    <td className="py-2 pr-4 tabular-nums">{t.averageQuality.toFixed(2)}</td>
                    <td className="py-2 pr-4 tabular-nums">{t.recentAverageQuality.toFixed(2)}</td>
                    <td className="py-2 pr-4 tabular-nums">{t.successRatePercent}%</td>
                    <td className="py-2">{t.weak ? <Badge tone="danger">Weak</Badge> : <Badge tone="success">On track</Badge>}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <p className="text-sm text-muted">Review some cards with topics and their performance appears here.</p>
        )}
      </Card>
    </div>
  );
}
