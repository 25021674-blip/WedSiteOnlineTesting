package com.ok.repository;

import java.util.Optional;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import com.ok.dto.QuizAttemptStatus;
import com.ok.quiz.entity.QuizSubmissionEntity;

public interface QuizSubmissionRepository extends JpaRepository<QuizSubmissionEntity, Long> {
    boolean existsByExamIdAndStudentId(Long examId, Long studentId);
    Optional<QuizSubmissionEntity> findByExamIdAndStudentId(Long examId, Long studentId);
    List<QuizSubmissionEntity> findByStatusAndExpiresAtLessThanEqualOrderByExpiresAtAsc(
            QuizAttemptStatus status, LocalDateTime expiresAt, Pageable pageable);
}
