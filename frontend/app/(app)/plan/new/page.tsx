"use client";

import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useToast } from "@/components/providers/ToastProvider";
import { AiStatusNotice } from "@/components/decks/AiStatusNotice";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { Input, Select } from "@/components/ui/Field";
import { PageHeader } from "@/components/ui/PageHeader";
import { TagInput } from "@/components/ui/TagInput";
import { useApiQuery } from "@/hooks/useApiQuery";
import { ApiRequestError, errorMessage } from "@/lib/api";
import { api } from "@/lib/endpoints";
import { cn } from "@/lib/cn";
import type { KnowledgeLevel } from "@/types";

const DAYS = [
  { n: 1, label: "Mon" },
  { n: 2, label: "Tue" },
  { n: 3, label: "Wed" },
  { n: 4, label: "Thu" },
  { n: 5, label: "Fri" },
  { n: 6, label: "Sat" },
  { n: 7, label: "Sun" },
];

export default function NewPlanPage() {
  const router = useRouter();
  const toast = useToast();
  const insights = useApiQuery(() => api.analytics.topicInsights());
  const [examName, setExamName] = useState("");
  const [examDate, setExamDate] = useState("");
  const [topics, setTopics] = useState<string[]>([]);
  const [level, setLevel] = useState<KnowledgeLevel>("INTERMEDIATE");
  const [minutes, setMinutes] = useState(45);
  const [days, setDays] = useState<number[]>([1, 2, 3, 4, 5, 6, 7]);
  const [busy, setBusy] = useState(false);
  const [errors, setErrors] = useState<Record<string, string>>({});

  // Lazy initial state keeps the clock read out of render, as the purity lint rule requires.
  const [tomorrow] = useState(() => new Date(Date.now() + 86_400_000).toISOString().slice(0, 10));
  const suggestions = (insights.data ?? []).filter((t) => !topics.includes(t.topic)).slice(0, 8);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setErrors({});
    try {
      const plan = await api.studyPlans.create({
        examName,
        examDate,
        topics,
        knowledgeLevel: level,
        minutesPerDay: minutes,
        preferredDays: days,
      });
      toast.success(`Plan created: ${plan.tasks.length} tasks over ${plan.progress.studyDaysTotal} study days`);
      router.push("/plan");
    } catch (error) {
      if (error instanceof ApiRequestError && Object.keys(error.fieldErrors).length > 0) {
        setErrors(error.fieldErrors);
      } else {
        toast.error(errorMessage(error));
      }
      setBusy(false);
    }
  };

  return (
    <div className="mx-auto max-w-2xl">
      <PageHeader
        eyebrow={
          <Link href="/plan" className="hover:underline">
            Study plan
          </Link>
        }
        title="Create a study plan"
        description="The schedule is computed from your topic performance; weak topics get more time and every week ends with a mock exam."
      />
      <Card>
        <form onSubmit={submit} className="space-y-4">
          <Input label="Exam name" value={examName} onChange={(e) => setExamName(e.target.value)} required maxLength={150} error={errors.examName} placeholder="Biology final" />
          <Input label="Exam date" type="date" value={examDate} min={tomorrow} onChange={(e) => setExamDate(e.target.value)} required error={errors.examDate} />
          <div>
            <TagInput label="Topics" value={topics} onChange={setTopics} max={20} maxLength={150} preserveCase placeholder="Type a topic and press Enter" hint="Up to 20 topics, as they appear on your cards. Press Enter or comma to add." />
            {errors.topics && <p className="mt-1 text-xs text-danger">{errors.topics}</p>}
            {suggestions.length > 0 && (
              <div className="mt-2 flex flex-wrap items-center gap-1.5 text-xs text-muted">
                From your results:
                {suggestions.map((t) => (
                  <button
                    key={t.topic}
                    type="button"
                    onClick={() => setTopics((prev) => [...prev, t.topic])}
                    className="rounded-full border border-border px-2 py-0.5 hover:border-primary"
                  >
                    {t.topic} · {t.accuracyPercent}%
                  </button>
                ))}
              </div>
            )}
          </div>
          <div className="grid gap-4 sm:grid-cols-2">
            <Select label="Current knowledge level" value={level} onChange={(e) => setLevel(e.target.value as KnowledgeLevel)} error={errors.knowledgeLevel}>
              <option value="BEGINNER">Beginner: mostly new to me</option>
              <option value="INTERMEDIATE">Intermediate: know the basics</option>
              <option value="ADVANCED">Advanced: revising</option>
            </Select>
            <Input
              label="Minutes per study day"
              type="number"
              min={10}
              max={720}
              step={5}
              value={minutes}
              onChange={(e) => setMinutes(Number(e.target.value))}
              error={errors.minutesPerDay}
            />
          </div>
          <div>
            <p className="mb-1 block text-sm font-medium">Preferred study days</p>
            <div className="flex flex-wrap gap-1.5" role="group" aria-label="Preferred study days">
              {DAYS.map((d) => {
                const on = days.includes(d.n);
                return (
                  <button
                    key={d.n}
                    type="button"
                    aria-pressed={on}
                    onClick={() => setDays((prev) => (on ? prev.filter((x) => x !== d.n) : [...prev, d.n].sort()))}
                    className={cn(
                      "rounded-lg border px-3 py-1.5 text-sm",
                      on ? "border-primary bg-primary/10 text-primary" : "border-border text-muted hover:text-foreground",
                    )}
                  >
                    {d.label}
                  </button>
                );
              })}
            </div>
            {errors.preferredDays && <p className="mt-1 text-xs text-danger">{errors.preferredDays}</p>}
          </div>
          <AiStatusNotice />
          <div className="flex justify-end gap-2">
            <Link href="/plan">
              <Button type="button" variant="secondary" disabled={busy}>
                Cancel
              </Button>
            </Link>
            <Button type="submit" loading={busy} disabled={topics.length === 0 || !examName || !examDate || days.length === 0}>
              Build my plan
            </Button>
          </div>
        </form>
      </Card>
    </div>
  );
}
