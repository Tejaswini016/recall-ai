package com.recallai.repository;

import com.recallai.entity.Deck;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeckRepository extends JpaRepository<Deck, Long> {

    /** Ownership check and lookup in one query: a deck that is not the user's does not exist for them. */
    Optional<Deck> findByIdAndUserId(Long id, Long userId);

    long countByUserId(Long userId);

    /**
     * Filtered, paginated listing done entirely in PostgreSQL. Full-text search covers name,
     * description and subject; prefix matching covers tags; all filters are optional.
     * Parameters are CAST so PostgreSQL can type NULL bindings.
     */
    @Query(value = """
            SELECT d.* FROM decks d
            WHERE d.user_id = :userId
              AND (CAST(:q AS TEXT) IS NULL
                   OR d.search_vector @@ websearch_to_tsquery('english', CAST(:q AS TEXT))
                   OR d.name ILIKE ('%' || CAST(:q AS TEXT) || '%')
                   OR d.subject ILIKE ('%' || CAST(:q AS TEXT) || '%')
                   OR EXISTS (SELECT 1 FROM unnest(d.tags) t WHERE t ILIKE (CAST(:q AS TEXT) || '%')))
              AND (CAST(:subject AS TEXT) IS NULL OR d.subject ILIKE CAST(:subject AS TEXT))
              AND (CAST(:tag AS TEXT) IS NULL OR CAST(:tag AS TEXT) = ANY(d.tags))
            ORDER BY d.updated_at DESC, d.id DESC
            """,
            countQuery = """
            SELECT count(*) FROM decks d
            WHERE d.user_id = :userId
              AND (CAST(:q AS TEXT) IS NULL
                   OR d.search_vector @@ websearch_to_tsquery('english', CAST(:q AS TEXT))
                   OR d.name ILIKE ('%' || CAST(:q AS TEXT) || '%')
                   OR d.subject ILIKE ('%' || CAST(:q AS TEXT) || '%')
                   OR EXISTS (SELECT 1 FROM unnest(d.tags) t WHERE t ILIKE (CAST(:q AS TEXT) || '%')))
              AND (CAST(:subject AS TEXT) IS NULL OR d.subject ILIKE CAST(:subject AS TEXT))
              AND (CAST(:tag AS TEXT) IS NULL OR CAST(:tag AS TEXT) = ANY(d.tags))
            """,
            nativeQuery = true)
    Page<Deck> search(@Param("userId") Long userId,
                      @Param("q") String q,
                      @Param("subject") String subject,
                      @Param("tag") String tag,
                      Pageable pageable);

    @Query(value = """
            SELECT d.id AS deckId,
                   count(c.id) AS cardCount,
                   count(c.id) FILTER (WHERE c.due_date <= :today) AS dueCount,
                   count(c.id) FILTER (WHERE c.interval >= :masteredInterval) AS masteredCount
            FROM decks d
            LEFT JOIN cards c ON c.deck_id = d.id
            WHERE d.id IN (:deckIds)
            GROUP BY d.id
            """, nativeQuery = true)
    List<DeckStats> findStats(@Param("deckIds") Collection<Long> deckIds,
                              @Param("today") LocalDate today,
                              @Param("masteredInterval") int masteredInterval);
}
