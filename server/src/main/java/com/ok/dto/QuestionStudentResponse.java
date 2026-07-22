package com.ok.dto;

import java.util.List;

public record QuestionStudentResponse(Long id, String content, Double points,
        List<AnswerOptionStudentResponse> options) {}
