package com.ok.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record GradeEssayRequest(
        @NotNull @DecimalMin("0.0") Double score,
        @Size(max = 2000) String feedback
) {}
