package com.ok.dto.request.student;

import java.time.Instant;

import com.ok.domain.enums.AttemptViolationType;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RecordAttemptViolationRequestDemo(
        @NotNull(message = "Loại vi phạm không được để trống")
        AttemptViolationType type,

        @NotNull(message = "Thời gian phía client không được để trống")
        Instant clientTime,

        @Size(
                max = 4000,
                message = "Metadata không được vượt quá 4000 ký tự"
        )
        String metadata
) {
}
