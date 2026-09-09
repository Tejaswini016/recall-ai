package com.recallai.repository;

import com.recallai.entity.Card;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CardRepository extends JpaRepository<Card, Long> {

    /** Ownership travels through the deck: a card in another user's deck does not exist for this user. */
    Optional<Card> findByIdAndDeckUserId(Long id, Long userId);

    /**
     * Card search scoped to the owner, optionally to one deck, with full-text search over
     * question, answer and topic, prefix matching over tags, and exact tag/topic filters.
     */
    @Query(value = """
            SELECT c.* FROM cards c
            JOIN decks d ON d.id = c.deck_id
            WHERE d.user_id = :userId
              AND (CAST(:deckId AS BIGINT) IS NULL OR c.deck_id = CAST(:deckId AS BIGINT))
              AND (CAST(:q AS TEXT) IS NULL
                   OR c.search_vector @@ websearch_to_tsquery('english', CAST(:q AS TEXT))
                   OR c.topic ILIKE ('%' || CAST(:q AS TEXT) || '%')
                   OR EXISTS (SELECT 1 FROM unnest(c.tags) t WHERE t ILIKE (CAST(:q AS TEXT) || '%')))
              AND (CAST(:topic AS TEXT) IS NULL OR c.topic ILIKE CAST(:topic AS TEXT))
              AND (CAST(:tag AS TEXT) IS NULL OR CAST(:tag AS TEXT) = ANY(c.tags))
            ORDER BY c.created_at DESC, c.id DESC
            """,
            countQuery = """
            SELECT count(*) FROM cards c
            JOIN decks d ON d.id = c.deck_id
            WHERE d.user_id = :userId
              AND (CAST(:deckId AS BIGINT) IS NULL OR c.deck_id = CAST(:deckId AS BIGINT))
              AND (CAST(:q AS TEXT) IS NULL
                   OR c.search_vector @@ websearch_to_tsquery('english', CAST(:q AS TEXT))
                   OR c.topic ILIKE ('%' || CAST(:q AS TEXT) || '%')
                   OR EXISTS (SELECT 1 FROM unnest(c.tags) t WHERE t ILIKE (CAST(:q AS TEXT) || '%')))
              AND (CAST(:topic AS TEXT) IS NULL OR c.topic ILIKE CAST(:topic AS TEXT))
              AND (CAST(:tag AS TEXT) IS NULL OR CAST(:tag AS TEXT) = ANY(c.tags))
            """,
            nativeQuery = true)
    Page<Card> search(@Param("userId") Long userId,
                      @Param("deckId") Long deckId,
                      @Param("q") String q,
                      @Param("topic") String topic,
                      @Param("tag") String tag,
                      Pageable pageable);

    /** Every distinct tag the user has applied to a deck or a card, for tag pickers. */
    @Query(value = """
            SELECT DISTINCT t FROM (
                SELECT unnest(d.tags) AS t FROM decks d WHERE d.user_id = :userId
                UNION ALL
                SELECT unnest(c.tags) AS t FROM cards c JOIN decks d ON d.id = c.deck_id WHERE d.user_id = :userId
            ) tags
            ORDER BY t
            """, nativeQuery = true)
    List<String> findDistinctTagsByUserId(@Param("userId") Long userId);
}
