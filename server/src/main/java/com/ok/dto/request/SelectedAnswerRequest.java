package com.ok.dto.request;

import jakarta.validation.constraints.NotNull;

public record SelectedAnswerRequest(@NotNull Long questionId, @NotNull Long selectedOptionId) {}
