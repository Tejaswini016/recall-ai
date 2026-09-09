# RecallAI

An AI-powered study companion. Paste notes or upload a PDF, let Claude turn them into flashcards and quizzes, then review with the SM-2 spaced-repetition algorithm so you only study what is actually due.

> **Status:** Phases 1–6 complete (foundation, authentication, decks and cards, SM-2 scheduler, review system, Claude integration). Flashcard upload, quizzes, analytics and the frontend land in subsequent phases. This README grows with the project.

## Why this is more than an AI wrapper

Claude does exactly two things here: generate flashcards and generate quizzes from user-supplied material. Everything else is deterministic software:

| Concern | Handled by |
|---|---|
| Flashcard / quiz generation, explanations | Claude, behind strict JSON validation, retries and a content-hash cache |
| Review scheduling (SM-2), streaks, scoring, weak-topic detection | Plain Java services with unit tests |
| Authentication, authorization, rate limiting, search | Spring Security, JPA, PostgreSQL |

The model never sees credentials, never decides what is due, and its output is never trusted without validation.

## Technology stack

- **Backend:** Java 21, Spring Boot 3.5, Spring Web, Spring Data JPA, Spring Security, Bean Validation, Flyway, PostgreSQL 16, springdoc-openapi, JUnit 5, Mockito, Testcontainers
- **Frontend:** Next.js 16 (App Router), TypeScript, Tailwind CSS 4
- **Infrastructure:** Docker, docker-compose, environment-variable configuration

## Repository layout

```
recall-ai/
├── backend/                 Spring Boot API
│   ├── src/main/java/com/recallai/
│   │   ├── ai/              Claude client, prompts, validation, retry, cache
│   │   ├── config/          Security, CORS, OpenAPI, properties
│   │   ├── controller/      REST controllers (thin; no business logic)
│   │   ├── dto/             Request/response records
│   │   ├── entity/          JPA entities
│   │   ├── exception/       Centralized error handling
│   │   ├── repository/      Spring Data repositories
│   │   ├── scheduler/       SM-2 algorithm
│   │   ├── security/        JWT
│   │   └── service/         Business logic
│   ├── src/main/resources/db/migration/   Flyway migrations
│   └── src/test/            Unit and integration tests
├── frontend/                Next.js application
│   ├── app/                 Routes
│   ├── components/          Reusable UI
│   ├── hooks/               React hooks
│   ├── lib/                 API client, utilities
│   └── types/               Shared TypeScript types
├── docker-compose.yml
└── .env.example
```

## Database schema

Flyway owns the schema (`backend/src/main/resources/db/migration`). Hibernate runs in `validate` mode and never alters tables.

| Table | Purpose |
|---|---|
| `users` | Accounts; bcrypt password hash |
| `decks` | User-owned collections of cards with subject and tags |
| `cards` | Question/answer/explanation plus SM-2 state: `ease_factor`, `interval`, `repetitions`, `due_date` |
| `review_history` | One row per review with before/after scheduling values, used for analytics, streaks and weak topics |
| `quizzes`, `quiz_questions` | AI-generated multiple-choice quizzes |
| `quiz_attempts` | Scores per attempt |
| `ai_cache` | Validated Claude responses keyed by `(content_hash, operation_type, model, prompt_version)` |

Check constraints enforce SM-2 invariants at the database level (ease factor ≥ 1.30, non-negative interval and repetitions, quality score 0–5, correct answer index 0–3).

## SM-2 spaced repetition

Scheduling is deterministic Java in `backend/src/main/java/com/recallai/scheduler` (`Sm2Service`, `Sm2State`, `ReviewResult`). It has no dependency on Spring Web, JPA or Claude, and is exercised by 30+ unit tests before any review endpoint exists.

**What the algorithm tracks per card**

| Field | Meaning | Start |
|---|---|---|
| `easeFactor` | How easy the card is; multiplies the interval on each success | 2.50 |
| `interval` | Days between the last successful review and the next one | 0 |
| `repetitions` | Consecutive successful reviews since the last failure | 0 |
| `dueDate` | Day the card re-enters the review queue | creation day |

