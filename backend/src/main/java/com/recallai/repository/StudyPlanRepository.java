package com.recallai.repository;

import com.recallai.entity.StudyPlan;
import com.recallai.entity.StudyPlanStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudyPlanRepository extends JpaRepository<StudyPlan, Long> {

    Optional<StudyPlan> findByIdAndUserId(Long id, Long userId);

    List<StudyPlan> findByUserIdOrderByCreatedAtDescIdDesc(Long userId);

    List<StudyPlan> findByUserIdAndStatusOrderByExamDateAscIdDesc(Long userId, StudyPlanStatus status);

    @Query("""
            SELECT DISTINCT p FROM StudyPlan p LEFT JOIN FETCH p.tasks
            WHERE p.id = :id AND p.user.id = :userId
            """)
    Optional<StudyPlan> findWithTasks(@Param("id") Long id, @Param("userId") Long userId);
}
