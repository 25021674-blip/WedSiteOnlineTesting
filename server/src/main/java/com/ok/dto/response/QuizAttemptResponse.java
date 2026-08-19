package com.ok.dto.response;

import java.time.LocalDateTime;

import com.ok.domain.enums.ExamAttemptStatus;

public record QuizAttemptResponse(
        Long attemptId,
        Integer attemptNumber,
        Long examId,
        Long studentId,
        ExamAttemptStatus status,
        LocalDateTime startedAt,
        LocalDateTime expiresAt,
        LocalDateTime submittedAt,
        LocalDateTime serverTime
) {}
