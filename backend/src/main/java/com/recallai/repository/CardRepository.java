package com.recallai.repository;

import com.recallai.entity.Card;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CardRepository extends JpaRepository<Card, Long> {

    /** Ownership travels through the deck: a card in another user's deck does not exist for this user. */
    Optional<Card> findByIdAndDeckUserId(Long id, Long userId);

    /** All cards of a deck whose ownership the caller has already verified. */
    List<Card> findByDeckIdOrderByIdAsc(Long deckId);

    long countByDeckUserId(Long userId);

    long countByDeckUserIdAndIntervalDaysGreaterThanEqual(Long userId, int intervalDays);

    /**
     * Same ownership check, but takes a row lock so two concurrent gradings of the same
     * card are serialized instead of racing on the SM-2 state.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Card c WHERE c.id = :cardId AND c.deck.user.id = :userId")
    Optional<Card> findOwnedForUpdate(@Param("cardId") Long cardId, @Param("userId") Long userId);

    /**
     * The review queue: only cards due on or before {@code today}, most overdue first, then
     * weakest (lowest ease) first. Future cards are never returned.
     */
    @Query("""
            SELECT c FROM Card c JOIN FETCH c.deck d
            WHERE d.user.id = :userId AND c.dueDate <= :today
            ORDER BY c.dueDate ASC, c.easeFactor ASC, c.id ASC
            """)
    List<Card> findDue(@Param("userId") Long userId, @Param("today") LocalDate today, Pageable pageable);

    @Query("""
            SELECT c FROM Card c JOIN FETCH c.deck d
            WHERE d.user.id = :userId AND d.id = :deckId AND c.dueDate <= :today
            ORDER BY c.dueDate ASC, c.easeFactor ASC, c.id ASC
            """)
    List<Card> findDueInDeck(@Param("userId") Long userId, @Param("deckId") Long deckId,
                             @Param("today") LocalDate today, Pageable pageable);

    @Query("SELECT count(c) FROM Card c WHERE c.deck.user.id = :userId AND c.dueDate <= :today")
    long countDue(@Param("userId") Long userId, @Param("today") LocalDate today);

    @Query("SELECT count(c) FROM Card c WHERE c.deck.user.id = :userId AND c.dueDate < :today")
    long countOverdue(@Param("userId") Long userId, @Param("today") LocalDate today);

    /** Cards due per day inside a window; overdue cards are counted separately by the caller. */
    @Query(value = """
            SELECT c.due_date AS day, count(*) AS cards
            FROM cards c JOIN decks d ON d.id = c.deck_id
            WHERE d.user_id = :userId AND c.due_date BETWEEN :from AND :to
            GROUP BY c.due_date
            ORDER BY c.due_date
            """, nativeQuery = true)
    List<DueCount> dueByDay(@Param("userId") Long userId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * Every card on a topic regardless of due date, for targeted practice. Cards without a topic
     * belong to their deck's name, the same rule the topic insight uses. Hardest first: lowest ease,
     * then soonest due.
     */
    @Query(value = """
            SELECT c.* FROM cards c
            JOIN decks d ON d.id = c.deck_id
            WHERE d.user_id = :userId
              AND lower(btrim(coalesce(c.topic, d.name))) = lower(btrim(CAST(:topic AS TEXT)))
            ORDER BY CASE c.difficulty WHEN 'EXPERT' THEN 0 WHEN 'HARD' THEN 1 WHEN 'MEDIUM' THEN 2 ELSE 3 END,
                     c.ease_factor ASC, c.due_date ASC, c.id ASC
            """, nativeQuery = true)
    List<Card> findByTopic(@Param("userId") Long userId, @Param("topic") String topic, Pageable pageable);

    @Query(value = """
            SELECT count(*) FROM cards c
            JOIN decks d ON d.id = c.deck_id
            WHERE d.user_id = :userId
              AND lower(btrim(coalesce(c.topic, d.name))) = lower(btrim(CAST(:topic AS TEXT)))
            """, nativeQuery = true)
    long countByTopic(@Param("userId") Long userId, @Param("topic") String topic);

    @Query("""
            SELECT count(c) FROM Card c
            WHERE c.deck.user.id = :userId AND c.deck.id = :deckId AND c.dueDate <= :today
            """)
    long countDueInDeck(@Param("userId") Long userId, @Param("deckId") Long deckId, @Param("today") LocalDate today);

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
            SELECT c.difficulty AS difficulty, count(*) AS cards
            FROM cards c JOIN decks d ON d.id = c.deck_id
            WHERE d.user_id = :userId
            GROUP BY c.difficulty
            """, nativeQuery = true)
    List<DifficultyCount> countByDifficulty(@Param("userId") Long userId);

    @Query("SELECT avg(c.avgResponseMs) FROM Card c WHERE c.deck.user.id = :userId AND c.avgResponseMs IS NOT NULL")
    Double averageResponseMs(@Param("userId") Long userId);

    @Query("SELECT count(c) FROM Card c WHERE c.deck.user.id = :userId AND c.lapseCount > 0")
    long countWithLapses(@Param("userId") Long userId);

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
