package com.ok.dto.response.student;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.ok.domain.enums.ExamAttemptStatus;
import com.ok.domain.enums.QuestionType;

public record StudentExamResultResponse(
        Long attemptId,
        Integer attemptNumber,
        ExamAttemptStatus status,
        Instant submittedAt,
        boolean scoreVisible,
        boolean correctAnswersVisible,
        BigDecimal score,
        BigDecimal maxScore,
        List<QuestionResult> questions
) {

    public record QuestionResult(
            Long questionId,
            Integer number,
            QuestionType type,
            Long selectedOptionId,
            String essayContent,
            Long correctOptionId,
            Boolean correct,
            BigDecimal awardedScore,
            BigDecimal maxScore
    ) {
    }
}
