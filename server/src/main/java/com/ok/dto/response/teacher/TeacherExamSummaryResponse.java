package com.ok.dto.response.teacher;

public record TeacherExamSummaryResponse(
        String title,
        long completedStudentCount
) {
}
