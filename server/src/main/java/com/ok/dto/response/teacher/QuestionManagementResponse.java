package com.ok.dto.response.teacher;

import java.math.BigDecimal;
import java.util.List;

import com.ok.domain.enums.QuestionType;

public record QuestionManagementResponse(
        Long id,
        Long examId,
        Integer questionOrder,
        QuestionType questionType,
        String content,
        BigDecimal points,
        List<AnswerOptionManagementResponse> options
) {}
