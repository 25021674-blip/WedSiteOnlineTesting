package com.ok.dto.response.student;

import java.time.Instant;
import java.util.List;

import com.ok.domain.enums.ExamAttemptStatus;
import com.ok.domain.enums.ExamType;
import com.ok.domain.enums.QuestionType;

public record StudentExamScreenResponse(
        Long attemptId,
        Integer attemptNumber,
        int maxAttempts,
        int remainingAttempts,
        boolean timeLimitEnabled,
        boolean requireFullscreen,
        boolean trackTabSwitches,
        ExamInfo exam,
        ExamAttemptStatus status,
        Instant serverTime,
        Instant startedAt,
        Instant deadlineAt,
        long remainingSeconds,
        int screenExitCount,
        Progress progress,
        List<QuestionInfo> questions
) {

    public record ExamInfo(
            Long id,
            String title,
            String description,
            ExamType type,
            java.math.BigDecimal maxScore
    ) {
    }

    public record Progress(
            int answeredCount,
            int totalQuestions,
            List<Long> answeredQuestionIds
    ) {
    }

    public record QuestionInfo(
            Long id,
            Integer number,
            QuestionType type,
            String content,
            List<OptionInfo> options,
            AnswerInfo answer
    ) {
    }

    public record OptionInfo(
            Long id,
            String content
    ) {
    }

    public record AnswerInfo(
            Long selectedOptionId,
            String essayContent,
            Long clientRevision
    ) {
    }
}
