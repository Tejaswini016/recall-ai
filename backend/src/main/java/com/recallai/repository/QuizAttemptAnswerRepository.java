package com.recallai.repository;

import com.recallai.entity.QuizAttemptAnswer;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuizAttemptAnswerRepository extends JpaRepository<QuizAttemptAnswer, Long> {

    /**
     * Per-topic quiz performance. A question without a topic (quizzes generated before topics were
     * collected) counts under its deck's name so no answer is lost. Topics group case-insensitively.
     */
    @Query(value = """
            SELECT min(coalesce(qq.topic, d.name)) AS topic,
                   count(*) AS answers,
                   count(*) FILTER (WHERE a.correct) AS correct,
                   max(att.completed_at) AS lastAnsweredAt
            FROM quiz_attempt_answers a
            JOIN quiz_attempts att ON att.id = a.attempt_id
            JOIN quiz_questions qq ON qq.id = a.question_id
            JOIN quizzes q ON q.id = qq.quiz_id
            JOIN decks d ON d.id = q.deck_id
            WHERE att.user_id = :userId
              AND (CAST(:deckId AS BIGINT) IS NULL OR d.id = CAST(:deckId AS BIGINT))
            GROUP BY lower(btrim(coalesce(qq.topic, d.name)))
            """, nativeQuery = true)
    List<TopicQuizStats> topicQuizStats(@Param("userId") Long userId, @Param("deckId") Long deckId);
}
