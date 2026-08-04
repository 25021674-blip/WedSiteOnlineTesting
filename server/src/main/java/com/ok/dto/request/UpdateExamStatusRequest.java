package com.ok.dto.request;

import com.ok.domain.enums.ExamStatus;

import jakarta.validation.constraints.NotNull;

public record UpdateExamStatusRequest(@NotNull ExamStatus status) {}
