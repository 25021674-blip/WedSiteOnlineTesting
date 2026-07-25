package com.ok.dto.response;

public record TeacherExamSummaryResponse(
        String title,
        long completedStudentCount
) {
}
