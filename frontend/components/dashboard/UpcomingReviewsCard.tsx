"use client";

import Link from "next/link";
import { Card, CardTitle } from "@/components/ui/Card";
import { ErrorState, Skeleton } from "@/components/ui/States";
import { formatShortDate, pluralize, relativeDay } from "@/lib/format";

const WEEKDAY = new Intl.DateTimeFormat(undefined, { weekday: "short" });

function dayLabel(iso: string): string {
  return relativeDay(iso) === "today" ? "Today" : WEEKDAY.format(new Date(`${iso}T00:00:00`));
}
import type { UpcomingReviews } from "@/types";

/** Cards coming due over the next week as a small bar strip, with the overdue backlog called out. */
export function UpcomingReviewsCard({ data, loading, error, onRetry }: { data: UpcomingReviews | null; loading: boolean; error: string | null; onRetry: () => void }) {
  if (loading && !data) return <Skeleton className="h-48" />;
  if (error) return <ErrorState message={error} onRetry={onRetry} />;
  if (!data) return null;
  const max = Math.max(1, ...data.days.map((d) => d.cards));
  const total = data.days.reduce((n, d) => n + d.cards, 0);

  return (
    <Card>
      <div className="mb-3 flex items-center justify-between">
        <CardTitle>Upcoming reviews</CardTitle>
        <span className="text-xs text-muted">{pluralize(total, "card")} in 7 days</span>
      </div>
      {data.overdue > 0 && (
        <p className="mb-3 rounded-lg bg-amber-50 px-3 py-2 text-xs text-amber-900 dark:bg-amber-950/40 dark:text-amber-200">
          {pluralize(data.overdue, "card")} overdue.{" "}
          <Link href="/study/all" className="font-medium underline">
            Catch up now
          </Link>
        </p>
      )}
      <ol className="flex h-32 items-end gap-1.5" aria-label="Cards due per day">
        {data.days.map((d) => (
          <li key={d.date} className="flex flex-1 flex-col items-center gap-1" title={`${formatShortDate(d.date)}: ${pluralize(d.cards, "card")}`}>
            <span className="text-[10px] tabular-nums text-muted">{d.cards}</span>
            <div className="flex h-20 w-full items-end">
              <div className={d.cards > 0 ? "w-full rounded-t bg-primary/70" : "w-full rounded-t bg-black/10 dark:bg-white/10"} style={{ height: `${d.cards > 0 ? Math.max(8, (d.cards * 100) / max) : 3}%` }} aria-hidden />
            </div>
            <span className="text-[10px] text-muted">{dayLabel(d.date)}</span>
          </li>
        ))}
      </ol>
    </Card>
  );
}