**What happens on a review** (quality `q` from 0 = blank to 5 = perfect):

1. `q < 3` is a lapse. Repetitions reset to 0 and the card is scheduled for relearning tomorrow (interval 1). The ease factor is left unchanged, as in Wozniak's original description.
2. `q ≥ 3` is a success. The next interval is 1 day for the first repetition, 6 days for the second, and `round(previousInterval × easeFactor)` afterwards, always at least one day longer than before. The multiplication uses the ease factor *before* this review's adjustment.
3. On success the ease factor moves by `0.1 − (5 − q) × (0.08 + (5 − q) × 0.02)` and is floored at 1.30. So quality 5 adds 0.10, quality 4 leaves it unchanged, quality 3 subtracts 0.14. There is no upper bound.
4. `dueDate = today + newInterval`.

With steady quality-4 recalls a card follows the classic ladder 1 → 6 → 15 → 38 → 95 → 238 days. Repeated quality-3 recalls push the ease down to the 1.30 floor, so hard cards come back more often; quality-5 recalls raise it, so easy cards come back less often.

**Why not let the AI schedule?** Spacing is a well-studied, deterministic problem with a known-good algorithm. A model would be slower, non-reproducible, cost money per review and be impossible to unit test. Claude is used only where language understanding is the job: turning study material into cards and quizzes.

## Review system

`ReviewService` is the only code that changes a card's schedule, and it never does arithmetic itself:

```
ReviewController → ReviewService → Sm2Service (pure) → ReviewHistoryRepository
```

