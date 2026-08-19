package com.ok.dto.response;

import java.time.LocalDateTime;

public record EssaySubmissionResponse(Long id, Integer attemptNumber, Long examId, Long studentId, String studentName,
        String originalFileName, Long fileSize, LocalDateTime submittedAt,
        Double score, String feedback) {}
