package com.ok.dto.response.student;

import java.time.Instant;

public record StudentExamWebSocketErrorResponseDemo(
        int status,
        String message,
        Instant serverTime
) {
}
