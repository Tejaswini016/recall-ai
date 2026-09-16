package com.recallai.repository;

import com.recallai.entity.StudyPlanStatus;
import com.recallai.entity.StudyPlanTask;
import com.recallai.entity.StudyTaskStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudyPlanTaskRepository extends JpaRepository<StudyPlanTask, Long> {

    @Query("SELECT t FROM StudyPlanTask t JOIN FETCH t.plan p WHERE t.id = :taskId AND p.id = :planId AND p.user.id = :userId")
    Optional<StudyPlanTask> findOwned(@Param("taskId") Long taskId, @Param("planId") Long planId,
                                      @Param("userId") Long userId);

    @Query("""
            SELECT t FROM StudyPlanTask t JOIN FETCH t.plan p
            WHERE p.user.id = :userId AND p.status = :status AND t.scheduledDate = :date
            ORDER BY p.examDate ASC, t.sortOrder ASC, t.id ASC
            """)
    List<StudyPlanTask> findForDay(@Param("userId") Long userId, @Param("status") StudyPlanStatus status,
                                   @Param("date") LocalDate date);

    @Query("""
            SELECT t FROM StudyPlanTask t JOIN FETCH t.plan p
            WHERE p.user.id = :userId AND p.status = :planStatus AND t.status = :taskStatus
              AND t.scheduledDate < :before AND t.scheduledDate >= :since
            ORDER BY t.scheduledDate DESC, t.sortOrder ASC
            """)
    List<StudyPlanTask> findOverdue(@Param("userId") Long userId, @Param("planStatus") StudyPlanStatus planStatus,
                                    @Param("taskStatus") StudyTaskStatus taskStatus, @Param("before") LocalDate before,
                                    @Param("since") LocalDate since, Pageable pageable);

    long countByPlanIdAndStatus(Long planId, StudyTaskStatus status);
}
