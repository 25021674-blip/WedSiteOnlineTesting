package com.ok.repository;

import com.ok.entity.ExamEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import com.ok.dto.ExamStatus;

public interface ExamRepository extends JpaRepository<ExamEntity, Long> {
    List<ExamEntity> findByCreatedByIdOrderByCreatedAtDesc(Long userId);
    List<ExamEntity> findByStatusOrderByCreatedAtDesc(ExamStatus status);
}
