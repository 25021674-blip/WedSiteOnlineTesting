package com.ok.dto.response.student;

import java.util.List;

public record QuestionStudentResponse(Long id, String content, Double points,
        List<AnswerOptionStudentResponse> options) {}
