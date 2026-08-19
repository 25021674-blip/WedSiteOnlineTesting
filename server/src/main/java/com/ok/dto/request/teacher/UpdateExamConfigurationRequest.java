package com.ok.dto.request.teacher;

import java.util.Set;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record UpdateExamConfigurationRequest(
        @NotNull Boolean showCorrectAnswersAfterSubmit,
        @NotNull Boolean showScoreAfterSubmit,
        @NotNull @Min(value = 1, message = "Số lần làm bài tối đa phải từ 1 trở lên")
        Integer maxAttempts,
        @NotNull Boolean timeLimitEnabled,
        @NotNull Boolean requireFullscreen,
        @NotNull Boolean trackTabSwitches,
        @NotNull Set<@NotNull @Positive Long> recipientStudentIds
) {
}
