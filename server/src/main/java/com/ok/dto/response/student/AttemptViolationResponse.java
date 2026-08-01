package com.ok.dto.response.student;

import java.time.Instant;

import com.ok.domain.enums.AttemptViolationType;

public record AttemptViolationResponse(
        Long violationId,
        Long attemptId,
        AttemptViolationType type,
        Instant clientTime,
        Instant serverReceivedAt,
        int screenExitCount
) {
}
