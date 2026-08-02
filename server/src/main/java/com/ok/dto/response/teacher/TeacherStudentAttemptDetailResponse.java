package com.ok.dto.response.teacher;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.ok.domain.enums.QuestionType;

public record TeacherStudentAttemptDetailResponseDemo(
        Long attemptId,
        String examTitle,
        Long studentId,
        String studentName,
        Instant startedAt,
        Instant submittedAt,
        int screenExitCount,
        BigDecimal score,
        BigDecimal maxScore,
        List<QuestionDetail> questions
) {

    public record QuestionDetail(
            Long questionId,
            Integer questionOrder,
            QuestionType questionType,
            String content,
            BigDecimal maxScore,
            List<OptionDetail> options,
            AnswerDetail answer
    ) {
    }

    public record OptionDetail(
            Long optionId,
            String content,
            boolean correct,
            Integer optionOrder
    ) {
    }

    public record AnswerDetail(
            Long selectedOptionId,
            String essayAnswer,
            BigDecimal score,
            Boolean correct
    ) {
    }
}