- **Queue** (`GET /api/reviews/due`): returns only cards with `due_date <= today`, ordered most-overdue first, then lowest ease factor (weakest) first. Future cards are never included. The response carries `totalDue` even when a `limit` truncates the list, and each card reports `daysOverdue`.
- **Grading** (`POST /api/reviews/{cardId}` with `{"quality": 0..5}`): in one transaction the card row is locked (`SELECT … FOR UPDATE`) so concurrent gradings cannot race, SM-2 computes the next schedule, the card is updated, and a `review_history` row records the before/after interval and ease. The response includes the new schedule, whether the card is now mastered, and how many cards remain due today.
- **Mastered**: a card is mastered when its interval reaches `MASTERED_INTERVAL_DAYS` (default 21, Anki's "mature" threshold). Deck statistics count mastered cards with the same rule.
- **Streaks** (`GET /api/reviews/streak`): computed from the distinct UTC days on which the user reviewed anything. `StreakCalculator` walks actual calendar dates, so a gap breaks the run and a streak that ended yesterday still counts as current (the user can extend it today). Longest streak is tracked independently of the current one.
- **History** (`GET /api/reviews/history`): paginated, newest first, with the card question and deck name for the activity feed.

Days are currently computed in UTC; per-user time zones are a listed future improvement.

## AI architecture

All model access lives in `backend/src/main/java/com/recallai/ai` and is reached through one facade, `AiGenerationService`. Nothing else in the application talks to Claude, builds a prompt, or parses model output.

```
AiGenerationService
  ├─ AiCacheService        content-hash lookup / store (PostgreSQL ai_cache)
  ├─ PromptService         FlashcardPromptBuilder, QuizPromptBuilder, corrective prompts
  ├─ AiRetryService        call → validate → corrective re-ask → transport retry → controlled error
  │    └─ ClaudeClient     interface; AnthropicClaudeClient (official Java SDK) in production,
  │                        FakeClaudeClient (scripted) in tests
  └─ AiResponseValidator   strict parsing and content rules → GeneratedFlashcard / GeneratedQuizQuestion
```

**Why Claude, and only here.** Turning free-form notes into good questions and plausible distractors is a language task. Everything else in RecallAI is deterministic code, because a model is slower, non-reproducible, costs money per call and cannot be unit-tested the way `Sm2Service` can.

### Structured prompt engineering

Prompts are versioned templates, not strings scattered through services. `FlashcardPromptBuilder.VERSION` and `QuizPromptBuilder.VERSION` (currently `v1`) are part of every cache key, so changing a prompt automatically stops serving responses generated by the old one.

Each prompt has three parts:

1. **System prompt**: the role and the rules. Use only the supplied material and never invent facts; return fewer items rather than pad; one fact per card; concise questions, precise answers, an explanation drawn from the material, a short topic label and lowercase tags; no duplicates; no cards about the document itself; respond with JSON only.
2. **User message**: the requested count and the material wrapped in `<study_material>` tags so instructions and content cannot be confused.
3. **Output schema**: a JSON Schema sent as the API's structured-output constraint (`output_config.format`). For quizzes it pins exactly four options and `correctAnswer` in 0–3.

The response shapes are:

```json
{ "cards": [ { "question": "...", "answer": "...", "explanation": "...", "topic": "...", "tags": ["..."] } ] }
{ "questions": [ { "question": "...", "options": ["...", "...", "...", "..."], "correctAnswer": 0, "explanation": "..." } ] }
```

### Guaranteeing structured output

Two independent layers. The API is asked for schema-constrained JSON, and the backend still validates everything as if it were not. `AiResponseValidator` checks that the text parses as a JSON object (a ```json fence is tolerated, prose around JSON is not), that the array exists and is non-empty and within the requested count, and per item that every required field is present, of the right type, non-blank and within length limits; quiz options must be four distinct non-empty strings and the answer index in range. Tags are normalized exactly like user-entered tags. Duplicate questions are dropped. Any problem is reported with its path (`cards[2].answer is missing`), and only validated `GeneratedFlashcard` / `GeneratedQuizQuestion` records leave the package.

### AI failure handling

`AiRetryService` implements the strategy:

1. Call the model.
2. Validate.
3. If invalid, send a corrective follow-up: the conversation is extended with the rejected reply as an assistant turn and a user turn that lists each problem and restates the format requirement. This runs `AI_VALIDATION_RETRIES` times (default 1).
4. If the call itself fails transiently (rate limit, 5xx, network), wait and retry `AI_TRANSPORT_RETRIES` times on top of the SDK's own retries. Non-retryable failures (bad key, refusal) are not retried.
5. Otherwise return a controlled error: `AI_INVALID_RESPONSE` (502) or `AI_ERROR` (502) in the standard error shape. Truncated output (`stop_reason = max_tokens`) is treated as invalid.

Invalid output is never persisted and never cached. Tests simulate malformed JSON, prose, missing fields, wrong types, empty arrays, bad option counts, out-of-range answers and truncation, plus the retry paths, using `FakeClaudeClient`.

### Content-hash caching

Before calling the model, `AiCacheService` computes `SHA-256(normalize(material) + params)` where normalization applies Unicode NFC, lower-casing and whitespace collapsing, and `params` carries the requested count. The cache key is `(hash, operation, model, prompt version)`; a hit returns the validated items without a call, a miss stores them only after validation. Rows are content-addressed rather than user-addressed, so two students pasting the same chapter share one model call, and no user-specific data is ever cached. Concurrent inserts of the same key are tolerated.

### Cost controls

- Cache first; identical material never pays twice.
- Input is capped at `AI_MAX_INPUT_CHARS` per request (larger documents are chunked by the caller), output at `CLAUDE_MAX_TOKENS`, item counts at `AI_MAX_CARDS` / `AI_MAX_QUIZ_QUESTIONS`.
- `CLAUDE_EFFORT` (default `medium`) tunes reasoning depth for what is a well-bounded extraction task.
- Every call logs latency, input and output tokens, cache hit or miss and retry count. Study content and keys are never logged.
- Per-user rate limiting of the generation endpoints (`AI_RATE_LIMIT`) is applied in the hardening phase.

### Handling hallucination

The prompt restricts the model to the supplied material and tells it to produce fewer items rather than invent, the material is delimited so it cannot be read as instructions, and every generated card is shown to the user before study. The application does not claim to detect factual errors; it minimizes their likelihood and keeps the human in the loop.

## Security and authentication

- **Registration and login** issue a signed JWT (HS256) containing only the user id and email. Tokens expire after `JWT_EXPIRATION_MINUTES`.
- **Passwords** are hashed with BCrypt and never logged or returned. Login for an unknown email still runs a BCrypt comparison against a dummy hash so response timing does not reveal whether an account exists.
- **Every request** passes through `JwtAuthenticationFilter`, which verifies the signature and expiry, then reloads the user from PostgreSQL. A token for a deleted account is rejected.
- **Stateless**: no sessions, no cookies, CSRF disabled because the API only accepts bearer tokens from a separate origin. CORS is restricted to `CORS_ALLOWED_ORIGINS`.
- **User isolation**: services always scope queries by the authenticated user id taken from the security context, never from the request body.
- **Fail-fast configuration**: the application refuses to start if `JWT_SECRET` is missing or shorter than 32 characters.

### Error format

Every error, including those raised inside the security filter chain, uses one shape:

```json
{
  "timestamp": "2026-09-09T16:20:00Z",
  "status": 400,
  "error": "VALIDATION_ERROR",
  "message": "Request validation failed",
  "path": "/api/auth/register",
  "fieldErrors": { "email": "must be a well-formed email address" }
}
```

`fieldErrors` appears only for validation failures. Unexpected exceptions are logged server-side and reported as `INTERNAL_ERROR` with a generic message.

## API

Interactive documentation is served at `/swagger-ui.html` (OpenAPI JSON at `/v3/api-docs`). Click **Authorize** and paste a token to call protected endpoints.

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/api/auth/register` | – | Create account, returns token + user |
| POST | `/api/auth/login` | – | Returns token + user |
| GET | `/api/auth/me` | Bearer | Current user |
| POST | `/api/decks` | Bearer | Create deck |
| GET | `/api/decks` | Bearer | List/search decks (`q`, `subject`, `tag`, `page`, `size`) with card, due and mastered counts |
| GET | `/api/decks/{id}` | Bearer | Deck with statistics and progress |
| PUT | `/api/decks/{id}` | Bearer | Update deck |
| DELETE | `/api/decks/{id}` | Bearer | Delete deck and its cards |
| POST | `/api/decks/{id}/cards` | Bearer | Add card |
| GET | `/api/decks/{id}/cards` | Bearer | List/search cards in a deck (`q`, `topic`, `tag`, paging) |
| GET | `/api/cards` | Bearer | Search cards across all decks |
| GET | `/api/cards/{id}` | Bearer | Get card |
| PUT | `/api/cards/{id}` | Bearer | Update card content (scheduling fields are read-only here) |
| DELETE | `/api/cards/{id}` | Bearer | Delete card |
| GET | `/api/reviews/due` | Bearer | Due queue (`deckId`, `limit`), overdue and weakest first |
| POST | `/api/reviews/{cardId}` | Bearer | Grade a card 0–5; returns the new SM-2 schedule and cards remaining |
| GET | `/api/reviews/streak` | Bearer | Current streak, longest streak, last active day, reviews today |
| GET | `/api/reviews/history` | Bearer | Paginated review history |
| GET | `/api/search?q=` | Bearer | Top decks and cards matching a query |
| GET | `/api/tags` | Bearer | All tags the user has used |

List endpoints return `{ content, page, size, totalElements, totalPages }`; `size` is capped at 100.

### Data isolation

Every deck and card lookup goes through a repository method that includes the authenticated user's id (`findByIdAndUserId`, `findByIdAndDeckUserId`). A resource that belongs to someone else is reported as `404 NOT_FOUND`, never `403`, so the API does not confirm that the id exists. Integration tests exercise this for reads, updates, deletes, card creation and search.

### Search

Search runs entirely in PostgreSQL:

- Generated `tsvector` columns on `decks` (name, description, subject) and `cards` (question, answer, topic) with GIN indexes provide stemmed full-text search via `websearch_to_tsquery`.
- Short fields (deck name and subject, card topic) also match by substring, and tags match by prefix, so partially typed words still find results.
- Tags are stored as `text[]` in one canonical form (trimmed, lower-cased, de-duplicated) with GIN indexes; exact tag filters use `= ANY(tags)`.
- Deck statistics (card count, cards due today, mastered cards) are one aggregate query per page of decks, not N+1 lookups.

## Local setup (without Docker)

Prerequisites: JDK 21+, Node 20+, a PostgreSQL 16 instance.

```bash
# 1. Database
createdb recallai   # or: POSTGRES_PORT=5433 docker compose up -d postgres  (then set DATABASE_URL to port 5433)

# 2. Backend
cd backend
cp .env.example .env            # fill in values; export them or use your IDE's env support
./mvnw spring-boot:run          # http://localhost:8080, Swagger UI at /swagger-ui.html

# 3. Frontend
cd ../frontend
cp .env.example .env.local
npm install
npm run dev                     # http://localhost:3000
```

The Maven wrapper downloads Maven on first use; no global Maven install is needed.

## Docker setup

```bash
cp .env.example .env            # set POSTGRES_PASSWORD, JWT_SECRET, CLAUDE_API_KEY
docker compose up --build
```

Services: PostgreSQL on 5432 (override with `POSTGRES_PORT` if a local PostgreSQL already uses it), backend on 8080, frontend on 3000. The backend waits for the database health check and the frontend waits for the backend readiness probe.

## Environment variables

Backend (`backend/.env.example`):

| Variable | Purpose |
|---|---|
| `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD` | JDBC connection |
| `JWT_SECRET`, `JWT_EXPIRATION_MINUTES` | Token signing key (base64, ≥256 bit) and lifetime |
| `CLAUDE_API_KEY`, `CLAUDE_MODEL` | Anthropic credentials and model id (default `claude-opus-5`) |
| `CLAUDE_EFFORT`, `CLAUDE_MAX_TOKENS` | Reasoning effort (`low`/`medium`/`high`) and output token ceiling |
| `AI_MAX_INPUT_CHARS`, `AI_MAX_CARDS`, `AI_MAX_QUIZ_QUESTIONS` | Size limits per generation request |
| `AI_VALIDATION_RETRIES`, `AI_TRANSPORT_RETRIES` | Corrective re-asks after invalid output; extra attempts after transient failures |
| `CORS_ALLOWED_ORIGINS` | Comma-separated frontend origins |
| `AI_RATE_LIMIT` | AI generation requests per user per hour |
| `MASTERED_INTERVAL_DAYS` | SM-2 interval at which a card counts as mastered (default 21) |
| `MAX_UPLOAD_SIZE` | Upload limit for study material |

Frontend (`frontend/.env.example`): `NEXT_PUBLIC_API_URL` – base URL of the API as seen from the browser.

Secrets are never committed; `.gitignore` excludes every `.env*` file except the examples.

## Testing

```bash
cd backend && ./mvnw verify     # unit + integration tests (integration tests use Testcontainers, requires Docker)
cd frontend && npm run lint && npm run build
```

## Roadmap

1. ✅ Foundation: repo, Spring Boot, Next.js, PostgreSQL, Flyway, Docker
2. ✅ Authentication (JWT)
3. ✅ Decks and cards: CRUD, tags, PostgreSQL full-text search, per-user isolation
4. ✅ SM-2 scheduler with comprehensive tests
5. ✅ Review queue, grading, history, streaks, mastered cards
6. ✅ Claude integration: versioned prompts, structured output, strict validation, corrective retries, content-hash cache, fake client for tests
7. AI flashcards from pasted text and PDF/TXT uploads
8. Quizzes
9. Weak topics and analytics
10. Frontend
11. Production hardening
12. AWS deployment
