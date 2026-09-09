package com.recallai.repository;

import com.recallai.entity.QuizAttempt;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, Long> {

    List<QuizAttempt> findByQuizIdAndUserIdOrderByCompletedAtDescIdDesc(Long quizId, Long userId);

    @Query("""
            SELECT DISTINCT a FROM QuizAttempt a
            LEFT JOIN FETCH a.answers ans
            LEFT JOIN FETCH ans.question
            JOIN FETCH a.quiz
            WHERE a.id = :attemptId AND a.quiz.id = :quizId AND a.user.id = :userId
            """)
    Optional<QuizAttempt> findDetail(@Param("attemptId") Long attemptId, @Param("quizId") Long quizId,
                                     @Param("userId") Long userId);

    long countByUserId(Long userId);
}
