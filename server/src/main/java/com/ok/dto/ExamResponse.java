package com.ok.dto;

import java.time.LocalDateTime;

public record ExamResponse(
        Long id,
        String title,
        String description,
        ExamType type,
        ExamStatus status,
        LocalDateTime startTime,
        LocalDateTime deadline,
        Integer durationMinutes,
        Long createdById,
        String createdByName,
        LocalDateTime createdAt,
        EssayAssignmentFileResponse assignmentFile
) {
}
