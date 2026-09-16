"use client";

import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { AlertTriangle, BookOpen, BrainCircuit, CheckCircle2, ClipboardCheck, GraduationCap, Sparkles } from "lucide-react";
import { useToast } from "@/components/providers/ToastProvider";
import { Button } from "@/components/ui/Button";
import { errorMessage } from "@/lib/api";
import { api } from "@/lib/endpoints";
import { cn } from "@/lib/cn";
import type { StudyPlanTask, StudyTaskStatus, StudyTaskType } from "@/types";

const ICONS: Record<StudyTaskType, React.ComponentType<{ className?: string }>> = {
  REVIEW_DUE: GraduationCap,
  LEARN_TOPIC: BookOpen,
  PRACTICE_QUIZ: BrainCircuit,
  REVIEW_MISTAKES: AlertTriangle,
  MOCK_EXAM: ClipboardCheck,
  FINAL_REVISION: Sparkles,
};

export function taskTypeLabel(type: StudyTaskType): string {
  return {
    REVIEW_DUE: "Due cards",
    LEARN_TOPIC: "Learn",
    PRACTICE_QUIZ: "Practice quiz",
    REVIEW_MISTAKES: "Mistakes",
    MOCK_EXAM: "Mock exam",
    FINAL_REVISION: "Final revision",
  }[type];
}

/** One planned block with its check-off controls and a shortcut into the matching activity. */
export function TaskRow({
  task,
  onChange,
  showDate = false,
}: {
  task: StudyPlanTask;
  onChange: (updated: StudyPlanTask) => void;
  showDate?: boolean;
}) {
  const toast = useToast();
  const router = useRouter();
  const [busy, setBusy] = useState(false);
  const [starting, setStarting] = useState(false);
  const Icon = ICONS[task.type];
  const done = task.status === "DONE";
  const skipped = task.status === "SKIPPED";

  const setStatus = async (status: StudyTaskStatus) => {
    setBusy(true);
    try {
      onChange(await api.studyPlans.updateTask(task.planId, task.id, status));
    } catch (error) {
      toast.error(errorMessage(error));
    } finally {
      setBusy(false);
    }
  };

  const startQuiz = async () => {
    if (!task.topic) return;
    setStarting(true);
    try {
      const quiz = await api.ai.topicQuiz({ topic: task.topic, count: 5 });
      router.push(`/quiz/${quiz.id}`);
    } catch (error) {
      toast.error(errorMessage(error));
      setStarting(false);
    }
  };

  const action = (() => {
    switch (task.type) {
      case "REVIEW_DUE":
        return <Link href="/study/all" className="text-sm font-medium text-primary hover:underline">Start review</Link>;
      case "LEARN_TOPIC":
        return task.topic ? (
          <Link href={`/study/all?topic=${encodeURIComponent(task.topic)}`} className="text-sm font-medium text-primary hover:underline">
            Study cards
          </Link>
        ) : null;
      case "PRACTICE_QUIZ":
        return (
          <Button size="sm" variant="secondary" onClick={startQuiz} loading={starting}>
            Generate quiz
          </Button>
        );
      case "REVIEW_MISTAKES":
        return <Link href="/mistakes" className="text-sm font-medium text-primary hover:underline">Open mistakes</Link>;
      case "MOCK_EXAM":
        return <Link href="/exams" className="text-sm font-medium text-primary hover:underline">Mock exams</Link>;
      case "FINAL_REVISION":
        return <Link href="/analytics" className="text-sm font-medium text-primary hover:underline">See weak topics</Link>;
    }
  })();

  return (
    <li className={cn("flex items-start gap-3 py-3", (done || skipped) && "opacity-60")}>
      <button
        type="button"
        onClick={() => setStatus(done ? "PENDING" : "DONE")}
        disabled={busy}
        aria-label={done ? "Mark as not done" : "Mark as done"}
        aria-pressed={done}
        className={cn(
          "mt-0.5 flex h-6 w-6 shrink-0 items-center justify-center rounded-full border transition",
          done ? "border-success bg-success text-white" : "border-border hover:border-primary",
        )}
      >
        {done && <CheckCircle2 className="h-4 w-4" />}
      </button>
      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-center gap-2">
          <Icon className="h-4 w-4 shrink-0 text-muted" aria-hidden />
          <p className={cn("font-medium", done && "line-through")}>{task.title}</p>
          <span className="text-xs text-muted">
            {taskTypeLabel(task.type)} · {task.minutes} min{showDate ? ` · ${task.date}` : ""}
            {skipped ? " · skipped" : ""}
          </span>
        </div>
        {task.description && <p className="mt-1 text-sm text-muted">{task.description}</p>}
        <div className="mt-2 flex flex-wrap items-center gap-3">
          {!done && !skipped && action}
          {!done && (
            <button
              type="button"
              onClick={() => setStatus(skipped ? "PENDING" : "SKIPPED")}
              disabled={busy}
              className="text-xs text-muted hover:text-foreground hover:underline"
            >
              {skipped ? "Restore" : "Skip"}
            </button>
          )}
        </div>
      </div>
    </li>
  );
}
