"use client";

import { FlaskConical, TriangleAlert } from "lucide-react";
import { useApiQuery } from "@/hooks/useApiQuery";
import { api } from "@/lib/endpoints";

/** Tells the user, inside the generate dialogs, when AI output is simulated or unavailable. */
export function AiStatusNotice() {
  const status = useApiQuery(() => api.ai.status(), []);
  if (!status.data) return null;

  if (status.data.demoMode) {
    return (
      <p
        role="status"
        className="flex items-start gap-2 rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-900 dark:border-amber-900 dark:bg-amber-950/40 dark:text-amber-200"
      >
        <FlaskConical className="mt-0.5 h-4 w-4 shrink-0" aria-hidden />
        <span>
          <strong>Demo mode is on.</strong> Cards are built from your notes with simple rules, not by Claude, and are
          tagged <code>demo</code>. Set a Claude API key and turn off <code>AI_DEMO_MODE</code> for real generation.
        </span>
      </p>
    );
  }
  if (!status.data.available) {
    return (
      <p
        role="alert"
        className="flex items-start gap-2 rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-900 dark:border-red-900 dark:bg-red-950/40 dark:text-red-200"
      >
        <TriangleAlert className="mt-0.5 h-4 w-4 shrink-0" aria-hidden />
        <span>
          AI generation is not configured on this server. Set <code>CLAUDE_API_KEY</code>, or <code>AI_DEMO_MODE=true</code> to
          try the flow without a key.
        </span>
      </p>
    );
  }
  return null;
}
