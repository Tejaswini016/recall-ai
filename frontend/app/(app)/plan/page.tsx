"use client";

import { useState } from "react";
import Link from "next/link";
import { CalendarDays, Plus, RefreshCw, Sparkles } from "lucide-react";
import { TaskRow } from "@/components/plan/TaskRow";
import { useToast } from "@/components/providers/ToastProvider";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card, CardTitle } from "@/components/ui/Card";
import { ConfirmDialog } from "@/components/ui/Modal";
import { PageHeader } from "@/components/ui/PageHeader";
import { ProgressBar, StatTile } from "@/components/ui/StatTile";
import { EmptyState, ErrorState, LoadingState, Skeleton } from "@/components/ui/States";
import { useApiQuery } from "@/hooks/useApiQuery";
import { errorMessage } from "@/lib/api";
import { api } from "@/lib/endpoints";
import { cn } from "@/lib/cn";
import { formatDate, formatShortDate, pluralize, relativeDay } from "@/lib/format";
import type { StudyPlan, StudyPlanTask } from "@/types";

const TABS = ["today", "week", "progress", "completed"] as const;
type Tab = (typeof TABS)[number];
const TAB_LABELS: Record<Tab, string> = { today: "Today", week: "This week", progress: "Progress", completed: "Completed" };

export default function PlanPage() {
  const plans = useApiQuery(() => api.studyPlans.list());
  const active = plans.data?.find((p) => p.status === "ACTIVE") ?? plans.data?.[0];
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const planId = selectedId ?? active?.id ?? null;
  const plan = useApiQuery(() => (planId ? api.studyPlans.get(planId) : Promise.resolve(null)), [planId]);

  if (plans.loading && !plans.data) return <LoadingState label="Loading your plan…" />;
  if (plans.error) return <ErrorState message={plans.error} onRetry={plans.refetch} />;

  if (!plans.data || plans.data.length === 0) {
    return (
      <div className="space-y-6">
        <PageHeader title="Study plan" description="A dated plan for your exam, built from your weak topics, cards and mistakes." />
        <EmptyState
          icon={CalendarDays}
          title="No study plan yet"
          description="Tell RecallAI the exam, its date and the topics. It schedules learning, practice quizzes, mistake reviews and weekly mock exams around the time you have."
          action={
            <Link href="/plan/new">
              <Button>
                <Plus className="h-4 w-4" />
                Create a plan
              </Button>
            </Link>
          }
        />
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="Study plan"
        description="Built from your topic performance; regenerate it after new results to rebalance the remaining days."
        actions={
          <>
            {plans.data.length > 1 && (
              <select
                aria-label="Plan"
                value={planId ?? undefined}
                onChange={(e) => setSelectedId(Number(e.target.value))}
                className="rounded-lg border border-border bg-card px-3 py-2 text-sm"
              >
                {plans.data.map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.examName} · {formatShortDate(p.examDate)} ({p.status.toLowerCase()})
                  </option>
                ))}
              </select>
            )}
            <Link href="/plan/new">
              <Button variant="secondary">
                <Plus className="h-4 w-4" />
                New plan
              </Button>
            </Link>
          </>
        }
      />
      {plan.loading && !plan.data ? (
        <Skeleton className="h-64" />
      ) : plan.error ? (
        <ErrorState message={plan.error} onRetry={plan.refetch} />
      ) : plan.data ? (
        <PlanView plan={plan.data} onChange={(updated) => plan.setData(() => updated)} onDeleted={() => { setSelectedId(null); plans.refetch(); }} />
      ) : null}
    </div>
  );
}

