package com.ok.dto.response.teacher;

import java.math.BigDecimal;
import java.time.Instant;

import com.ok.domain.enums.ExamType;

public record TeacherExamDetailResponse(
        Long examId,
        String title,
        ExamType type,
        long completedStudentCount,
        Instant createdAt,
        Instant expiresAt,
        Integer durationMinutes,
        BigDecimal maxScore
) {
}
