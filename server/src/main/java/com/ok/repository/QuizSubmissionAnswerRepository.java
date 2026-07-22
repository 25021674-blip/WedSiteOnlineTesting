package com.ok.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.ok.quiz.entity.QuizSubmissionAnswerEntity;

public interface QuizSubmissionAnswerRepository extends JpaRepository<QuizSubmissionAnswerEntity, Long> {
    List<QuizSubmissionAnswerEntity> findBySubmissionIdOrderById(Long submissionId);
}
