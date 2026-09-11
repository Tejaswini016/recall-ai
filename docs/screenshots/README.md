# Screenshots

Capture these after `docker compose up --build` (or the local setup) with an account that has a
few decks and reviews, at 1440x900, and save them here with the exact names below so the main
README picks them up.

| File | Screen |
|---|---|
| `dashboard.png` | `/dashboard` with due cards, streak and weak topics |
| `deck.png` | `/decks/{id}` showing the card list and the Generate cards dialog |
| `study.png` | `/study/{deckId}` with the answer revealed and the six rating buttons |
| `quiz-results.png` | `/quiz/{quizId}` results screen with an explanation for a missed question |
| `analytics.png` | `/analytics` charts |

One-liner per screen with Playwright (no install beyond npx):

```bash
npx playwright screenshot --viewport-size=1440,900 --wait-for-timeout=1500 \
  http://localhost:3000/dashboard docs/screenshots/dashboard.png
```

Log in first in the same browser profile, or pass the `recallai_token` cookie with `--cookie`.
