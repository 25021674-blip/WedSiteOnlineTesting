package com.ok.dto;

public record QuizAnswerResultResponse(Long questionId, Long selectedOptionId,
        boolean correct, Double awardedPoints) {}
