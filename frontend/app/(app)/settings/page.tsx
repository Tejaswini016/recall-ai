"use client";

import { LogOut } from "lucide-react";
import { useAuth } from "@/components/providers/AuthProvider";
import { Button } from "@/components/ui/Button";
import { Card, CardTitle } from "@/components/ui/Card";
import { PageHeader } from "@/components/ui/PageHeader";
import { API_URL } from "@/lib/env";
import { formatDate, QUALITY_LABELS } from "@/lib/format";

export default function SettingsPage() {
  const { user, signOut } = useAuth();

  return (
    <div className="space-y-6">
      <PageHeader title="Settings" description="Your account and how RecallAI works." />

      <Card>
        <CardTitle className="mb-4">Account</CardTitle>
        <dl className="grid gap-4 sm:grid-cols-3">
          <div>
            <dt className="text-xs uppercase tracking-wide text-muted">Name</dt>
            <dd className="font-medium">{user?.name}</dd>
          </div>
          <div>
            <dt className="text-xs uppercase tracking-wide text-muted">Email</dt>
            <dd className="font-medium">{user?.email}</dd>
          </div>
          <div>
            <dt className="text-xs uppercase tracking-wide text-muted">Member since</dt>
            <dd className="font-medium">{formatDate(user?.createdAt)}</dd>
          </div>
        </dl>
        <Button variant="secondary" className="mt-6" onClick={signOut}>
          <LogOut className="h-4 w-4" />
          Sign out
        </Button>
      </Card>

      <Card>
        <CardTitle className="mb-4">Study shortcuts</CardTitle>
        <ul className="grid gap-2 text-sm sm:grid-cols-2">
          <li className="flex items-center justify-between rounded-lg bg-background px-3 py-2">
            <span>Reveal answer</span>
            <kbd className="rounded border border-border px-1.5 text-xs">Space</kbd>
          </li>
          {QUALITY_LABELS.map((q) => (
            <li key={q.quality} className="flex items-center justify-between rounded-lg bg-background px-3 py-2">
              <span>
                {q.label} <span className="text-muted">· {q.hint}</span>
              </span>
              <kbd className="rounded border border-border px-1.5 text-xs">{q.key}</kbd>
            </li>
          ))}
          <li className="flex items-center justify-between rounded-lg bg-background px-3 py-2">
            <span>Quiz: pick option / continue</span>
            <span className="space-x-1">
              <kbd className="rounded border border-border px-1.5 text-xs">1–4</kbd>
              <kbd className="rounded border border-border px-1.5 text-xs">Enter</kbd>
            </span>
          </li>
        </ul>
      </Card>

      <Card>
        <CardTitle className="mb-2">How scheduling works</CardTitle>
        <p className="text-sm text-muted">
          Every rating runs the SM-2 spaced-repetition algorithm on the server. Ratings of 3 or above lengthen the
          interval (1 day, then 6, then roughly ×2.5 each time); lower ratings reset the card for tomorrow. A card counts as
          mastered once its interval reaches 21 days. Weak topics are flagged when the recent average rating for a topic
          falls below 3. None of this uses AI, which is only used to write cards and quizzes from your material.
        </p>
      </Card>

      <Card>
        <CardTitle className="mb-2">Connection</CardTitle>
        <p className="text-sm text-muted">
          This app talks to <code className="rounded bg-background px-1">{API_URL}</code>. Your session token is stored in a
          cookie on this device and expires automatically.
        </p>
      </Card>
    </div>
  );
}
