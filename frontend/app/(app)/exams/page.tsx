"use client";

import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { ClipboardCheck, Play, Trophy } from "lucide-react";
import { DIFFICULTY_LABELS, TYPE_LABELS } from "@/components/exams/examLabels";
import { AiStatusNotice } from "@/components/decks/AiStatusNotice";
import { useToast } from "@/components/providers/ToastProvider";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card, CardTitle } from "@/components/ui/Card";
import { Input, Select } from "@/components/ui/Field";
import { PageHeader } from "@/components/ui/PageHeader";
import { StatTile } from "@/components/ui/StatTile";
import { EmptyState, ErrorState, Skeleton } from "@/components/ui/States";
import { useApiQuery } from "@/hooks/useApiQuery";
import { errorMessage } from "@/lib/api";
import { api } from "@/lib/endpoints";
import { cn } from "@/lib/cn";
import { formatDateTime, formatDuration } from "@/lib/format";
import type { ExamDifficulty, ExamQuestionType } from "@/types";

const TYPES: ExamQuestionType[] = ["MCQ", "TRUE_FALSE", "SHORT_ANSWER"];

export default function ExamsPage() {
  const router = useRouter();
  const toast = useToast();
  const stats = useApiQuery(() => api.mockExams.stats());
  const decks = useApiQuery(() => api.decks.list({ size: 100 }));
  const insights = useApiQuery(() => api.analytics.topicInsights());

  const [scope, setScope] = useState<"topic" | "deck" | "all">("topic");
  const [topic, setTopic] = useState("");
  const [deckId, setDeckId] = useState<number | "">("");
  const [difficulty, setDifficulty] = useState<ExamDifficulty>("MEDIUM");
  const [count, setCount] = useState(10);
  const [duration, setDuration] = useState(15);
  const [types, setTypes] = useState<ExamQuestionType[]>(TYPES);
  const [busy, setBusy] = useState(false);

  const start = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    try {
      const exam = await api.mockExams.create({
        topic: scope === "topic" ? topic : undefined,
        deckId: scope === "deck" && deckId !== "" ? deckId : undefined,
        difficulty,
        questionCount: count,
        durationMinutes: duration,
        questionTypes: types,
      });
      toast.success(`Exam ready: ${exam.questionCount} questions, ${exam.durationMinutes} minutes. Good luck!`);
      router.push(`/exams/${exam.id}`);
    } catch (error) {
      toast.error(errorMessage(error));
      setBusy(false);
    }
  };

  const canStart = types.length > 0 && (scope === "all" || (scope === "topic" ? topic.trim().length > 0 : deckId !== ""));

  return (
    <div className="space-y-6">
      <PageHeader
        title="Mock exams"
        description="Timed exams generated from your own cards, with multiple-choice, true/false and short-answer questions. Every wrong answer becomes a mistake you can turn into a flashcard."
      />

      {stats.error ? (
        <ErrorState message={stats.error} onRetry={stats.refetch} />
      ) : (
        <div className="grid gap-4 sm:grid-cols-3">
          {stats.loading || !stats.data ? (
            Array.from({ length: 3 }).map((_, i) => <Skeleton key={i} className="h-24" />)
          ) : (
            <>
              <StatTile label="Exams taken" value={stats.data.submitted} icon={ClipboardCheck} />
              <StatTile label="Average score" value={stats.data.averagePercent === null ? "—" : `${stats.data.averagePercent}%`} icon={Trophy} tone="primary" />
              <StatTile label="Best score" value={stats.data.bestPercent === null ? "—" : `${stats.data.bestPercent}%`} tone="success" sublabel={stats.data.latestPercent !== null ? `Latest ${stats.data.latestPercent}%` : undefined} />
            </>
          )}
        </div>
      )}

      <div className="grid gap-6 lg:grid-cols-5">
        <Card className="lg:col-span-2">
          <CardTitle className="mb-4">New mock exam</CardTitle>
          <form onSubmit={start} className="space-y-4">
            <div className="flex rounded-lg border border-border bg-background p-0.5" role="group" aria-label="Question source">
              {(["topic", "deck", "all"] as const).map((s) => (
                <button
                  key={s}
                  type="button"
                  onClick={() => setScope(s)}
                  aria-pressed={scope === s}
                  className={cn("flex-1 rounded-md px-3 py-1.5 text-xs font-medium", scope === s ? "bg-card shadow-sm" : "text-muted")}
                >
                  {s === "topic" ? "One topic" : s === "deck" ? "One deck" : "All my cards"}
                </button>
              ))}
            </div>
            {scope === "topic" && (
              <div>
                <Input label="Topic" value={topic} onChange={(e) => setTopic(e.target.value)} placeholder="Genetics" list="exam-topics" maxLength={150} />
                <datalist id="exam-topics">
                  {(insights.data ?? []).map((t) => (
                    <option key={t.topic} value={t.topic} />
                  ))}
                </datalist>
                {insights.data && insights.data.length > 0 && (
                  <div className="mt-2 flex flex-wrap gap-1.5">
                    {insights.data.slice(0, 6).map((t) => (
                      <button key={t.topic} type="button" onClick={() => setTopic(t.topic)} className="rounded-full border border-border px-2 py-0.5 text-xs text-muted hover:border-primary">
                        {t.topic} · {t.accuracyPercent}%
                      </button>
                    ))}
                  </div>
                )}
              </div>
            )}
            {scope === "deck" && (
              <Select label="Deck" value={deckId} onChange={(e) => setDeckId(e.target.value === "" ? "" : Number(e.target.value))}>
                <option value="">Choose a deck</option>
                {(decks.data?.content ?? []).map((d) => (
                  <option key={d.id} value={d.id}>
                    {d.name} ({d.cardCount} cards)
                  </option>
                ))}
              </Select>
            )}
            <div className="grid gap-4 sm:grid-cols-3">
              <Select label="Difficulty" value={difficulty} onChange={(e) => setDifficulty(e.target.value as ExamDifficulty)}>
                {(Object.keys(DIFFICULTY_LABELS) as ExamDifficulty[]).map((d) => (
                  <option key={d} value={d}>
                    {DIFFICULTY_LABELS[d]}
                  </option>
                ))}
              </Select>
              <Input label="Questions" type="number" min={3} max={30} value={count} onChange={(e) => setCount(Number(e.target.value))} />
              <Input label="Minutes" type="number" min={5} max={180} value={duration} onChange={(e) => setDuration(Number(e.target.value))} />
            </div>
            <div>
              <p className="mb-1 text-sm font-medium">Question types</p>
              <div className="flex flex-wrap gap-1.5" role="group" aria-label="Question types">
                {TYPES.map((t) => {
                  const on = types.includes(t);
                  return (
                    <button
                      key={t}
                      type="button"
                      aria-pressed={on}
                      onClick={() => setTypes((prev) => (on ? prev.filter((x) => x !== t) : [...prev, t]))}
                      className={cn("rounded-lg border px-3 py-1.5 text-sm", on ? "border-primary bg-primary/10 text-primary" : "border-border text-muted")}
                    >
                      {TYPE_LABELS[t]}
                    </button>
                  );
                })}
              </div>
            </div>
            <AiStatusNotice />
            <Button type="submit" className="w-full" loading={busy} disabled={!canStart}>
              <Play className="h-4 w-4" />
              Generate and start
            </Button>
          </form>
        </Card>

        <Card className="lg:col-span-3">
          <CardTitle className="mb-4">History</CardTitle>
          {stats.loading && !stats.data ? (
            <Skeleton className="h-40" />
          ) : !stats.data || stats.data.recent.length === 0 ? (
            <EmptyState icon={ClipboardCheck} title="No exams yet" description="Your first mock exam will appear here with its score and topic breakdown." />
          ) : (
            <ExamHistory />
          )}
        </Card>
      </div>
    </div>
  );
}

