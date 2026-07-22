package com.ok.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateExamStatusRequest(@NotNull ExamStatus status) {}
