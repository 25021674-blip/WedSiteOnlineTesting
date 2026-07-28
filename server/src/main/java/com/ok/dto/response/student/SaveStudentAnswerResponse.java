package com.ok.dto.response.student;

import java.time.Instant;

import com.ok.domain.enums.QuestionType;

public record SaveStudentAnswerResponse(
        Long attemptId,
        Long questionId,
        QuestionType type,
        Long selectedOptionId,
        String essayContent,
        Long clientRevision,
        Instant updatedAt,
        boolean answered
) {
}