function ExamHistory() {
  const list = useApiQuery(() => api.mockExams.list(20));
  if (list.loading && !list.data) return <Skeleton className="h-40" />;
  if (list.error) return <ErrorState message={list.error} onRetry={list.refetch} />;
  return (
    <ul className="divide-y divide-border">
      {(list.data ?? []).map((e) => (
        <li key={e.id} className="flex items-center justify-between gap-3 py-3">
          <div className="min-w-0">
            <p className="truncate font-medium">{e.title}</p>
            <p className="text-xs text-muted">
              {DIFFICULTY_LABELS[e.difficulty]} · {e.questionCount} questions · {e.durationMinutes} min · {formatDateTime(e.startedAt)}
              {e.timeTakenSeconds !== null ? ` · took ${formatDuration(e.timeTakenSeconds)}` : ""}
              {e.timedOut ? " · over time" : ""}
            </p>
          </div>
          <div className="flex shrink-0 items-center gap-2">
            {e.status === "SUBMITTED" ? (
              <>
                <Badge tone={(e.percent ?? 0) >= 70 ? "success" : "warning"}>{e.percent}%</Badge>
                <Link href={`/exams/${e.id}/results`} className="text-sm font-medium text-primary hover:underline">
                  Results
                </Link>
              </>
            ) : (
              <Link href={`/exams/${e.id}`}>
                <Button size="sm">Continue</Button>
              </Link>
            )}
          </div>
        </li>
      ))}
    </ul>
  );
}
