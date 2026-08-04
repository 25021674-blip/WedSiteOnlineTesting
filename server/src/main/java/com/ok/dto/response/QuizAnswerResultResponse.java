package com.ok.dto.response;

public record QuizAnswerResultResponse(Long questionId, Long selectedOptionId,
        boolean correct, Double awardedPoints) {}
