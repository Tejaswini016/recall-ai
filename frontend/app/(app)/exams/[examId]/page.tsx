"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { AlertTriangle, ChevronLeft, ChevronRight, Send, Timer } from "lucide-react";
import { DIFFICULTY_LABELS, TYPE_LABELS, formatClock } from "@/components/exams/examLabels";
import { useToast } from "@/components/providers/ToastProvider";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { Textarea } from "@/components/ui/Field";
import { ConfirmDialog } from "@/components/ui/Modal";
import { ProgressBar } from "@/components/ui/StatTile";
import { ErrorState, LoadingState } from "@/components/ui/States";
import { useApiQuery } from "@/hooks/useApiQuery";
import { errorMessage } from "@/lib/api";
import { api } from "@/lib/endpoints";
import { cn } from "@/lib/cn";
import type { MockExam } from "@/types";

const LETTERS = ["A", "B", "C", "D"];
type Answer = { selectedOption?: number; answerText?: string };

export default function TakeExamPage() {
  const params = useParams<{ examId: string }>();
  const examId = Number(params.examId);
  const router = useRouter();
  const exam = useApiQuery(() => api.mockExams.get(examId), [examId]);

  useEffect(() => {
    if (exam.data?.status === "SUBMITTED") router.replace(`/exams/${examId}/results`);
  }, [exam.data, examId, router]);

  if (exam.loading && !exam.data) return <LoadingState label="Loading exam…" />;
  if (exam.error || !exam.data) return <ErrorState message={exam.error ?? "Exam not found"} onRetry={exam.refetch} />;
  if (exam.data.status === "SUBMITTED") return <LoadingState label="Opening results…" />;
  return <ExamSession exam={exam.data} />;
}

