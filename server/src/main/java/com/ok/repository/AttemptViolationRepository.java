package com.ok.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ok.entity.AttemptViolationEntity;

public interface AttemptViolationRepository
        extends JpaRepository<AttemptViolationEntity, Long> {
}
