package com.ok.dto.response.teacher;

public record TeacherExamSubmissionResponse(
        Long attemptId,
        Integer attemptNumber,
        Long studentId,
        String studentName
) {
}
