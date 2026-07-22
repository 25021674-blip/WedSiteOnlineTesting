package com.ok.dto;

import java.time.LocalDateTime;

public record EssaySubmissionResponse(Long id, Long examId, Long studentId, String studentName,
        String originalFileName, Long fileSize, LocalDateTime submittedAt,
        Double score, String feedback) {}
