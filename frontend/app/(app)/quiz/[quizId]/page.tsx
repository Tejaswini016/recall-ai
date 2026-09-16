"use client";

import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { CheckCircle2, RotateCcw, XCircle } from "lucide-react";
import { ReviewMistakeButton } from "@/components/mistakes/ReviewMistakeButton";
import { useToast } from "@/components/providers/ToastProvider";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { ProgressBar } from "@/components/ui/StatTile";
import { ErrorState, LoadingState } from "@/components/ui/States";
import { useApiQuery } from "@/hooks/useApiQuery";
import { errorMessage } from "@/lib/api";
import { api } from "@/lib/endpoints";
import { cn } from "@/lib/cn";
import { formatDuration, pluralize } from "@/lib/format";
import type { QuizAttempt } from "@/types";

const LETTERS = ["A", "B", "C", "D"];

export default function QuizPage() {
  const params = useParams<{ quizId: string }>();
  const quizId = Number(params.quizId);
  const toast = useToast();
  const quiz = useApiQuery(() => api.quizzes.get(quizId), [quizId]);

  const [index, setIndex] = useState(0);
  const [answers, setAnswers] = useState<Record<number, number>>({});
  const [result, setResult] = useState<QuizAttempt | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const startedAt = useRef<number | null>(null);

  const questions = quiz.data?.questions ?? [];
  const question = questions[index];
  const selected = question ? answers[question.id] : undefined;
  const last = index === questions.length - 1;

  const next = async () => {
    if (!last) {
      setIndex((i) => i + 1);
      return;
    }
    setSubmitting(true);
    try {
      const attempt = await api.quizzes.submit(
        quizId,
        questions.map((q) => ({ questionId: q.id, selectedAnswer: answers[q.id] ?? null })),
        Math.round((Date.now() - (startedAt.current ?? Date.now())) / 1000),
      );
      setResult(attempt);
    } catch (error) {
      toast.error(errorMessage(error));
    } finally {
      setSubmitting(false);
    }
  };

  useEffect(() => {
    startedAt.current ??= Date.now();
  }, []);

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (result || !question) return;
      const n = Number(event.key);
      if (n >= 1 && n <= 4) setAnswers((a) => ({ ...a, [question.id]: n - 1 }));
      if (event.key === "Enter" && selected !== undefined) void next();
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [question, selected, result, index]);

  const retry = () => {
    setIndex(0);
    setAnswers({});
    setResult(null);
    startedAt.current = Date.now();
    quiz.refetch();
  };

  if (quiz.loading && !quiz.data) return <LoadingState label="Loading quiz…" />;
  if (quiz.error || !quiz.data) return <ErrorState message={quiz.error ?? "Quiz not found"} onRetry={quiz.refetch} />;

  if (result) {
    return (
      <div className="mx-auto max-w-2xl space-y-6 animate-in">
        <Card className="text-center">
          <p className="text-sm text-muted">{result.quizTitle}</p>
          <p className="mt-2 text-5xl font-bold tabular-nums">{result.percent}%</p>
          <p className="mt-2 text-sm text-muted">
            {result.correctCount} correct · {result.incorrectCount} wrong · {formatDuration(result.durationSeconds)}
          </p>
          <div className="mt-5 flex flex-wrap justify-center gap-2">
            <Button onClick={retry}>
              <RotateCcw className="h-4 w-4" />
              Retry quiz
            </Button>
            <Link href={`/decks/${quiz.data.deckId}`}>
              <Button variant="secondary">Back to deck</Button>
            </Link>
          </div>
        </Card>

        <ol className="space-y-3">
          {result.results.map((r, i) => (
            <li key={r.questionId}>
              <Card className={cn("border-l-4", r.correct ? "border-l-success" : "border-l-danger")}>
                <div className="flex items-start gap-3">
                  {r.correct ? (
                    <CheckCircle2 className="mt-0.5 h-5 w-5 shrink-0 text-success" aria-label="Correct" />
                  ) : (
                    <XCircle className="mt-0.5 h-5 w-5 shrink-0 text-danger" aria-label="Incorrect" />
                  )}
                  <div className="min-w-0 flex-1">
                    <p className="font-medium">
                      {i + 1}. {r.question}
                    </p>
                    <ul className="mt-2 space-y-1 text-sm">
                      {r.options.map((option, oi) => (
                        <li
                          key={oi}
                          className={cn(
                            "rounded-md px-2 py-1",
                            oi === r.correctAnswer && "bg-emerald-50 font-medium text-emerald-900 dark:bg-emerald-950/40 dark:text-emerald-200",
                            oi === r.selectedAnswer && !r.correct && "bg-red-50 text-red-900 line-through dark:bg-red-950/40 dark:text-red-200",
                          )}
                        >
                          <span className="mr-2 text-muted">{LETTERS[oi]}.</span>
                          {option}
                        </li>
                      ))}
                    </ul>
                    {r.selectedAnswer === null && <p className="mt-2 text-xs text-muted">Skipped.</p>}
                    {!r.correct && <p className="mt-3 rounded-lg bg-background p-3 text-sm text-muted">{r.explanation}</p>}
                    {!r.correct && r.mistakeId !== null && (
                      <div className="mt-3">
                        <ReviewMistakeButton mistakeId={r.mistakeId} status={r.mistakeStatus} cardId={r.mistakeCardId} />
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

  if (!question) {
    return <ErrorState message="This quiz has no questions." />;
  }

  return (
    <div className="mx-auto max-w-2xl">
      <div className="mb-4 flex items-center justify-between text-sm text-muted">
        <Link href={`/decks/${quiz.data.deckId}`} className="hover:underline">
          Exit
        </Link>
        <span>
          Question {index + 1} of {questions.length}
        </span>
      </div>
      <ProgressBar value={(index / questions.length) * 100} className="mb-6" label="Quiz progress" />

      <Card key={question.id} className="animate-flip p-6 sm:p-8">
        <p className="text-sm text-muted">{quiz.data.title}</p>
        <p className="mt-2 text-xl font-semibold leading-snug sm:text-2xl">{question.question}</p>
        <div className="mt-6 space-y-2" role="radiogroup" aria-label="Options">
          {question.options.map((option, oi) => {
            const active = selected === oi;
            return (
              <button
                key={oi}
                type="button"
                role="radio"
                aria-checked={active}
                onClick={() => setAnswers((a) => ({ ...a, [question.id]: oi }))}
                className={cn(
                  "flex w-full items-start gap-3 rounded-xl border p-3 text-left text-sm transition focus:outline-none focus-visible:ring-2 focus-visible:ring-primary",
                  active ? "border-primary bg-primary/10" : "border-border bg-card hover:border-primary/50",
                )}
              >
                <span
                  className={cn(
                    "flex h-6 w-6 shrink-0 items-center justify-center rounded-full border text-xs font-semibold",
                    active ? "border-primary bg-primary text-primary-foreground" : "border-border text-muted",
                  )}
                >
                  {LETTERS[oi]}
                </span>
                <span className="pt-0.5">{option}</span>
              </button>
            );
          })}
        </div>
        <div className="mt-6 flex items-center justify-between">
          <p className="text-xs text-muted">Keys 1–4 select · Enter continues</p>
          <Button onClick={next} disabled={selected === undefined} loading={submitting}>
            {last ? "Finish quiz" : "Next"}
          </Button>
        </div>
      </Card>
      <p className="mt-3 text-center text-xs text-muted">{pluralize(Object.keys(answers).length, "question")} answered</p>
    </div>
  );
}
