package com.ok.dto;

import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

public record SubmitQuizRequest(@NotEmpty List<@Valid SelectedAnswerRequest> answers) {}
