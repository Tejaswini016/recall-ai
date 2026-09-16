-- Phase 2 of the adaptive upgrade: a per-card difficulty tier and the counters that drive it.
-- SM-2 columns are untouched; the tier only nudges the interval SM-2 produces.
ALTER TABLE cards
    ADD COLUMN difficulty VARCHAR(10) NOT NULL DEFAULT 'MEDIUM',
    ADD COLUMN success_streak INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN lapse_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN total_reviews INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN avg_response_ms INTEGER,
    ADD CONSTRAINT ck_cards_difficulty CHECK (difficulty IN ('EASY', 'MEDIUM', 'HARD', 'EXPERT')),
    ADD CONSTRAINT ck_cards_adaptive_counters CHECK (
        success_streak >= 0 AND lapse_count >= 0 AND total_reviews >= 0
        AND (avg_response_ms IS NULL OR avg_response_ms >= 0));

-- Backfill counters from existing history so the tier logic starts from real numbers.
UPDATE cards c
SET total_reviews = h.reviews,
    lapse_count = h.lapses
FROM (
    SELECT card_id,
           count(*) AS reviews,
           count(*) FILTER (WHERE quality_score < 3) AS lapses
    FROM review_history
    GROUP BY card_id
) h
WHERE h.card_id = c.id;

ALTER TABLE review_history
    ADD COLUMN response_ms INTEGER,
    ADD COLUMN difficulty_after VARCHAR(10),
    ADD CONSTRAINT ck_review_history_response CHECK (response_ms IS NULL OR response_ms >= 0);

CREATE INDEX idx_cards_difficulty ON cards (deck_id, difficulty);
