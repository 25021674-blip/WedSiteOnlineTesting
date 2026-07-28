package com.ok.dto.request.student;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record SaveStudentAnswerRequest(
        @Positive(message = "Mã phương án phải lớn hơn 0")
        Long selectedOptionId,

        String essayContent,

        @NotNull(message = "Client revision không được để trống")
        @Positive(message = "Client revision phải lớn hơn 0")
        Long clientRevision
) {
}
