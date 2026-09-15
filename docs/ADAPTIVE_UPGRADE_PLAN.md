# Adaptive study companion upgrade: implementation plan

Written before any code change, after inspecting the backend (entities, migrations V1-V3, services,
repositories, the `ClaudeClient` provider abstraction with Anthropic/Gemini/Groq/demo implementations),
the SM-2 scheduler, analytics, the quiz pipeline and the Next.js frontend (routes, `lib/endpoints.ts`,
`types/index.ts`, dashboard, study and quiz pages).

Hard constraints: Groq stays the working provider, no Anthropic key requirement is added, every existing
feature and test keeps working, all model output is validated before it is stored, no secrets in code,
logs or git.

## What exists and is reused

| Concern | Existing piece | How the upgrade uses it |
|---|---|---|
| AI calls | `ClaudeClient` + `AiRetryService` + `AiResponseValidator` + `AiCacheService` | New prompt builders plug into `PromptService`; every new operation gets a schema, a validator and a cache namespace (`AiOperation`) |
| Scheduling | `Sm2Service` (pure) + `ReviewService.review` | SM-2 stays untouched; adaptive difficulty is a post-step that adjusts the SM-2 interval by a bounded factor |
| Topic data | `cards.topic`, `review_history`, `quiz_attempt_answers` | Topic insight combines card reviews and quiz answers; quiz questions gain a `topic` column |
| Analytics | `AnalyticsService`, `WeakTopicDetector` | Kept as-is (existing endpoints stay); new `TopicInsightService`, `ReadinessService` sit beside them |
| Frontend | `useApiQuery`, `api` endpoint map, ui components | New pages reuse the same primitives; nav gains Plan, Exams, Mistakes |

## Database changes (one Flyway migration per phase)

- V4 (phase 1): `quiz_questions.topic VARCHAR(150)`.
- V5 (phase 2): `cards.difficulty` (EASY/MEDIUM/HARD/EXPERT, default MEDIUM), `cards.success_streak`,
  `cards.lapse_count`, `cards.total_reviews`, `cards.avg_response_ms`; `review_history.response_ms`,
  `review_history.difficulty_after`.
- V6 (phase 3): `mistakes` table (user, source QUIZ/MOCK_EXAM, question text, given/correct answer,
  explanation, topic, deck, quiz_question, occurrences, status OPEN/CONVERTED/DISMISSED, card link);
  `cards.origin` (MANUAL/AI/MISTAKE) and `cards.mistake_id`.
- V7 (phase 4): `study_plans` and `study_plan_tasks`.
- V8 (phase 5): `mock_exams` and `mock_exam_questions` (answers stored on the question row; one attempt
  per exam); `mistakes.mock_exam_question_id`.

## Phase 1: weak topic detection

- Quiz prompt v2 asks for a `topic` per question; validator accepts an optional topic (max 150 chars).
- `TopicInsightService` merges `review_history` (per card topic) with `quiz_attempt_answers` (per
  question topic, falling back to the deck name) into one row per topic: accuracy %, attempts,
  mistakes, card/quiz breakdown, last studied, category and recommended action.
- Categories (deterministic): fewer than 3 attempts = UNRATED; accuracy >= 85 STRONG; >= 70 GOOD;
  >= 50 WEAK; below 50 CRITICAL.
- Endpoints: `GET /api/analytics/topic-insights`, `GET /api/reviews/practice?topic=` (cards of a topic,
  hardest first, graded through the normal SM-2 endpoint) and `POST /api/ai/quiz/topic` (targeted quiz
  built from the user's cards on that topic).
- Frontend: dashboard "Your weak topics" panel with a Practice action (flashcards or quiz); analytics
  page shows every topic with its category; study page accepts `?topic=`.

## Phase 2: adaptive difficulty

- `AdaptiveDifficultyService` (pure): tier moves one step harder on a lapse (two on quality 0); one
  step easier after three consecutive successes with quality >= 4 that were not unusually slow.
- Interval multiplier after SM-2 (only from the third repetition on, so SM-2's 1 and 6 day seeds are
  untouched): EASY 1.15, MEDIUM 1.0, HARD 0.85, EXPERT 0.7, never below 1 day.
- `POST /api/reviews/{cardId}` accepts optional `responseMs`; response reports the tier and whether it
  changed. Card responses expose the tier and counters. `GET /api/analytics/difficulty` gives the
  distribution.
- Frontend: the study page measures time-to-reveal, shows the tier and announces changes.

## Phase 3: mistake to flashcard to revision

- Wrong quiz answers create or bump a `mistakes` row when an attempt is submitted.
- `POST /api/mistakes/{id}/flashcard` asks the model (new `MistakePromptBuilder`, operation
  MISTAKE_CARD, validated) for an explanatory card; if the model is unavailable or invalid the card is
  built deterministically from the stored question, correct answer and explanation. The card is saved
  with origin MISTAKE, due today, so SM-2 schedules it.
- Endpoints: list/summary/dismiss. Frontend: "Review this mistake" on quiz results, `/mistakes` page,
  dashboard "Mistakes to review".

## Phase 4: AI study planner

- `StudyPlanAllocator` (pure, deterministic) turns exam date, preferred weekdays, minutes per day,
  topics and topic insights into dated tasks (review due cards, learn/practise a topic, review
  mistakes, weekly mock exam, final revision). Weak topics get more time.
- The model adds a validated summary and per-topic advice (operation STUDY_PLAN); when unavailable the
  plan is still created with a deterministic summary.
- Endpoints: create, list, get, today, regenerate (rebuilds pending future tasks from fresh insights),
  task status update, delete. Frontend `/plan` (Today, Week, Progress, Completed) and `/plan/new`.

## Phase 5: mock exam generator

- `MockExamPromptBuilder` (operation MOCK_EXAM) generates MCQ, true/false and short-answer questions
  from the user's cards for a topic (or deck) at a difficulty; validator checks every type.
- Short answers are graded by a rule-based `ShortAnswerGrader` (normalised exact match, accepted
  alternatives, keyword overlap). Wrong answers become mistakes with source MOCK_EXAM.
- Endpoints: create, get (no answers, time remaining), submit, results, history. Frontend `/exams`,
  `/exams/[id]` (timer, navigator, submit, auto-submit at zero) and `/exams/[id]/results`.

## Phase 6: dashboard and readiness

- `GET /api/analytics/readiness`: weighted estimate from accuracy, retention, weak-topic coverage,
  revision consistency and mock exam average, with the components and a recommendation. The UI labels it
  "Estimated".
- Dashboard: readiness, weak/strong topics, today's plan, upcoming reviews (`GET /api/reviews/upcoming`),
  recent mistakes, difficulty progress, mock exam performance, streak, retention and accuracy.

## Phase 7: integration testing

Backend `./mvnw verify`, frontend lint/test/build, Flyway on a fresh database, docker compose build,
health, and a browser walk through flashcards, quiz, weak topics, adaptive difficulty, study plan, mock
exam, mistake flow, SM-2 integration and the dashboard against Groq.

## Testing per phase

Every phase adds unit tests for its pure logic and MockMvc integration tests against Testcontainers
with the scripted `FakeClaudeClient`; each phase ends with the full backend and frontend suites green
and a commit.
