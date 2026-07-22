package com.ok.dto;

import java.time.LocalDateTime;

public record EssayAssignmentFileResponse(
        Long id,
        Long examId,
        String originalFileName,
        Long fileSize,
        LocalDateTime uploadedAt
) {
}