function ExamSession({ exam }: { exam: MockExam }) {
  const router = useRouter();
  const toast = useToast();
  const [index, setIndex] = useState(0);
  const [answers, setAnswers] = useState<Record<number, Answer>>({});
  const [remaining, setRemaining] = useState(exam.remainingSeconds);
  const [confirming, setConfirming] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const submitted = useRef(false);
  // The deadline is fixed once from the server's remaining seconds so a slow tick never drifts
  // (a lazy initialiser keeps the clock read out of render).
  const [deadline] = useState(() => Date.now() + exam.remainingSeconds * 1000);

  const question = exam.questions[index];
  const answered = exam.questions.filter((q) => isAnswered(answers[q.id])).length;

  const submit = useCallback(async () => {
    if (submitted.current) return;
    submitted.current = true;
    setSubmitting(true);
    try {
      await api.mockExams.submit(
        exam.id,
        exam.questions
          .filter((q) => isAnswered(answers[q.id]))
          .map((q) => ({ questionId: q.id, selectedOption: answers[q.id]?.selectedOption ?? null, answerText: answers[q.id]?.answerText ?? null })),
      );
      router.replace(`/exams/${exam.id}/results`);
    } catch (error) {
      submitted.current = false;
      setSubmitting(false);
      toast.error(errorMessage(error));
    }
  }, [answers, exam, router, toast]);

  useEffect(() => {
    const tick = setInterval(() => {
      const left = Math.max(0, Math.round((deadline - Date.now()) / 1000));
      setRemaining(left);
      if (left === 0) {
        clearInterval(tick);
        toast.toast("Time is up. Submitting your answers.");
        void submit();
      }
    }, 1000);
    return () => clearInterval(tick);
  }, [submit, toast, deadline]);

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.target instanceof HTMLInputElement || event.target instanceof HTMLTextAreaElement) return;
      if (!question || question.type === "SHORT_ANSWER") return;
      const n = Number(event.key);
      if (n >= 1 && n <= question.options.length) {
        setAnswers((a) => ({ ...a, [question.id]: { selectedOption: n - 1 } }));
      }
      if (event.key === "ArrowRight") setIndex((i) => Math.min(i + 1, exam.questions.length - 1));
      if (event.key === "ArrowLeft") setIndex((i) => Math.max(i - 1, 0));
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [question, exam.questions.length]);

  if (!question) return <ErrorState message="This exam has no questions." />;
  const low = remaining <= 60;

  return (
    <div className="mx-auto max-w-3xl">
      <div className="mb-4 flex flex-wrap items-center justify-between gap-3 text-sm">
        <div className="flex items-center gap-2 text-muted">
          <Link href="/exams" className="hover:underline">
            Exams
          </Link>
          <span>·</span>
          <span className="font-medium text-foreground">{exam.title}</span>
          <Badge>{DIFFICULTY_LABELS[exam.difficulty]}</Badge>
        </div>
        <div className={cn("flex items-center gap-2 rounded-lg border px-3 py-1.5 font-mono text-base tabular-nums", low ? "border-red-300 bg-red-50 text-red-700 dark:bg-red-950/40 dark:text-red-300" : "border-border bg-card")} role="timer" aria-live={low ? "assertive" : "off"}>
          <Timer className="h-4 w-4" aria-hidden />
          {formatClock(remaining)}
        </div>
      </div>
      <ProgressBar value={(answered / exam.questions.length) * 100} className="mb-4" label="Answered" />

      <div className="grid gap-4 md:grid-cols-[1fr_180px]">
        <Card key={question.id} className="animate-flip p-6 sm:p-8">
          <div className="flex items-center justify-between text-xs text-muted">
            <span>
              Question {index + 1} of {exam.questions.length} · {TYPE_LABELS[question.type]}
            </span>
            {question.topic && <Badge tone="primary">{question.topic}</Badge>}
          </div>
          <p className="mt-2 text-xl font-semibold leading-snug sm:text-2xl">{question.question}</p>

          {question.type === "SHORT_ANSWER" ? (
            <div className="mt-6">
              <Textarea
                label="Your answer"
                rows={2}
                value={answers[question.id]?.answerText ?? ""}
                onChange={(e) => setAnswers((a) => ({ ...a, [question.id]: { answerText: e.target.value } }))}
                placeholder="A word or short phrase"
                hint="Graded on the key words, so spelling matters more than sentence structure."
                maxLength={500}
              />
            </div>
          ) : (
            <div className="mt-6 space-y-2" role="radiogroup" aria-label="Options">
              {question.options.map((option, oi) => {
                const active = answers[question.id]?.selectedOption === oi;
                return (
                  <button
                    key={oi}
                    type="button"
                    role="radio"
                    aria-checked={active}
                    onClick={() => setAnswers((a) => ({ ...a, [question.id]: { selectedOption: oi } }))}
                    className={cn(
                      "flex w-full items-start gap-3 rounded-xl border p-3 text-left text-sm transition focus:outline-none focus-visible:ring-2 focus-visible:ring-primary",
                      active ? "border-primary bg-primary/10" : "border-border bg-card hover:border-primary/50",
                    )}
                  >
                    <span className={cn("flex h-6 w-6 shrink-0 items-center justify-center rounded-full border text-xs font-semibold", active ? "border-primary bg-primary text-primary-foreground" : "border-border text-muted")}>
                      {LETTERS[oi]}
                    </span>
                    <span className="pt-0.5">{option}</span>
                  </button>
                );
              })}
            </div>
          )}

          <div className="mt-6 flex items-center justify-between">
            <Button variant="secondary" size="sm" onClick={() => setIndex((i) => Math.max(0, i - 1))} disabled={index === 0}>
              <ChevronLeft className="h-4 w-4" />
              Previous
            </Button>
            {index < exam.questions.length - 1 ? (
              <Button size="sm" onClick={() => setIndex((i) => i + 1)}>
                Next
                <ChevronRight className="h-4 w-4" />
              </Button>
            ) : (
              <Button size="sm" onClick={() => setConfirming(true)} loading={submitting}>
                <Send className="h-4 w-4" />
                Submit exam
              </Button>
            )}
          </div>
        </Card>

        <Card className="h-fit">
          <p className="mb-2 text-xs font-medium uppercase tracking-wide text-muted">Navigator</p>
          <div className="grid grid-cols-5 gap-1.5 md:grid-cols-4">
            {exam.questions.map((q, i) => (
              <button
                key={q.id}
                type="button"
                onClick={() => setIndex(i)}
                aria-label={`Question ${i + 1}${isAnswered(answers[q.id]) ? ", answered" : ""}`}
                aria-current={i === index ? "true" : undefined}
                className={cn(
                  "h-8 rounded-md border text-xs font-semibold",
                  i === index ? "border-primary ring-2 ring-primary/40" : "border-border",
                  isAnswered(answers[q.id]) ? "bg-primary/15 text-primary" : "bg-card text-muted",
                )}
              >
                {i + 1}
              </button>
            ))}
          </div>
          <p className="mt-3 text-xs text-muted">
            {answered} of {exam.questions.length} answered
          </p>
          <Button className="mt-3 w-full" size="sm" variant="secondary" onClick={() => setConfirming(true)} loading={submitting}>
            Submit
          </Button>
          <p className="mt-3 text-[11px] text-muted">Keys 1–4 select · arrows move</p>
        </Card>
      </div>

      <ConfirmDialog
        open={confirming}
        onClose={() => setConfirming(false)}
        onConfirm={() => {
          setConfirming(false);
          void submit();
        }}
        loading={submitting}
        confirmLabel="Submit"
        title="Submit the exam?"
        description={
          answered < exam.questions.length
            ? `${exam.questions.length - answered} question${exam.questions.length - answered === 1 ? " is" : "s are"} unanswered and will count as wrong.`
            : "All questions answered. You cannot change answers after submitting."
        }
      />
      {low && (
        <p className="mt-3 flex items-center justify-center gap-1 text-xs text-danger">
          <AlertTriangle className="h-3.5 w-3.5" /> Less than a minute left; answers submit automatically at zero.
        </p>
      )}
    </div>
  );
}

function isAnswered(answer: Answer | undefined): boolean {
  if (!answer) return false;
  if (answer.selectedOption !== undefined) return true;
  return (answer.answerText ?? "").trim().length > 0;
}
