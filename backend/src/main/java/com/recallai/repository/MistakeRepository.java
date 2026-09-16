package com.recallai.repository;

import com.recallai.entity.Mistake;
import com.recallai.entity.MistakeSource;
import com.recallai.entity.MistakeStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MistakeRepository extends JpaRepository<Mistake, Long> {

    Optional<Mistake> findByIdAndUserId(Long id, Long userId);

    Optional<Mistake> findByUserIdAndQuizQuestionId(Long userId, Long quizQuestionId);

    List<Mistake> findByUserIdAndQuizQuestionIdIn(Long userId, Collection<Long> quizQuestionIds);

    Optional<Mistake> findByUserIdAndMockExamQuestionId(Long userId, Long mockExamQuestionId);

    List<Mistake> findByUserIdAndMockExamQuestionIdIn(Long userId, Collection<Long> mockExamQuestionIds);

    long countByUserIdAndStatus(Long userId, MistakeStatus status);

    long countByUserId(Long userId);

    /** Newest miss first; every filter is optional. Enum filters are passed as their names. */
    @Query(value = """
            SELECT m.* FROM mistakes m
            WHERE m.user_id = :userId
              AND (CAST(:status AS TEXT) IS NULL OR m.status = CAST(:status AS TEXT))
              AND (CAST(:source AS TEXT) IS NULL OR m.source = CAST(:source AS TEXT))
              AND (CAST(:topic AS TEXT) IS NULL OR lower(btrim(m.topic)) = lower(btrim(CAST(:topic AS TEXT))))
            ORDER BY m.last_missed_at DESC, m.id DESC
            """,
            countQuery = """
            SELECT count(*) FROM mistakes m
            WHERE m.user_id = :userId
              AND (CAST(:status AS TEXT) IS NULL OR m.status = CAST(:status AS TEXT))
              AND (CAST(:source AS TEXT) IS NULL OR m.source = CAST(:source AS TEXT))
              AND (CAST(:topic AS TEXT) IS NULL OR lower(btrim(m.topic)) = lower(btrim(CAST(:topic AS TEXT))))
            """,
            nativeQuery = true)
    Page<Mistake> search(@Param("userId") Long userId, @Param("status") String status,
                         @Param("source") String source, @Param("topic") String topic, Pageable pageable);

    List<Mistake> findTop5ByUserIdAndStatusOrderByLastMissedAtDescIdDesc(Long userId, MistakeStatus status);

    @Query(value = """
            SELECT min(m.topic) AS topic, count(*) AS count
            FROM mistakes m
            WHERE m.user_id = :userId AND m.status = 'OPEN' AND m.topic IS NOT NULL
            GROUP BY lower(btrim(m.topic))
            ORDER BY count DESC, topic ASC
            LIMIT 5
            """, nativeQuery = true)
    List<TopicCount> openByTopic(@Param("userId") Long userId);

    default List<Mistake> findOwnedByQuestionIds(Long userId, Collection<Long> ids) {
        return ids.isEmpty() ? List.of() : findByUserIdAndQuizQuestionIdIn(userId, ids);
    }

    /** Source is a JPQL enum comparison; used by mock exams later. */
    long countByUserIdAndSource(Long userId, MistakeSource source);
}
