package com.ok.dto.response;

import java.time.LocalDateTime;

import com.ok.domain.enums.*;
import com.ok.dto.response.teacher.EssayAssignmentFileResponse;

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
