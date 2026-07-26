package com.ok.repository;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.ok.entity.QuestionEntity;

public interface StudentQuestionRepository
        extends JpaRepository<QuestionEntity, Long> {

    @EntityGraph(attributePaths = "options")
    List<QuestionEntity> findByExam_IdOrderByQuestionOrderAsc(Long examId);
}
