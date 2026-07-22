package com.ok.dto;

import java.util.List;

public record QuestionManagementResponse(Long id, Long examId, String content, Double points,
        List<AnswerOptionManagementResponse> options) {}
