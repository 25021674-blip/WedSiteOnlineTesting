package com.ok.dto.response.teacher;

public record TeacherExamSummaryResponse(
        Long examId,
        String title,
        long completedStudentCount
) {
}
