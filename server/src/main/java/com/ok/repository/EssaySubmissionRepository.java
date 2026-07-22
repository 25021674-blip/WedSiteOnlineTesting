package com.ok.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.ok.essay.entity.EssaySubmissionEntity;

public interface EssaySubmissionRepository extends JpaRepository<EssaySubmissionEntity, Long> {
    boolean existsByExamIdAndStudentId(Long examId, Long studentId);
    Optional<EssaySubmissionEntity> findByExamIdAndStudentId(Long examId, Long studentId);
    List<EssaySubmissionEntity> findByExamIdOrderBySubmittedAtDesc(Long examId);
}
