package com.ok.dto.response.teacher;

import java.time.Instant;
import java.util.List;

import com.ok.domain.enums.ExamStatus;

public record ExamConfigurationResponse(
        Long examId,
        ExamStatus status,
        boolean showCorrectAnswersAfterSubmit,
        boolean showScoreAfterSubmit,
        int maxAttempts,
        boolean timeLimitEnabled,
        boolean requireFullscreen,
        boolean trackTabSwitches,
        List<Recipient> recipients
) {

    public record Recipient(
            Long studentId,
            String fullName,
            String email,
            Instant assignedAt
    ) {
    }
}
