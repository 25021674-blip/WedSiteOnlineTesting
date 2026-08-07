package com.ok.repository;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.ok.entity.QuestionEntity;

public interface QuestionRepository extends JpaRepository<QuestionEntity, Long> {
    List<QuestionEntity> findByExamIdOrderById(Long examId);

    @EntityGraph(attributePaths = "options")
    List<QuestionEntity> findByExamIdOrderByQuestionOrderAsc(Long examId);

    long countByExamId(Long examId);
    void deleteByExamId(Long examId);
}
