package com.ok.dto.response.student;

import java.time.Instant;

import com.ok.domain.enums.ExamAttemptStatus;

public record StudentHeartbeatResponse(
        Long attemptId,
        ExamAttemptStatus status,
        Instant serverTime,
        Instant lastHeartbeatAt,
        long remainingSeconds
) {
}
