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
  createdAt: string;
  updatedAt: string;
}

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
