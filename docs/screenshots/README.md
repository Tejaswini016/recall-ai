# Screenshots

Current set: captured 16 Sept 2026 from the compose stack with `AI_PROVIDER=groq` (model `openai/gpt-oss-120b`), light theme, 1440x900, by the walkthrough script that also exercises every adaptive feature end to end.

Capture these after `docker compose up --build` (or the local setup) with an account that has a
few decks and reviews, at 1440x900, and save them here with the exact names below so the main
README picks them up.

| File | Screen |
|---|---|
| `dashboard.png` | `/dashboard`, full page: readiness estimate, today's plan, weak and strong topics, upcoming reviews, queue, mistakes, difficulty, mock exams, activity |
| `deck.png` | `/decks/{id}` showing the card list and the Generate cards dialog |
| `study.png` | `/study/all?topic=...` practice session with the difficulty tier badge |
| `quiz-results.png` | `/quiz/{quizId}` results with "Review this mistake" on a missed question |
| `analytics.png` | `/analytics` charts, topic table and difficulty distribution |
| `plan.png` | `/plan` with the coach's summary and today's tasks |
| `exam.png` | `/exams/{id}` timed exam with the navigator |
| `exam-results.png` | `/exams/{id}/results` with the per-topic breakdown |
| `practice-topic.png` | the Practice chooser opened from a weak topic |
| `mistakes.png` | `/mistakes` list |

One-liner per screen with Playwright (no install beyond npx):

```bash
npx playwright screenshot --viewport-size=1440,900 --wait-for-timeout=1500 \
  http://localhost:3000/dashboard docs/screenshots/dashboard.png
```

Log in first in the same browser profile, or pass the `recallai_token` cookie with `--cookie`.
