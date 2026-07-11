package com.ok.dto;

import java.time.Instant;
import java.util.Map;

public record ErrorResponse(
        int status,
        String message,
        Instant timestamp,
        Map<String, String> errors
) {
}