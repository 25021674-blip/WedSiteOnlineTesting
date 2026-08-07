package com.ok.dto.response.teacher;

public record AnswerOptionManagementResponse(
        Long id,
        Integer optionOrder,
        String content,
        boolean correct
) {}