function PlanView({ plan, onChange, onDeleted }: { plan: StudyPlan; onChange: (plan: StudyPlan) => void; onDeleted: () => void }) {
  const toast = useToast();
  const [tab, setTab] = useState<Tab>("today");
  const [regenerating, setRegenerating] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [busy, setBusy] = useState(false);
  const [today] = useState(() => new Date().toISOString().slice(0, 10));
  const [weekEndIso] = useState(() => {
    const weekEnd = new Date();
    weekEnd.setDate(weekEnd.getDate() + 6);
    return weekEnd.toISOString().slice(0, 10);
  });
  const p = plan.progress;

  const replaceTask = (updated: StudyPlanTask) => {
    onChange({ ...plan, tasks: plan.tasks.map((t) => (t.id === updated.id ? updated : t)) });
  };

  const regenerate = async () => {
    setRegenerating(true);
    try {
      const updated = await api.studyPlans.regenerate(plan.id);
      onChange(updated);
      toast.success(updated.aiGenerated ? "Plan rebalanced with fresh advice" : "Plan rebalanced");
    } catch (error) {
      toast.error(errorMessage(error));
    } finally {
      setRegenerating(false);
    }
  };

  const setStatus = async (status: StudyPlan["status"]) => {
    setBusy(true);
    try {
      const summary = await api.studyPlans.updateStatus(plan.id, status);
      onChange({ ...plan, status: summary.status });
      toast.success(`Plan ${status.toLowerCase()}`);
    } catch (error) {
      toast.error(errorMessage(error));
    } finally {
      setBusy(false);
    }
  };

  const remove = async () => {
    setBusy(true);
    try {
      await api.studyPlans.remove(plan.id);
      toast.success("Plan deleted");
      onDeleted();
    } catch (error) {
      toast.error(errorMessage(error));
      setBusy(false);
    }
  };

  const todayTasks = plan.tasks.filter((t) => t.date === today);
  const overdue = plan.tasks.filter((t) => t.date < today && t.status === "PENDING");
  const weekTasks = plan.tasks.filter((t) => t.date >= today && t.date <= weekEndIso);
  const byDay = new Map<string, StudyPlanTask[]>();
  for (const t of weekTasks) byDay.set(t.date, [...(byDay.get(t.date) ?? []), t]);
  const completed = plan.tasks.filter((t) => t.status !== "PENDING").sort((a, b) => (b.completedAt ?? "").localeCompare(a.completedAt ?? ""));
  const minutesByTopic = new Map<string, { done: number; total: number }>();
  for (const t of plan.tasks) {
    if (!t.topic) continue;
    const entry = minutesByTopic.get(t.topic) ?? { done: 0, total: 0 };
    entry.total += t.minutes;
    if (t.status === "DONE") entry.done += t.minutes;
    minutesByTopic.set(t.topic, entry);
  }

  return (
    <>
      <Card>
        <div className="flex flex-col gap-4 md:flex-row md:items-start md:justify-between">
          <div className="min-w-0">
            <div className="flex flex-wrap items-center gap-2">
              <h2 className="text-xl font-bold">{plan.examName}</h2>
              <Badge tone={plan.status === "ACTIVE" ? "primary" : plan.status === "COMPLETED" ? "success" : "neutral"}>
                {plan.status.toLowerCase()}
              </Badge>
            </div>
            <p className="mt-1 text-sm text-muted">
              Exam {formatDate(plan.examDate)} ({relativeDay(plan.examDate)}) · {plan.minutesPerDay} min per study day ·{" "}
              {pluralize(p.studyDaysLeft, "study day")} left of {p.studyDaysTotal}
            </p>
            <div className="mt-2 flex flex-wrap gap-1.5">
              {plan.topics.map((t) => (
                <Badge key={t}>{t}</Badge>
              ))}
            </div>
          </div>
          <div className="flex flex-wrap gap-2">
            {plan.status === "ACTIVE" && (
              <Button variant="secondary" onClick={regenerate} loading={regenerating}>
                <RefreshCw className="h-4 w-4" />
                Regenerate
              </Button>
            )}
            {plan.status === "ACTIVE" ? (
              <Button variant="ghost" onClick={() => setStatus("COMPLETED")} disabled={busy}>
                Mark completed
              </Button>
            ) : (
              <Button variant="ghost" onClick={() => setStatus("ACTIVE")} disabled={busy}>
                Reactivate
              </Button>
            )}
            <Button variant="ghost" className="text-danger" onClick={() => setDeleting(true)} disabled={busy}>
              Delete
            </Button>
          </div>
        </div>
        <div className="mt-4">
          <div className="mb-1 flex justify-between text-xs text-muted">
            <span>
              {p.doneTasks} of {p.totalTasks} tasks done · {Math.round(p.doneMinutes / 60)}h of {Math.round(p.totalMinutes / 60)}h
            </span>
            <span className={p.onTrack ? "text-success" : "text-warning"}>{p.onTrack ? "On track" : "Behind schedule"}</span>
          </div>
          <ProgressBar value={p.percentComplete} label="Plan progress" />
        </div>
      </Card>

      {plan.summary && (
        <Card>
          <div className="mb-2 flex items-center gap-2">
            <Sparkles className="h-4 w-4 text-primary" aria-hidden />
            <CardTitle>{plan.aiGenerated ? "Coach's summary" : "Plan summary"}</CardTitle>
            {plan.aiGenerated ? <Badge tone="primary">AI-written</Badge> : <Badge>Computed</Badge>}
          </div>
          <p className="text-sm text-muted">{plan.summary}</p>
          {plan.topicAdvice.length > 0 && (
            <ul className="mt-3 grid gap-2 sm:grid-cols-2">
              {plan.topicAdvice.map((a) => (
                <li key={a.topic} className="rounded-lg bg-background p-3 text-sm">
                  <p className="font-medium">{a.topic}</p>
                  <p className="text-muted">{a.advice}</p>
                </li>
              ))}
            </ul>
          )}
          <p className="mt-2 text-xs text-muted">The schedule itself is computed from your results; the model only writes the summary and tips.</p>
        </Card>
      )}

      <div className="flex gap-1 border-b border-border" role="tablist">
        {TABS.map((t) => (
          <button
            key={t}
            role="tab"
            aria-selected={tab === t}
            onClick={() => setTab(t)}
            className={cn(
              "-mb-px border-b-2 px-3 py-2 text-sm font-medium",
              tab === t ? "border-primary text-primary" : "border-transparent text-muted hover:text-foreground",
            )}
          >
            {TAB_LABELS[t]}
          </button>
        ))}
      </div>

      {tab === "today" && (
        <Card>
          <CardTitle className="mb-2">Today · {pluralize(todayTasks.reduce((m, t) => m + t.minutes, 0), "minute")}</CardTitle>
          {todayTasks.length === 0 ? (
            <p className="text-sm text-muted">No tasks today. {plan.status === "ACTIVE" ? "Enjoy the rest day, or start tomorrow's block early." : ""}</p>
          ) : (
            <ul className="divide-y divide-border">
              {todayTasks.map((t) => (
                <TaskRow key={t.id} task={t} onChange={replaceTask} />
              ))}
            </ul>
          )}
          {overdue.length > 0 && (
            <div className="mt-6">
              <CardTitle className="mb-2">Carried over</CardTitle>
              <ul className="divide-y divide-border">
                {overdue.slice(0, 10).map((t) => (
                  <TaskRow key={t.id} task={t} onChange={replaceTask} showDate />
                ))}
              </ul>
            </div>
          )}
        </Card>
      )}

      {tab === "week" && (
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          {[...byDay.entries()].map(([date, tasks]) => (
            <Card key={date}>
              <CardTitle className="mb-2">
                {formatShortDate(date)} <span className="font-normal text-muted">· {relativeDay(date)}</span>
              </CardTitle>
              <ul className="divide-y divide-border">
                {tasks.map((t) => (
                  <TaskRow key={t.id} task={t} onChange={replaceTask} />
                ))}
              </ul>
            </Card>
          ))}
          {byDay.size === 0 && <p className="text-sm text-muted">Nothing scheduled in the next seven days.</p>}
        </div>
      )}

      {tab === "progress" && (
        <div className="space-y-4">
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <StatTile label="Complete" value={`${p.percentComplete}%`} tone="primary" sublabel={`${p.doneTasks} done · ${p.skippedTasks} skipped`} />
            <StatTile label="Study time" value={`${Math.round(p.doneMinutes / 60)}h`} sublabel={`of ${Math.round(p.totalMinutes / 60)}h planned`} />
            <StatTile label="Days to exam" value={p.daysUntilExam} tone={p.daysUntilExam <= 3 ? "warning" : "neutral"} sublabel={`${p.studyDaysLeft} study days left`} />
            <StatTile label="Pace" value={p.onTrack ? "On track" : "Behind"} tone={p.onTrack ? "success" : "warning"} />
          </div>
          <Card>
            <CardTitle className="mb-3">Minutes per topic</CardTitle>
            <ul className="space-y-3">
              {[...minutesByTopic.entries()].map(([topic, m]) => (
                <li key={topic}>
                  <div className="mb-1 flex justify-between text-sm">
                    <span className="font-medium">{topic}</span>
                    <span className="text-muted">
                      {m.done} / {m.total} min
                    </span>
                  </div>
                  <ProgressBar value={m.total === 0 ? 0 : (m.done * 100) / m.total} label={`${topic} progress`} />
                </li>
              ))}
            </ul>
          </Card>
        </div>
      )}

      {tab === "completed" && (
        <Card>
          {completed.length === 0 ? (
            <p className="text-sm text-muted">Nothing completed yet. Check tasks off from the Today tab.</p>
          ) : (
            <ul className="divide-y divide-border">
              {completed.map((t) => (
                <TaskRow key={t.id} task={t} onChange={replaceTask} showDate />
              ))}
            </ul>
          )}
        </Card>
      )}

      <ConfirmDialog
        open={deleting}
        onClose={() => setDeleting(false)}
        onConfirm={remove}
        loading={busy}
        title="Delete this plan?"
        description={`The plan for "${plan.examName}" and its tasks will be removed. Your cards and results are not affected.`}
      />
    </>
  );
}
