package com.ok.dto.response.student;

import java.math.BigDecimal;
import java.time.Instant;

import com.ok.domain.enums.ExamAttemptStatus;

public record SubmitStudentExamResponse(
        Long attemptId,
        ExamAttemptStatus status,
        Instant submittedAt,
        Instant serverTime,
        boolean scoreVisible,
        BigDecimal score,
        BigDecimal maxScore
) {
}
