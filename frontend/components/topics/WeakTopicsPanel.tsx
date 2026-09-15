"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { BrainCircuit, GraduationCap } from "lucide-react";
import { TopicBadge } from "@/components/topics/TopicBadge";
import { AiStatusNotice } from "@/components/decks/AiStatusNotice";
import { useToast } from "@/components/providers/ToastProvider";
import { Button } from "@/components/ui/Button";
import { Modal } from "@/components/ui/Modal";
import { ErrorState, Skeleton } from "@/components/ui/States";
import { errorMessage } from "@/lib/api";
import { api } from "@/lib/endpoints";
import { formatDateTime } from "@/lib/format";
import type { TopicInsight } from "@/types";

/**
 * Topic table shared by the dashboard (compact, weak topics only) and the analytics page (every
 * topic). Each row offers a "Practice" action that opens the flashcards-or-quiz chooser.
 */
export function WeakTopicsPanel({
  insights,
  loading,
  error,
  onRetry,
  compact = false,
  limit,
}: {
  insights: TopicInsight[] | null;
  loading: boolean;
  error: string | null;
  onRetry: () => void;
  compact?: boolean;
  limit?: number;
}) {
  const [practising, setPractising] = useState<TopicInsight | null>(null);

  if (loading && !insights) return <Skeleton className={compact ? "h-24" : "h-40"} />;
  if (error) return <ErrorState message={error} onRetry={onRetry} />;

  const rows = (insights ?? []).filter((t) => !compact || t.category === "CRITICAL" || t.category === "WEAK");
  const visible = limit ? rows.slice(0, limit) : rows;

  if (visible.length === 0) {
    return (
      <p className="text-sm text-muted">
        {compact
          ? "No weak topics right now. Review cards and take quizzes and this updates automatically."
          : "Review some cards or take a quiz and every topic you study appears here."}
      </p>
    );
  }

  return (
    <>
      {compact ? (
        <ul className="divide-y divide-border">
          {visible.map((t) => (
            <li key={t.topic} className="flex items-center justify-between gap-3 py-2.5">
              <div className="min-w-0">
                <div className="flex items-center gap-2">
                  <p className="truncate text-sm font-medium">{t.topic}</p>
                  <TopicBadge category={t.category} />
                </div>
                <p className="mt-0.5 text-xs text-muted">
                  {t.accuracyPercent}% accuracy · {t.attempts} attempts · {t.mistakes} mistakes
                </p>
                <p className="text-xs text-muted">{t.recommendedAction}</p>
              </div>
              <Button size="sm" variant="secondary" onClick={() => setPractising(t)}>
                Practice
              </Button>
            </li>
          ))}
        </ul>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead className="text-left text-xs uppercase text-muted">
              <tr>
                <th className="py-2 pr-4">Topic</th>
                <th className="py-2 pr-4">Status</th>
                <th className="py-2 pr-4">Accuracy</th>
                <th className="py-2 pr-4">Attempts</th>
                <th className="py-2 pr-4">Mistakes</th>
                <th className="py-2 pr-4">Last studied</th>
                <th className="py-2 pr-4">Recommended action</th>
                <th className="py-2" />
              </tr>
            </thead>
            <tbody className="divide-y divide-border">
              {visible.map((t) => (
                <tr key={t.topic}>
                  <td className="py-2 pr-4 font-medium">{t.topic}</td>
                  <td className="py-2 pr-4">
                    <TopicBadge category={t.category} />
                  </td>
                  <td className="py-2 pr-4 tabular-nums">{t.accuracyPercent}%</td>
                  <td className="py-2 pr-4 tabular-nums" title={`${t.cardReviews} card reviews, ${t.quizAnswers} quiz answers`}>
                    {t.attempts}
                  </td>
                  <td className="py-2 pr-4 tabular-nums">{t.mistakes}</td>
                  <td className="py-2 pr-4 whitespace-nowrap text-muted">{formatDateTime(t.lastStudiedAt)}</td>
                  <td className="py-2 pr-4 text-muted">{t.recommendedAction}</td>
                  <td className="py-2 text-right">
                    <Button size="sm" variant="secondary" onClick={() => setPractising(t)}>
                      Practice
                    </Button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      <PracticeTopicModal topic={practising} onClose={() => setPractising(null)} />
    </>
  );
}

/** Lets the student choose between a flashcard session and a targeted AI quiz for one topic. */
export function PracticeTopicModal({ topic, onClose }: { topic: TopicInsight | null; onClose: () => void }) {
  const router = useRouter();
  const toast = useToast();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const startQuiz = async () => {
    if (!topic) return;
    setBusy(true);
    setError(null);
    try {
      const quiz = await api.ai.topicQuiz({ topic: topic.topic, count: 5 });
      toast.success(`Practice quiz ready: ${quiz.questionCount} questions`);
      onClose();
      router.push(`/quiz/${quiz.id}`);
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  };

  const startCards = () => {
    if (!topic) return;
    onClose();
    router.push(`/study/all?topic=${encodeURIComponent(topic.topic)}`);
  };

  return (
    <Modal
      open={topic !== null}
      onClose={() => !busy && onClose()}
      title={topic ? `Practice ${topic.topic}` : "Practice"}
      description={topic ? topic.recommendedAction : undefined}
    >
      {topic && (
        <div className="space-y-3">
          <p className="text-sm text-muted">
            {topic.accuracyPercent}% accuracy over {topic.attempts} attempts ({topic.cardReviews} card reviews,{" "}
            {topic.quizAnswers} quiz answers).
          </p>
          <div className="grid gap-3 sm:grid-cols-2">
            <button
              type="button"
              onClick={startCards}
              disabled={busy || topic.cardCount === 0}
              className="flex flex-col items-start gap-2 rounded-xl border border-border bg-card p-4 text-left transition hover:border-primary/60 disabled:opacity-50"
            >
              <GraduationCap className="h-5 w-5 text-primary" />
              <span className="font-semibold">Flashcard session</span>
              <span className="text-xs text-muted">
                {topic.cardCount > 0
                  ? `Review all ${topic.cardCount} cards on this topic, hardest first. Ratings reschedule them with SM-2.`
                  : "No cards carry this topic yet."}
              </span>
            </button>
            <button
              type="button"
              onClick={startQuiz}
              disabled={busy || topic.cardCount === 0}
              className="flex flex-col items-start gap-2 rounded-xl border border-border bg-card p-4 text-left transition hover:border-primary/60 disabled:opacity-50"
            >
              <BrainCircuit className="h-5 w-5 text-primary" />
              <span className="font-semibold">{busy ? "Generating…" : "Targeted quiz"}</span>
              <span className="text-xs text-muted">
                {topic.cardCount > 0
                  ? "Five fresh questions generated from your cards on this topic."
                  : "Needs cards on this topic to build questions from."}
              </span>
            </button>
          </div>
          <AiStatusNotice />
          {error && (
            <p role="alert" className="text-sm text-danger">
              {error}
            </p>
          )}
        </div>
      )}
    </Modal>
  );
}
