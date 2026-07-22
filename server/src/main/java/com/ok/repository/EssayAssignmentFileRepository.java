package com.ok.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ok.essay.entity.EssayAssignmentFileEntity;

public interface EssayAssignmentFileRepository extends JpaRepository<EssayAssignmentFileEntity, Long> {
    boolean existsByExamId(Long examId);
    Optional<EssayAssignmentFileEntity> findByExamId(Long examId);
}
