package com.ok.dto.response.student;

import java.time.Instant;

import com.ok.domain.enums.ExamAttemptStatus;

public record SubmitStudentExamResponseDemo(
        Long attemptId,
        ExamAttemptStatus status,
        Instant submittedAt,
        Instant serverTime
) {
}
