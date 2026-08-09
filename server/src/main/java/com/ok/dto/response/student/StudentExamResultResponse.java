package com.ok.dto.response.student;

import java.math.BigDecimal;
import java.time.Instant;

import com.ok.domain.enums.ExamAttemptStatus;

public record StudentExamResultResponse(
        Long attemptId,
        Long examId,
        ExamAttemptStatus status,
        BigDecimal score,
        BigDecimal totalPoints,
        Instant submittedAt,
        int answeredCount,
        int totalQuestions
) {
}
