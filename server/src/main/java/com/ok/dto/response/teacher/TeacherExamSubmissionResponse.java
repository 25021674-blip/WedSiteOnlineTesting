package com.ok.dto.response.teacher;

public record TeacherExamSubmissionResponse(
        Long attemptId,
        Long studentId,
        String studentName
) {
}
