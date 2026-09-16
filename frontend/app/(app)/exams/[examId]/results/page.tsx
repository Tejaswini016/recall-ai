"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { CheckCircle2, ClipboardCheck, RotateCcw, XCircle } from "lucide-react";
import { DIFFICULTY_LABELS, TYPE_LABELS } from "@/components/exams/examLabels";
import { ReviewMistakeButton } from "@/components/mistakes/ReviewMistakeButton";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card, CardTitle } from "@/components/ui/Card";
import { StatTile } from "@/components/ui/StatTile";
import { ErrorState, LoadingState } from "@/components/ui/States";
import { useApiQuery } from "@/hooks/useApiQuery";
import { api } from "@/lib/endpoints";
import { cn } from "@/lib/cn";
import { formatDuration } from "@/lib/format";

const LETTERS = ["A", "B", "C", "D"];

export default function ExamResultsPage() {
  const params = useParams<{ examId: string }>();
  const examId = Number(params.examId);
  const result = useApiQuery(() => api.mockExams.results(examId), [examId]);

  if (result.loading && !result.data) return <LoadingState label="Loading results…" />;
  if (result.error || !result.data) return <ErrorState message={result.error ?? "Results not found"} onRetry={result.refetch} />;
  const r = result.data;

  return (
    <div className="mx-auto max-w-3xl space-y-6 animate-in">
      <Card className="text-center">
        <p className="text-sm text-muted">
          {r.title} · {DIFFICULTY_LABELS[r.difficulty]}
        </p>
        <p className="mt-2 text-5xl font-bold tabular-nums">{r.percent}%</p>
        <p className="mt-2 text-sm text-muted">
          {r.correctCount} correct · {r.incorrectCount} wrong · {r.skippedCount} skipped · {formatDuration(r.timeTakenSeconds)} of {r.durationMinutes} min
          {r.timedOut ? " · submitted after time ran out" : ""}
        </p>
        <div className="mt-5 flex flex-wrap justify-center gap-2">
          <Link href="/exams">
            <Button>
              <RotateCcw className="h-4 w-4" />
              New mock exam
            </Button>
          </Link>
          {r.incorrectCount + r.skippedCount > 0 && (
            <Link href="/mistakes">
              <Button variant="secondary">
                <ClipboardCheck className="h-4 w-4" />
                Review mistakes
              </Button>
            </Link>
          )}
        </div>
      </Card>

      <div className="grid gap-4 sm:grid-cols-3">
        <StatTile label="Accuracy" value={`${r.percent}%`} tone={r.percent >= 70 ? "success" : "warning"} sublabel={`${r.score} of ${r.totalQuestions}`} />
        <StatTile label="Strong topics" value={r.strongTopics.length} tone="success" sublabel={r.strongTopics.join(", ") || "None yet"} />
        <StatTile label="Weak topics" value={r.weakTopics.length} tone={r.weakTopics.length > 0 ? "warning" : "neutral"} sublabel={r.weakTopics.join(", ") || "None"} />
      </div>

      {r.topics.length > 0 && (
        <Card>
          <CardTitle className="mb-3">By topic</CardTitle>
          <ul className="space-y-2">
            {r.topics.map((t) => (
              <li key={t.topic} className="flex items-center justify-between gap-3 text-sm">
                <span className="font-medium">{t.topic}</span>
                <span className="flex items-center gap-2 text-muted">
                  {t.correct}/{t.total}
                  <Badge tone={t.strong ? "success" : "danger"}>{t.percent}%</Badge>
                </span>
              </li>
            ))}
          </ul>
        </Card>
      )}

      <ol className="space-y-3">
        {r.questions.map((q, i) => (
          <li key={q.id}>
            <Card className={cn("border-l-4", q.correct ? "border-l-success" : "border-l-danger")}>
              <div className="flex items-start gap-3">
                {q.correct ? (
                  <CheckCircle2 className="mt-0.5 h-5 w-5 shrink-0 text-success" aria-label="Correct" />
                ) : (
                  <XCircle className="mt-0.5 h-5 w-5 shrink-0 text-danger" aria-label="Incorrect" />
                )}
                <div className="min-w-0 flex-1">
                  <p className="text-xs text-muted">
                    {TYPE_LABELS[q.type]}
                    {q.topic ? ` · ${q.topic}` : ""}
                  </p>
                  <p className="font-medium">
                    {i + 1}. {q.question}
                  </p>
                  {q.type === "SHORT_ANSWER" ? (
                    <p className="mt-2 text-sm">
                      <span className="text-muted">Your answer: </span>
                      <span className={q.correct ? "text-success" : "text-danger"}>{q.answerText ?? "skipped"}</span>
                      {!q.correct && (
                        <>
                          <span className="mx-2 text-muted">·</span>
                          <span className="text-muted">Expected: </span>
                          <span className="text-success">{q.correctAnswer}</span>
                        </>
                      )}
                    </p>
                  ) : (
                    <ul className="mt-2 space-y-1 text-sm">
                      {q.options.map((option, oi) => (
                        <li
                          key={oi}
                          className={cn(
                            "rounded-md px-2 py-1",
                            oi === q.correctOption && "bg-emerald-50 font-medium text-emerald-900 dark:bg-emerald-950/40 dark:text-emerald-200",
                            oi === q.selectedOption && !q.correct && "bg-red-50 text-red-900 line-through dark:bg-red-950/40 dark:text-red-200",
                          )}
                        >
                          <span className="mr-2 text-muted">{LETTERS[oi]}.</span>
                          {option}
                        </li>
                      ))}
                    </ul>
                  )}
                  {q.skipped && <p className="mt-2 text-xs text-muted">Skipped.</p>}
                  {!q.correct && <p className="mt-3 rounded-lg bg-background p-3 text-sm text-muted">{q.explanation}</p>}
                  {!q.correct && q.mistakeId !== null && (
                    <div className="mt-3">
                      <ReviewMistakeButton mistakeId={q.mistakeId} status={q.mistakeStatus} cardId={q.mistakeCardId} />
                    </div>
                  )}
                </div>
              </div>
            </Card>
          </li>
        ))}
      </ol>
    </div>
  );
}
