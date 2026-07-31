package com.ok.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ok.entity.AttemptViolationEntity;

public interface AttemptViolationRepositoryDemo
        extends JpaRepository<AttemptViolationEntity, Long> {
}
