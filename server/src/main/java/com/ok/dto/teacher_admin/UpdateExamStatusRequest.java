package com.ok.dto.teacher_admin;

import com.ok.dto.common.ExamStatus;

import jakarta.validation.constraints.NotNull;

public record UpdateExamStatusRequest(@NotNull ExamStatus status) {}
