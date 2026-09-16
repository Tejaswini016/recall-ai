/** API types mirroring the backend DTOs. Field names are identical to the JSON. */

export interface ApiError {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
  fieldErrors?: Record<string, string>;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface User {
  id: number;
  name: string;
  email: string;
  createdAt: string;
}

export interface AuthResponse {
  token: string;
  expiresAt: string;
  user: User;
}

export interface Deck {
  id: number;
  name: string;
  description: string | null;
  subject: string | null;
  tags: string[];
  cardCount: number;
  dueCount: number;
  masteredCount: number;
  progressPercent: number;
  createdAt: string;
  updatedAt: string;
}

export interface DeckRequest {
  name: string;
  description?: string;
  subject?: string;
  tags: string[];
}

export type DifficultyTier = "EASY" | "MEDIUM" | "HARD" | "EXPERT";

export interface Card {
  id: number;
  deckId: number;
  question: string;
  answer: string;
  explanation: string | null;
  topic: string | null;
  tags: string[];
  easeFactor: number;
  interval: number;
  repetitions: number;
  dueDate: string;
  difficulty: DifficultyTier;
  successStreak: number;
  lapseCount: number;
  totalReviews: number;
  origin: CardOrigin;
  mistakeId: number | null;
  createdAt: string;
  updatedAt: string;
}

export type CardOrigin = "MANUAL" | "AI" | "MISTAKE";

export interface CardRequest {
  question: string;
  answer: string;
  explanation?: string;
  topic?: string;
  tags: string[];
}

export interface DueCard {
  id: number;
  deckId: number;
  deckName: string;
  question: string;
  answer: string;
  explanation: string | null;
  topic: string | null;
  tags: string[];
  easeFactor: number;
  interval: number;
  repetitions: number;
  dueDate: string;
  daysOverdue: number;
  difficulty: DifficultyTier;
  successStreak: number;
  lapseCount: number;
}

export interface ReviewQueue {
  today: string;
  totalDue: number;
  cards: DueCard[];
}

export interface ReviewResult {
  cardId: number;
  quality: number;
  successful: boolean;
  previousEaseFactor: number;
  newEaseFactor: number;
  previousInterval: number;
  newInterval: number;
  repetitions: number;
  nextDueDate: string;
  mastered: boolean;
  remainingDue: number;
  previousDifficulty: DifficultyTier;
  difficulty: DifficultyTier;
  difficultyChanged: boolean;
  successStreak: number;
  sm2Interval: number;
}

export interface Streak {
  currentStreak: number;
  longestStreak: number;
  lastActiveDate: string | null;
  reviewedToday: number;
}

export interface ReviewHistoryItem {
  id: number;
  cardId: number;
  deckId: number;
  deckName: string;
  question: string;
  topic: string | null;
  quality: number;
  successful: boolean;
  previousInterval: number;
  newInterval: number;
  previousEaseFactor: number;
  newEaseFactor: number;
  reviewedAt: string;
  responseMs: number | null;
  difficultyAfter: DifficultyTier | null;
}

export interface GenerateCardsResponse {
  deckId: number;
  cardsCreated: number;
  chunks: number;
  cachedChunks: number;
  retries: number;
  cards: Card[];
}

export interface QuizQuestion {
  id: number;
  question: string;
  options: string[];
  topic: string | null;
}

export interface QuizSummary {
  id: number;
  deckId: number;
  deckName: string;
  title: string;
  questionCount: number;
  attemptCount: number;
  bestScorePercent: number | null;
  createdAt: string;
}

export interface Quiz extends QuizSummary {
  questions: QuizQuestion[];
}

export interface AnswerSubmission {
  questionId: number;
  selectedAnswer: number | null;
}

export interface QuestionResult {
  questionId: number;
  question: string;
  options: string[];
  selectedAnswer: number | null;
  correctAnswer: number;
  correct: boolean;
  explanation: string;
  topic: string | null;
  mistakeId: number | null;
  mistakeStatus: MistakeStatus | null;
  mistakeCardId: number | null;
}

export type MistakeSource = "QUIZ" | "MOCK_EXAM";
export type MistakeStatus = "OPEN" | "CONVERTED" | "DISMISSED";

export interface Mistake {
  id: number;
  source: MistakeSource;
  status: MistakeStatus;
  question: string;
  givenAnswer: string | null;
  correctAnswer: string;
  explanation: string | null;
  topic: string | null;
  deckId: number | null;
  deckName: string | null;
  quizQuestionId: number | null;
  occurrences: number;
  cardId: number | null;
  firstMissedAt: string;
  lastMissedAt: string;
  resolvedAt: string | null;
}

export interface MistakeSummary {
  open: number;
  converted: number;
  dismissed: number;
  total: number;
  openByTopic: { topic: string; count: number }[];
}

export interface MistakeFlashcard {
  mistake: Mistake;
  card: Card;
  aiGenerated: boolean;
}

export interface QuizAttempt {
  id: number;
  quizId: number;
  quizTitle: string;
  score: number;
  totalQuestions: number;
  percent: number;
  correctCount: number;
  incorrectCount: number;
  completedAt: string;
  durationSeconds: number | null;
  results: QuestionResult[];
}

export interface QuizAttemptSummary {
  id: number;
  score: number;
  totalQuestions: number;
  percent: number;
  completedAt: string;
  durationSeconds: number | null;
}

export interface AnalyticsSummary {
  dueToday: number;
  reviewedToday: number;
  totalCards: number;
  cardsMastered: number;
  masteredPercent: number;
  totalDecks: number;
  totalReviews: number;
  averageRecall: number | null;
  retentionRate: number | null;
  currentStreak: number;
  longestStreak: number;
  lastActiveDate: string | null;
  quizzesTaken: number;
  averageQuizPercent: number | null;
}

export interface ActivityPoint {
  date: string;
  reviews: number;
  successful: number;
  averageQuality: number | null;
  retentionPercent: number | null;
}

export interface MasteryPoint {
  date: string;
  masteredCards: number;
}

export interface TopicPerformance {
  topic: string;
  cardCount: number;
  reviews: number;
  averageQuality: number;
  recentAverageQuality: number;
  successRatePercent: number;
  lastReviewedAt: string;
  weak: boolean;
}

export interface DifficultyDistribution {
  totalCards: number;
  tiers: { tier: DifficultyTier; cards: number; percent: number }[];
  averageResponseMs: number | null;
  cardsWithLapses: number;
}

export type TopicCategory = "CRITICAL" | "WEAK" | "GOOD" | "STRONG" | "UNRATED";

/** One topic across flashcard reviews and quiz answers, as computed by /api/analytics/topic-insights. */
export interface TopicInsight {
  topic: string;
  category: TopicCategory;
  accuracyPercent: number;
  attempts: number;
  mistakes: number;
  cardCount: number;
  cardReviews: number;
  cardSuccesses: number;
  quizAnswers: number;
  quizCorrect: number;
  lastStudiedAt: string | null;
  recommendedAction: string;
}

export type KnowledgeLevel = "BEGINNER" | "INTERMEDIATE" | "ADVANCED";
export type StudyPlanStatus = "ACTIVE" | "COMPLETED" | "ARCHIVED";
export type StudyTaskType = "REVIEW_DUE" | "LEARN_TOPIC" | "PRACTICE_QUIZ" | "REVIEW_MISTAKES" | "MOCK_EXAM" | "FINAL_REVISION";
export type StudyTaskStatus = "PENDING" | "DONE" | "SKIPPED";

export interface StudyPlanTask {
  id: number;
  planId: number;
  examName: string;
  date: string;
  order: number;
  type: StudyTaskType;
  topic: string | null;
  title: string;
  description: string | null;
  minutes: number;
  status: StudyTaskStatus;
  completedAt: string | null;
}

export interface StudyPlanProgress {
  totalTasks: number;
  doneTasks: number;
  skippedTasks: number;
  pendingTasks: number;
  percentComplete: number;
  totalMinutes: number;
  doneMinutes: number;
  daysUntilExam: number;
  studyDaysLeft: number;
  studyDaysTotal: number;
  onTrack: boolean;
  dueToday: number;
}

export interface StudyPlanSummary {
  id: number;
  examName: string;
  examDate: string;
  topics: string[];
  knowledgeLevel: KnowledgeLevel;
  minutesPerDay: number;
  status: StudyPlanStatus;
  aiGenerated: boolean;
  createdAt: string;
  progress: StudyPlanProgress;
}

export interface StudyPlan extends StudyPlanSummary {
  preferredDays: number[];
  summary: string | null;
  topicAdvice: { topic: string; advice: string }[];
  generatedAt: string;
  tasks: StudyPlanTask[];
}

export interface CreateStudyPlanRequest {
  examName: string;
  examDate: string;
  topics: string[];
  knowledgeLevel: KnowledgeLevel;
  minutesPerDay: number;
  preferredDays: number[];
}

export interface TodayPlan {
  date: string;
  activePlans: number;
  tasks: StudyPlanTask[];
  carriedOver: StudyPlanTask[];
  totalMinutes: number;
  doneMinutes: number;
}

export type ExamQuestionType = "MCQ" | "TRUE_FALSE" | "SHORT_ANSWER";
export type ExamDifficulty = "EASY" | "MEDIUM" | "HARD" | "EXPERT";
export type MockExamStatus = "IN_PROGRESS" | "SUBMITTED";

export interface CreateMockExamRequest {
  topic?: string;
  deckId?: number;
  title?: string;
  difficulty: ExamDifficulty;
  questionCount: number;
  durationMinutes: number;
  questionTypes: ExamQuestionType[];
}

export interface MockExamQuestion {
  id: number;
  order: number;
  type: ExamQuestionType;
  question: string;
  options: string[];
  topic: string | null;
}

export interface MockExam {
  id: number;
  title: string;
  topic: string | null;
  deckId: number | null;
  difficulty: ExamDifficulty;
  durationMinutes: number;
  status: MockExamStatus;
  startedAt: string;
  expiresAt: string;
  remainingSeconds: number;
  questionCount: number;
  questions: MockExamQuestion[];
}

export interface MockExamAnswer {
  questionId: number;
  selectedOption?: number | null;
  answerText?: string | null;
}

export interface MockExamQuestionResult {
  id: number;
  order: number;
  type: ExamQuestionType;
  question: string;
  options: string[];
  topic: string | null;
  selectedOption: number | null;
  answerText: string | null;
  correctOption: number | null;
  correctAnswer: string;
  acceptableAnswers: string[];
  correct: boolean;
  skipped: boolean;
  explanation: string;
  mistakeId: number | null;
  mistakeStatus: MistakeStatus | null;
  mistakeCardId: number | null;
}

export interface MockExamResult {
  id: number;
  title: string;
  topic: string | null;
  difficulty: ExamDifficulty;
  score: number;
  totalQuestions: number;
  percent: number;
  correctCount: number;
  incorrectCount: number;
  skippedCount: number;
  durationMinutes: number;
  timeTakenSeconds: number | null;
  timedOut: boolean;
  startedAt: string;
  submittedAt: string;
  topics: { topic: string; correct: number; total: number; percent: number; strong: boolean }[];
  strongTopics: string[];
  weakTopics: string[];
  questions: MockExamQuestionResult[];
}

export interface MockExamSummary {
  id: number;
  title: string;
  topic: string | null;
  difficulty: ExamDifficulty;
  questionCount: number;
  durationMinutes: number;
  status: MockExamStatus;
  score: number;
  percent: number | null;
  timeTakenSeconds: number | null;
  timedOut: boolean;
  startedAt: string;
  submittedAt: string | null;
}

export interface MockExamStats {
  exams: number;
  submitted: number;
  averagePercent: number | null;
  bestPercent: number | null;
  latestPercent: number | null;
  recent: MockExamSummary[];
}

export interface ReadinessComponent {
  key: string;
  label: string;
  score: number;
  weight: number;
  detail: string;
  available: boolean;
}

export interface Readiness {
  score: number;
  label: string;
  estimate: boolean;
  confidence: "LOW" | "MEDIUM" | "HIGH";
  evidenceAttempts: number;
  components: ReadinessComponent[];
  recommendation: string;
  computedAt: string;
}

export interface UpcomingReviews {
  today: string;
  overdue: number;
  dueToday: number;
  days: { date: string; cards: number }[];
}

export interface AiStatus {
  available: boolean;
  demoMode: boolean;
  provider: "anthropic" | "gemini" | "groq" | "demo";
  model: string;
}

export interface SearchResponse {
  decks: Deck[];
  cards: Card[];
}
