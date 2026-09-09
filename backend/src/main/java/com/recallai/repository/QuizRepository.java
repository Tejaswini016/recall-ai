package com.recallai.repository;

import com.recallai.entity.Quiz;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuizRepository extends JpaRepository<Quiz, Long> {

    /** Ownership travels through the deck. */
    Optional<Quiz> findByIdAndDeckUserId(Long id, Long userId);

    Page<Quiz> findByDeckUserIdOrderByCreatedAtDescIdDesc(Long userId, Pageable pageable);

    Page<Quiz> findByDeckIdAndDeckUserIdOrderByCreatedAtDescIdDesc(Long deckId, Long userId, Pageable pageable);

    long countByDeckUserId(Long userId);

    @Query("""
            SELECT q.id AS quizId, count(qq) AS questionCount
            FROM Quiz q LEFT JOIN q.questions qq
            WHERE q.id IN :quizIds
            GROUP BY q.id
            """)
    List<QuestionCount> countQuestions(@Param("quizIds") Collection<Long> quizIds);

    @Query("""
            SELECT a.quiz.id AS quizId,
                   count(a) AS attemptCount,
                   max(a.score * 100.0 / a.totalQuestions) AS bestPercent
            FROM QuizAttempt a
            WHERE a.quiz.id IN :quizIds AND a.user.id = :userId
            GROUP BY a.quiz.id
            """)
    List<QuizStats> findStats(@Param("quizIds") Collection<Long> quizIds, @Param("userId") Long userId);

    interface QuestionCount {
        Long getQuizId();

        long getQuestionCount();
    }
}
