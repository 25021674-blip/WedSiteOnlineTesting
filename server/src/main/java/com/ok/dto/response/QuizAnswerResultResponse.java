package com.ok.dto.response;

public record QuizAnswerResultResponse(Long questionId, Long selectedOptionId, Long correctOptionId,
        Boolean correct, Double awardedPoints) {}
