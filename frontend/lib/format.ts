const dateFormatter = new Intl.DateTimeFormat(undefined, { month: "short", day: "numeric", year: "numeric" });
const shortDateFormatter = new Intl.DateTimeFormat(undefined, { month: "short", day: "numeric" });
const dateTimeFormatter = new Intl.DateTimeFormat(undefined, {
  month: "short",
  day: "numeric",
  hour: "numeric",
  minute: "2-digit",
});

export function formatDate(iso: string | null | undefined): string {
  if (!iso) return "—";
  return dateFormatter.format(new Date(iso));
}

export function formatShortDate(iso: string): string {
  // Date-only strings are parsed as UTC; add a time so the local day is preserved.
  return shortDateFormatter.format(new Date(iso.length === 10 ? `${iso}T00:00:00` : iso));
}

export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) return "—";
  return dateTimeFormatter.format(new Date(iso));
}

export function relativeDay(isoDate: string, today = new Date()): string {
  const target = new Date(`${isoDate}T00:00:00`);
  const start = new Date(today.getFullYear(), today.getMonth(), today.getDate());
  const diff = Math.round((target.getTime() - start.getTime()) / 86_400_000);
  if (diff === 0) return "today";
  if (diff === 1) return "tomorrow";
  if (diff === -1) return "yesterday";
  if (diff < 0) return `${-diff} days ago`;
  return `in ${diff} days`;
}

export function formatDuration(seconds: number | null | undefined): string {
  if (seconds === null || seconds === undefined) return "—";
  const m = Math.floor(seconds / 60);
  const s = seconds % 60;
  return m > 0 ? `${m}m ${s}s` : `${s}s`;
}

export function greeting(date = new Date()): string {
  const hour = date.getHours();
  if (hour < 5) return "Good night";
  if (hour < 12) return "Good morning";
  if (hour < 17) return "Good afternoon";
  return "Good evening";
}

export function pluralize(count: number, singular: string, plural = `${singular}s`): string {
  return `${count} ${count === 1 ? singular : plural}`;
}

/** Labels for SM-2 quality scores as shown on the study screen. */
export const QUALITY_LABELS: { quality: number; label: string; hint: string; key: string }[] = [
  { quality: 0, label: "Blank", hint: "No memory at all", key: "0" },
  { quality: 1, label: "Again", hint: "Wrong, but it rang a bell", key: "1" },
  { quality: 2, label: "Hard", hint: "Wrong, felt close", key: "2" },
  { quality: 3, label: "Good", hint: "Correct with effort", key: "3" },
  { quality: 4, label: "Easy", hint: "Correct after a moment", key: "4" },
  { quality: 5, label: "Excellent", hint: "Instant recall", key: "5" },
];
