package com.ok.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

import com.ok.entity.QuizSubmissionAnswerEntity;

public interface QuizSubmissionAnswerRepository extends JpaRepository<QuizSubmissionAnswerEntity, Long> {
    List<QuizSubmissionAnswerEntity> findBySubmissionIdOrderById(Long submissionId);
    Optional<QuizSubmissionAnswerEntity> findBySubmissionIdAndQuestionId(Long submissionId, Long questionId);
}
