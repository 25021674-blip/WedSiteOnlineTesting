package com.ok.repository;

import com.ok.domain.enums.ExamStatus;
import com.ok.entity.ExamEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ExamRepository extends JpaRepository<ExamEntity, Long> {
    List<ExamEntity> findByTeacherIdOrderByCreatedAtDesc(Long userId);
    List<ExamEntity> findByStatusOrderByCreatedAtDesc(ExamStatus status);
}
