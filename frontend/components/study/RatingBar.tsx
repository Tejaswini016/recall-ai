"use client";

import { QUALITY_LABELS } from "@/lib/format";
import { cn } from "@/lib/cn";

const TONES: Record<number, string> = {
  0: "border-red-300 text-red-700 hover:bg-red-50 dark:text-red-300 dark:hover:bg-red-950/40",
  1: "border-red-300 text-red-700 hover:bg-red-50 dark:text-red-300 dark:hover:bg-red-950/40",
  2: "border-amber-300 text-amber-700 hover:bg-amber-50 dark:text-amber-300 dark:hover:bg-amber-950/40",
  3: "border-emerald-300 text-emerald-700 hover:bg-emerald-50 dark:text-emerald-300 dark:hover:bg-emerald-950/40",
  4: "border-emerald-300 text-emerald-700 hover:bg-emerald-50 dark:text-emerald-300 dark:hover:bg-emerald-950/40",
  5: "border-primary text-primary hover:bg-primary/10",
};

/** Six SM-2 quality buttons. The keyboard mapping (0–5) is handled by the study page. */
export function RatingBar({ onRate, disabled }: { onRate: (quality: number) => void; disabled?: boolean }) {
  return (
    <div className="grid grid-cols-3 gap-2 sm:grid-cols-6" role="group" aria-label="Rate your recall">
      {QUALITY_LABELS.map(({ quality, label, hint, key }) => (
        <button
          key={quality}
          type="button"
          disabled={disabled}
          onClick={() => onRate(quality)}
          title={hint}
          className={cn(
            "flex flex-col items-center rounded-xl border bg-card px-2 py-3 text-sm font-semibold transition focus:outline-none focus-visible:ring-2 focus-visible:ring-primary disabled:opacity-50",
            TONES[quality],
          )}
        >
          <span>{label}</span>
          <span className="mt-1 text-[11px] font-normal text-muted">
            <kbd className="rounded border border-border px-1">{key}</kbd>
          </span>
        </button>
      ))}
    </div>
  );
}
