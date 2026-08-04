package com.ok.dto.response.teacher;

import java.util.List;

public record QuestionManagementResponse(Long id, Long examId, String content, Double points,
        List<AnswerOptionManagementResponse> options) {}
