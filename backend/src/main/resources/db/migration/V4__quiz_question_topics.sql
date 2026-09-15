-- Phase 1 of the adaptive upgrade: quiz questions carry the sub-topic they test so quiz answers
-- can feed weak-topic detection alongside flashcard reviews.
ALTER TABLE quiz_questions ADD COLUMN topic VARCHAR(150);

CREATE INDEX idx_quiz_attempt_answers_question ON quiz_attempt_answers (question_id);
