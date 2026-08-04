package com.ok.dto.request.teacher;

import java.util.List;

import com.ok.dto.request.AnswerOptionRequest;

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
