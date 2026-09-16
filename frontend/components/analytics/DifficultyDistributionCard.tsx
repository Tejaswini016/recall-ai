"use client";

import { Card, CardTitle } from "@/components/ui/Card";
import { ErrorState, Skeleton } from "@/components/ui/States";
import { difficultyLabel } from "@/components/study/DifficultyBadge";
import { pluralize } from "@/lib/format";
import type { DifficultyDistribution, DifficultyTier } from "@/types";

const BAR: Record<DifficultyTier, string> = {
  EASY: "bg-emerald-500",
  MEDIUM: "bg-primary",
  HARD: "bg-amber-500",
  EXPERT: "bg-red-500",
};

/** Cards per adaptive difficulty tier, as one stacked bar plus a legend. */
export function DifficultyDistributionCard({
  data,
  loading,
  error,
  onRetry,
  title = "Adaptive difficulty",
}: {
  data: DifficultyDistribution | null;
  loading: boolean;
  error: string | null;
  onRetry: () => void;
  title?: string;
}) {
  return (
    <Card>
      <div className="mb-4 flex flex-wrap items-end justify-between gap-2">
        <CardTitle>{title}</CardTitle>
        <p className="text-xs text-muted">
          Cards move to a harder tier when you forget them and to an easier one after three confident recalls. Hard and
          expert cards come back sooner than SM-2 alone would schedule.
        </p>
      </div>
      {loading && !data ? (
        <Skeleton className="h-16" />
      ) : error ? (
        <ErrorState message={error} onRetry={onRetry} />
      ) : !data || data.totalCards === 0 ? (
        <p className="text-sm text-muted">Add cards and review them to see their difficulty tiers.</p>
      ) : (
        <div>
          <div className="flex h-3 w-full overflow-hidden rounded-full bg-black/5 dark:bg-white/10" role="img" aria-label="Cards per difficulty tier">
            {data.tiers
              .filter((t) => t.cards > 0)
              .map((t) => (
                <div key={t.tier} className={BAR[t.tier]} style={{ width: `${(t.cards * 100) / data.totalCards}%` }} title={`${difficultyLabel(t.tier)}: ${t.cards}`} />
              ))}
          </div>
          <ul className="mt-3 grid grid-cols-2 gap-2 sm:grid-cols-4">
            {data.tiers.map((t) => (
              <li key={t.tier} className="flex items-center gap-2 whitespace-nowrap text-sm">
                <span className={`h-2.5 w-2.5 rounded-full ${BAR[t.tier]}`} aria-hidden />
                <span className="font-medium">{difficultyLabel(t.tier)}</span>
                <span className="text-muted tabular-nums">
                  {t.cards} ({t.percent}%)
                </span>
              </li>
            ))}
          </ul>
          <p className="mt-3 text-xs text-muted">
            {pluralize(data.cardsWithLapses, "card")} forgotten at least once
            {data.averageResponseMs !== null ? ` · average time to recall ${(data.averageResponseMs / 1000).toFixed(1)}s` : ""}
          </p>
        </div>
      )}
    </Card>
  );
}
