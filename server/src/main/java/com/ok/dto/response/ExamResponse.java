package com.ok.dto.response;

import java.math.BigDecimal;
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
        BigDecimal maxScore,
        Long createdById,
        String createdByName,
        LocalDateTime createdAt,
        EssayAssignmentFileResponse assignmentFile
) {
}
