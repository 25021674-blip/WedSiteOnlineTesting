package com.ok.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ok.entity.ExamEntity;

public interface StudentExamRepository
        extends JpaRepository<ExamEntity, Long> {
}
