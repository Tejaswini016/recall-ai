"use client";

import { Info } from "lucide-react";
import { Badge } from "@/components/ui/Badge";
import { Card, CardTitle } from "@/components/ui/Card";
import { ProgressBar } from "@/components/ui/StatTile";
import { ErrorState, Skeleton } from "@/components/ui/States";
import { cn } from "@/lib/cn";
import type { Readiness } from "@/types";

export function readinessTone(score: number): "success" | "primary" | "warning" | "danger" {
  if (score >= 85) return "success";
  if (score >= 70) return "primary";
  if (score >= 50) return "warning";
  return "danger";
}

/**
 * The estimated exam readiness score with every component and its weight, so the number is
 * always explainable. Labelled as an estimate everywhere it appears.
 */
export function ReadinessCard({ data, loading, error, onRetry }: { data: Readiness | null; loading: boolean; error: string | null; onRetry: () => void }) {
  if (loading && !data) return <Skeleton className="h-64" />;
  if (error) return <ErrorState message={error} onRetry={onRetry} />;
  if (!data) return null;
  const tone = readinessTone(data.score);
  const ring = { success: "text-success", primary: "text-primary", warning: "text-warning", danger: "text-danger" }[tone];

  return (
    <Card>
      <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
        <div className="flex items-center gap-2">
          <CardTitle>Estimated exam readiness</CardTitle>
          <Badge>Estimate</Badge>
        </div>
        <span className="text-xs text-muted">
          Confidence {data.confidence.toLowerCase()} · based on {data.evidenceAttempts} reviews and answers
        </span>
      </div>
      <div className="grid gap-6 md:grid-cols-[auto_1fr]">
        <div className="flex flex-col items-center justify-center md:pr-6 md:border-r md:border-border">
          <p className={cn("text-6xl font-bold tabular-nums", ring)} aria-label={`Estimated readiness ${data.score} out of 100`}>
            {data.score}
          </p>
          <p className="text-sm font-medium">{data.label}</p>
          <p className="text-xs text-muted">out of 100</p>
        </div>
        <ul className="space-y-2.5">
          {data.components.map((c) => (
            <li key={c.key} className={cn(!c.available && "opacity-60")}>
              <div className="mb-1 flex items-baseline justify-between gap-2 text-sm">
                <span className="font-medium">
                  {c.label} <span className="text-xs font-normal text-muted">{c.available ? `${c.weight}% of the score` : "not yet measured"}</span>
                </span>
                <span className="tabular-nums text-muted">{c.available ? `${c.score}` : "—"}</span>
              </div>
              <ProgressBar value={c.available ? c.score : 0} label={`${c.label} score`} />
              <p className="mt-1 text-xs text-muted">{c.detail}</p>
            </li>
          ))}
        </ul>
      </div>
      <div className="mt-4 flex items-start gap-2 rounded-lg bg-background p-3 text-sm">
        <Info className="mt-0.5 h-4 w-4 shrink-0 text-primary" aria-hidden />
        <p>
          <span className="font-medium">Next step: </span>
          {data.recommendation}
        </p>
      </div>
      <p className="mt-2 text-xs text-muted">
        This is a heuristic over your measured accuracy, retention, topic coverage, revision consistency and mock exam scores. It is not a prediction of an exam grade.
      </p>
    </Card>
  );
}
