package com.ok.dto.student;

import jakarta.validation.constraints.NotNull;

public record SelectedAnswerRequest(@NotNull Long questionId, @NotNull Long selectedOptionId) {}
