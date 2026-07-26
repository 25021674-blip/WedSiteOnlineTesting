package com.ok.repository;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.ok.entity.StudentAnswerEntity;

public interface StudentAnswerRepository
        extends JpaRepository<StudentAnswerEntity, Long> {

    @EntityGraph(attributePaths = {"question", "selectedOption"})
    List<StudentAnswerEntity> findByAttempt_Id(Long attemptId);
}
