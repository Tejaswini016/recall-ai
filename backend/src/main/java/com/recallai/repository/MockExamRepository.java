package com.recallai.repository;

import com.recallai.entity.MockExam;
import com.recallai.entity.MockExamStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MockExamRepository extends JpaRepository<MockExam, Long> {

    Optional<MockExam> findByIdAndUserId(Long id, Long userId);

    @Query("SELECT DISTINCT e FROM MockExam e LEFT JOIN FETCH e.questions WHERE e.id = :id AND e.user.id = :userId")
    Optional<MockExam> findWithQuestions(@Param("id") Long id, @Param("userId") Long userId);

    List<MockExam> findByUserIdOrderByCreatedAtDescIdDesc(Long userId, Pageable pageable);

    long countByUserId(Long userId);

    long countByUserIdAndStatus(Long userId, MockExamStatus status);

    @Query("""
            SELECT avg(e.score * 100.0 / e.totalQuestions) FROM MockExam e
            WHERE e.user.id = :userId AND e.status = :status AND e.totalQuestions > 0
            """)
    Double averagePercent(@Param("userId") Long userId, @Param("status") MockExamStatus status);

    @Query("""
            SELECT max(e.score * 100.0 / e.totalQuestions) FROM MockExam e
            WHERE e.user.id = :userId AND e.status = :status AND e.totalQuestions > 0
            """)
    Double bestPercent(@Param("userId") Long userId, @Param("status") MockExamStatus status);
}
