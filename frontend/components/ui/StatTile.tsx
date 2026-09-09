import { Card } from "@/components/ui/Card";
import { cn } from "@/lib/cn";

export function StatTile({
  label,
  value,
  sublabel,
  icon: Icon,
  tone = "neutral",
}: {
  label: string;
  value: string | number;
  sublabel?: string;
  icon?: React.ComponentType<{ className?: string }>;
  tone?: "neutral" | "primary" | "success" | "warning";
}) {
  const toneClass = {
    neutral: "text-foreground",
    primary: "text-primary",
    success: "text-success",
    warning: "text-warning",
  }[tone];
  return (
    <Card className="flex items-start justify-between gap-3">
      <div>
        <p className="text-xs font-medium uppercase tracking-wide text-muted">{label}</p>
        <p className={cn("mt-1 text-3xl font-bold tabular-nums", toneClass)}>{value}</p>
        {sublabel && <p className="mt-1 text-xs text-muted">{sublabel}</p>}
      </div>
      {Icon && <Icon className="h-5 w-5 shrink-0 text-muted" />}
    </Card>
  );
}

export function ProgressBar({ value, className, label }: { value: number; className?: string; label?: string }) {
  const clamped = Math.max(0, Math.min(100, value));
  return (
    <div
      className={cn("h-2 w-full overflow-hidden rounded-full bg-black/5 dark:bg-white/10", className)}
      role="progressbar"
      aria-valuenow={clamped}
      aria-valuemin={0}
      aria-valuemax={100}
      aria-label={label ?? "Progress"}
    >
      <div className="h-full rounded-full bg-primary transition-all" style={{ width: `${clamped}%` }} />
    </div>
  );
}
