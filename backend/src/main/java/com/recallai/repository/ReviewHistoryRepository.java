package com.recallai.repository;

import com.recallai.entity.ReviewHistory;
import java.sql.Date;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewHistoryRepository extends JpaRepository<ReviewHistory, Long> {

    long countByUserId(Long userId);

    long countByUserIdAndReviewedAtGreaterThanEqual(Long userId, Instant since);

    /** Distinct calendar days (UTC) on which the user reviewed at least one card, newest first. */
    @Query(value = """
            SELECT DISTINCT CAST(r.reviewed_at AT TIME ZONE 'UTC' AS DATE) AS review_day
            FROM review_history r
            WHERE r.user_id = :userId
            ORDER BY review_day DESC
            """, nativeQuery = true)
    List<Date> findDistinctReviewDays(@Param("userId") Long userId);

    @Query(value = """
            SELECT r FROM ReviewHistory r
            JOIN FETCH r.card c
            JOIN FETCH c.deck
            WHERE r.user.id = :userId
            ORDER BY r.reviewedAt DESC, r.id DESC
            """,
            countQuery = "SELECT count(r) FROM ReviewHistory r WHERE r.user.id = :userId")
    Page<ReviewHistory> findByUserIdWithCards(@Param("userId") Long userId, Pageable pageable);

    @Query("SELECT avg(r.qualityScore) FROM ReviewHistory r WHERE r.user.id = :userId")
    Double averageQuality(@Param("userId") Long userId);

    /** Fraction of successful (quality >= 3) reviews since a point in time, in 0..1. */
    @Query("""
            SELECT avg(CASE WHEN r.qualityScore >= 3 THEN 1.0 ELSE 0.0 END)
            FROM ReviewHistory r WHERE r.user.id = :userId AND r.reviewedAt >= :since
            """)
    Double retentionSince(@Param("userId") Long userId, @Param("since") Instant since);

    @Query(value = """
            SELECT CAST(r.reviewed_at AT TIME ZONE 'UTC' AS DATE) AS day,
                   count(*) AS reviews,
                   count(*) FILTER (WHERE r.quality_score >= 3) AS successful,
                   avg(r.quality_score) AS averageQuality
            FROM review_history r
            WHERE r.user_id = :userId AND r.reviewed_at >= :since
            GROUP BY day
            ORDER BY day
            """, nativeQuery = true)
    List<DailyActivity> dailyActivity(@Param("userId") Long userId, @Param("since") Instant since);

    /**
     * The day each card first reached the mastered interval, grouped and counted, so the
     * caller can accumulate "cards mastered over time".
     */
    @Query(value = """
            SELECT CAST(first_mastered AT TIME ZONE 'UTC' AS DATE) AS day, count(*) AS reviews,
                   0 AS successful, NULL AS averageQuality
            FROM (
                SELECT r.card_id, min(r.reviewed_at) AS first_mastered
                FROM review_history r
                WHERE r.user_id = :userId AND r.new_interval >= :masteredInterval
                GROUP BY r.card_id
            ) m
            GROUP BY day
            ORDER BY day
            """, nativeQuery = true)
    List<DailyActivity> masteryByDay(@Param("userId") Long userId, @Param("masteredInterval") int masteredInterval);

    /**
     * Per-topic performance. Topics are grouped case-insensitively; the recent average is a
     * window over each topic's newest {@code recentWindow} reviews. All in one query.
     */
    @Query(value = """
            WITH scored AS (
                SELECT lower(btrim(c.topic)) AS topic_key,
                       c.topic AS topic,
                       c.id AS card_id,
                       r.quality_score,
                       r.reviewed_at,
                       row_number() OVER (PARTITION BY lower(btrim(c.topic))
                                          ORDER BY r.reviewed_at DESC, r.id DESC) AS recency
                FROM review_history r
                JOIN cards c ON c.id = r.card_id
                JOIN decks d ON d.id = c.deck_id
                WHERE r.user_id = :userId
                  AND c.topic IS NOT NULL
                  AND (CAST(:deckId AS BIGINT) IS NULL OR d.id = CAST(:deckId AS BIGINT))
            )
            SELECT min(topic) AS topic,
                   count(DISTINCT card_id) AS cardCount,
                   count(*) AS reviews,
                   avg(quality_score) AS averageQuality,
                   avg(quality_score) FILTER (WHERE recency <= :recentWindow) AS recentAverageQuality,
                   avg(CASE WHEN quality_score >= 3 THEN 1.0 ELSE 0.0 END) AS successRate,
                   max(reviewed_at) AS lastReviewedAt
            FROM scored
            GROUP BY topic_key
            ORDER BY recentAverageQuality ASC, reviews DESC, topic ASC
            """, nativeQuery = true)
    List<TopicStats> topicStats(@Param("userId") Long userId, @Param("deckId") Long deckId,
                                @Param("recentWindow") int recentWindow);

    /**
     * Review counts per topic for the combined topic insight. Unlike {@link #topicStats}, cards
     * without a topic count under their deck's name, matching how quiz answers are grouped.
     */
    @Query(value = """
            SELECT min(coalesce(c.topic, d.name)) AS topic,
                   count(DISTINCT c.id) AS cardCount,
                   count(*) AS reviews,
                   count(*) FILTER (WHERE r.quality_score >= 3) AS successful,
                   max(r.reviewed_at) AS lastReviewedAt
            FROM review_history r
            JOIN cards c ON c.id = r.card_id
            JOIN decks d ON d.id = c.deck_id
            WHERE r.user_id = :userId
              AND (CAST(:deckId AS BIGINT) IS NULL OR d.id = CAST(:deckId AS BIGINT))
            GROUP BY lower(btrim(coalesce(c.topic, d.name)))
            """, nativeQuery = true)
    List<TopicReviewStats> topicReviewStats(@Param("userId") Long userId, @Param("deckId") Long deckId);
}
