package com.ok.dto.teacher_admin;

import java.util.List;

public record QuestionManagementResponse(Long id, Long examId, String content, Double points,
        List<AnswerOptionManagementResponse> options) {}
