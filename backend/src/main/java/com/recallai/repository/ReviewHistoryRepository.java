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
}
