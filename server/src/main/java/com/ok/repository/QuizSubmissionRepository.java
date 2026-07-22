package com.ok.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.ok.quiz.entity.QuizSubmissionEntity;

public interface QuizSubmissionRepository extends JpaRepository<QuizSubmissionEntity, Long> {
    boolean existsByExamIdAndStudentId(Long examId, Long studentId);
    Optional<QuizSubmissionEntity> findByExamIdAndStudentId(Long examId, Long studentId);
}
