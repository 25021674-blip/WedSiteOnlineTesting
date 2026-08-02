package com.ok.repository;

import java.math.BigDecimal;
import java.time.Instant;

import com.ok.domain.enums.ExamType;

public interface TeacherExamDetailView {

    Long getExamId();

    String getTitle();

    ExamType getType();

    long getCompletedStudentCount();

    Instant getCreatedAt();

    Instant getExpiresAt();

    Integer getDurationMinutes();

    BigDecimal getMaxScore();
}
