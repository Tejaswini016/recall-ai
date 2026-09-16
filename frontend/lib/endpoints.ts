import { apiFetch, query } from "@/lib/api";
import type {
  ActivityPoint,
  AiStatus,
  CreateMockExamRequest,
  CreateStudyPlanRequest,
  AnalyticsSummary,
  AnswerSubmission,
  AuthResponse,
  Card,
  CardRequest,
  Deck,
  DeckRequest,
  DifficultyDistribution,
  GenerateCardsResponse,
  MasteryPoint,
  MockExam,
  MockExamAnswer,
  MockExamResult,
  MockExamStats,
  MockExamSummary,
  Mistake,
  MistakeFlashcard,
  MistakeSource,
  MistakeStatus,
  MistakeSummary,
  PageResponse,
  Quiz,
  QuizAttempt,
  QuizAttemptSummary,
  QuizSummary,
  ReviewHistoryItem,
  ReviewQueue,
  ReviewResult,
  SearchResponse,
  Streak,
  StudyPlan,
  StudyPlanStatus,
  StudyPlanSummary,
  StudyPlanTask,
  StudyTaskStatus,
  TodayPlan,
  TopicInsight,
  TopicPerformance,
  User,
} from "@/types";

/** One typed function per backend endpoint, so pages never build URLs themselves. */
export const api = {
  auth: {
    register: (body: { name: string; email: string; password: string }) =>
      apiFetch<AuthResponse>("/api/auth/register", { method: "POST", body }),
    login: (body: { email: string; password: string }) =>
      apiFetch<AuthResponse>("/api/auth/login", { method: "POST", body }),
    me: () => apiFetch<User>("/api/auth/me"),
  },
  decks: {
    list: (params: { q?: string; subject?: string; tag?: string; page?: number; size?: number } = {}) =>
      apiFetch<PageResponse<Deck>>(`/api/decks${query(params)}`),
    get: (id: number) => apiFetch<Deck>(`/api/decks/${id}`),
    create: (body: DeckRequest) => apiFetch<Deck>("/api/decks", { method: "POST", body }),
    update: (id: number, body: DeckRequest) => apiFetch<Deck>(`/api/decks/${id}`, { method: "PUT", body }),
    remove: (id: number) => apiFetch<void>(`/api/decks/${id}`, { method: "DELETE" }),
    cards: (id: number, params: { q?: string; topic?: string; tag?: string; page?: number; size?: number } = {}) =>
      apiFetch<PageResponse<Card>>(`/api/decks/${id}/cards${query(params)}`),
    addCard: (id: number, body: CardRequest) =>
      apiFetch<Card>(`/api/decks/${id}/cards`, { method: "POST", body }),
  },
  cards: {
    get: (id: number) => apiFetch<Card>(`/api/cards/${id}`),
    search: (params: { q?: string; topic?: string; tag?: string; page?: number; size?: number } = {}) =>
      apiFetch<PageResponse<Card>>(`/api/cards${query(params)}`),
    update: (id: number, body: CardRequest) => apiFetch<Card>(`/api/cards/${id}`, { method: "PUT", body }),
    remove: (id: number) => apiFetch<void>(`/api/cards/${id}`, { method: "DELETE" }),
  },
  reviews: {
    due: (params: { deckId?: number; limit?: number } = {}) =>
      apiFetch<ReviewQueue>(`/api/reviews/due${query(params)}`),
    practice: (params: { topic: string; limit?: number }) =>
      apiFetch<ReviewQueue>(`/api/reviews/practice${query(params)}`),
    grade: (cardId: number, quality: number, responseMs?: number) =>
      apiFetch<ReviewResult>(`/api/reviews/${cardId}`, { method: "POST", body: { quality, responseMs } }),
    streak: () => apiFetch<Streak>("/api/reviews/streak"),
    history: (params: { page?: number; size?: number } = {}) =>
      apiFetch<PageResponse<ReviewHistoryItem>>(`/api/reviews/history${query(params)}`),
  },
  ai: {
    status: () => apiFetch<AiStatus>("/api/ai/status"),
    flashcards: (body: { deckId: number; text: string; count?: number }) =>
      apiFetch<GenerateCardsResponse>("/api/ai/flashcards", { method: "POST", body }),
    flashcardsUpload: (deckId: number, file: File, count?: number) => {
      const formData = new FormData();
      formData.append("file", file);
      const params = query({ deckId, count });
      return apiFetch<GenerateCardsResponse>(`/api/ai/flashcards/upload${params}`, { method: "POST", formData });
    },
    quiz: (body: { deckId: number; title?: string; text?: string; count?: number }) =>
      apiFetch<Quiz>("/api/ai/quiz", { method: "POST", body }),
    topicQuiz: (body: { topic: string; count?: number }) =>
      apiFetch<Quiz>("/api/ai/quiz/topic", { method: "POST", body }),
  },
  quizzes: {
    list: (params: { deckId?: number; page?: number; size?: number } = {}) =>
      apiFetch<PageResponse<QuizSummary>>(`/api/quizzes${query(params)}`),
    get: (id: number) => apiFetch<Quiz>(`/api/quizzes/${id}`),
    remove: (id: number) => apiFetch<void>(`/api/quizzes/${id}`, { method: "DELETE" }),
    submit: (id: number, answers: AnswerSubmission[], durationSeconds?: number) =>
      apiFetch<QuizAttempt>(`/api/quizzes/${id}/attempts`, { method: "POST", body: { answers, durationSeconds } }),
    attempts: (id: number) => apiFetch<QuizAttemptSummary[]>(`/api/quizzes/${id}/attempts`),
    attempt: (id: number, attemptId: number) => apiFetch<QuizAttempt>(`/api/quizzes/${id}/attempts/${attemptId}`),
  },
  mistakes: {
    list: (params: { status?: MistakeStatus; source?: MistakeSource; topic?: string; page?: number; size?: number } = {}) =>
      apiFetch<PageResponse<Mistake>>(`/api/mistakes${query(params)}`),
    summary: () => apiFetch<MistakeSummary>("/api/mistakes/summary"),
    recent: () => apiFetch<Mistake[]>("/api/mistakes/recent"),
    get: (id: number) => apiFetch<Mistake>(`/api/mistakes/${id}`),
    toFlashcard: (id: number, deckId?: number) =>
      apiFetch<MistakeFlashcard>(`/api/mistakes/${id}/flashcard`, { method: "POST", body: { deckId } }),
    dismiss: (id: number) => apiFetch<Mistake>(`/api/mistakes/${id}/dismiss`, { method: "POST" }),
    reopen: (id: number) => apiFetch<Mistake>(`/api/mistakes/${id}/reopen`, { method: "POST" }),
    remove: (id: number) => apiFetch<void>(`/api/mistakes/${id}`, { method: "DELETE" }),
  },
  analytics: {
    summary: () => apiFetch<AnalyticsSummary>("/api/analytics/summary"),
    activity: (days: number) => apiFetch<ActivityPoint[]>(`/api/analytics/activity${query({ days })}`),
    mastery: (days: number) => apiFetch<MasteryPoint[]>(`/api/analytics/mastery${query({ days })}`),
    topics: (deckId?: number) => apiFetch<TopicPerformance[]>(`/api/analytics/topics${query({ deckId })}`),
    weakTopics: (deckId?: number) => apiFetch<TopicPerformance[]>(`/api/analytics/weak-topics${query({ deckId })}`),
    difficulty: () => apiFetch<DifficultyDistribution>("/api/analytics/difficulty"),
    topicInsights: (params: { deckId?: number; weakOnly?: boolean } = {}) =>
      apiFetch<TopicInsight[]>(`/api/analytics/topic-insights${query(params)}`),
  },
  studyPlans: {
    create: (body: CreateStudyPlanRequest) => apiFetch<StudyPlan>("/api/study-plans", { method: "POST", body }),
    list: () => apiFetch<StudyPlanSummary[]>("/api/study-plans"),
    today: () => apiFetch<TodayPlan>("/api/study-plans/today"),
    get: (id: number) => apiFetch<StudyPlan>(`/api/study-plans/${id}`),
    regenerate: (id: number) => apiFetch<StudyPlan>(`/api/study-plans/${id}/regenerate`, { method: "POST" }),
    updateTask: (planId: number, taskId: number, status: StudyTaskStatus) =>
      apiFetch<StudyPlanTask>(`/api/study-plans/${planId}/tasks/${taskId}`, { method: "PATCH", body: { status } }),
    updateStatus: (id: number, status: StudyPlanStatus) =>
      apiFetch<StudyPlanSummary>(`/api/study-plans/${id}`, { method: "PATCH", body: { status } }),
    remove: (id: number) => apiFetch<void>(`/api/study-plans/${id}`, { method: "DELETE" }),
  },
  mockExams: {
    create: (body: CreateMockExamRequest) => apiFetch<MockExam>("/api/mock-exams", { method: "POST", body }),
    list: (limit = 20) => apiFetch<MockExamSummary[]>(`/api/mock-exams${query({ limit })}`),
    stats: () => apiFetch<MockExamStats>("/api/mock-exams/stats"),
    get: (id: number) => apiFetch<MockExam>(`/api/mock-exams/${id}`),
    submit: (id: number, answers: MockExamAnswer[]) =>
      apiFetch<MockExamResult>(`/api/mock-exams/${id}/submit`, { method: "POST", body: { answers } }),
    results: (id: number) => apiFetch<MockExamResult>(`/api/mock-exams/${id}/results`),
    remove: (id: number) => apiFetch<void>(`/api/mock-exams/${id}`, { method: "DELETE" }),
  },
  search: (q: string) => apiFetch<SearchResponse>(`/api/search${query({ q })}`),
  tags: () => apiFetch<string[]>("/api/tags"),
};
