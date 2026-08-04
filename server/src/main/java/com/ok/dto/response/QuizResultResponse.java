package com.ok.dto.response;

import java.time.LocalDateTime;
import java.util.List;

import com.ok.domain.enums.ExamAttemptStatus;

public record QuizResultResponse(Long submissionId, Long examId, Long studentId,
        ExamAttemptStatus status, LocalDateTime startedAt, LocalDateTime expiresAt,
        Double score, Double totalPoints, LocalDateTime submittedAt,
        List<QuizAnswerResultResponse> answers) {}
