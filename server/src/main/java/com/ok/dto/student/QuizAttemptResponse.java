package com.ok.dto.student;

import com.ok.dto.common.QuizAttemptStatus;

import java.time.LocalDateTime;

public record QuizAttemptResponse(
        Long attemptId,
        Long examId,
        Long studentId,
        QuizAttemptStatus status,
        LocalDateTime startedAt,
        LocalDateTime expiresAt,
        LocalDateTime submittedAt,
        LocalDateTime serverTime
) {}
