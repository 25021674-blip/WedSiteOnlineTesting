package com.ok.dto;

import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public record CreateQuestionRequest(
        @NotBlank @Size(max = 2000) String content,
        @DecimalMin("0.1") double points,
        @NotEmpty @Size(min = 2) List<@Valid AnswerOptionRequest> options
) {}
