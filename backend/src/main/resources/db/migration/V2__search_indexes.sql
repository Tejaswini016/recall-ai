-- Full-text search support. Generated tsvector columns are maintained by PostgreSQL
-- itself, so the application never has to keep a search index in sync.

ALTER TABLE decks ADD COLUMN search_vector TSVECTOR
    GENERATED ALWAYS AS (
        to_tsvector('english',
            coalesce(name, '') || ' ' || coalesce(description, '') || ' ' || coalesce(subject, ''))
    ) STORED;
CREATE INDEX idx_decks_search ON decks USING GIN (search_vector);

ALTER TABLE cards ADD COLUMN search_vector TSVECTOR
    GENERATED ALWAYS AS (
        to_tsvector('english',
            coalesce(question, '') || ' ' || coalesce(answer, '') || ' ' || coalesce(topic, ''))
    ) STORED;
CREATE INDEX idx_cards_search ON cards USING GIN (search_vector);
