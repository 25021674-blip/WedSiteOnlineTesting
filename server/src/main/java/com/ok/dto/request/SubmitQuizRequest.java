package com.ok.dto.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record SubmitQuizRequest(@NotNull List<@Valid SelectedAnswerRequest> answers) {}
