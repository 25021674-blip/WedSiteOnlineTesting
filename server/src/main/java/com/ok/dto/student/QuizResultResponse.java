package com.ok.dto.student;

import com.ok.dto.common.QuizAttemptStatus;

import java.time.LocalDateTime;
import java.util.List;

public record QuizResultResponse(Long submissionId, Long examId, Long studentId,
        QuizAttemptStatus status, LocalDateTime startedAt, LocalDateTime expiresAt,
        Double score, Double totalPoints, LocalDateTime submittedAt,
        List<QuizAnswerResultResponse> answers) {}
