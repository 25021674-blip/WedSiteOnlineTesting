package com.ok.dto.student;

public record QuizAnswerResultResponse(Long questionId, Long selectedOptionId,
        boolean correct, Double awardedPoints) {}
