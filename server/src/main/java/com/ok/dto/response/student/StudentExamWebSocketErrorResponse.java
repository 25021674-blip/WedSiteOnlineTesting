package com.ok.dto.response.student;

import java.time.Instant;

public record StudentExamWebSocketErrorResponse(
        int status,
        String message,
        Instant serverTime
) {
}
