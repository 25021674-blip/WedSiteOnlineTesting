package com.ok.dto;

public record TeacherExamSummaryResponse(
        String title,
        long completedStudentCount
) {
}
