package com.ok.dto;

import java.time.LocalDateTime;
import java.util.List;

public record QuizResultResponse(Long submissionId, Long examId, Long studentId,
        Double score, Double totalPoints, LocalDateTime submittedAt,
        List<QuizAnswerResultResponse> answers) {}
