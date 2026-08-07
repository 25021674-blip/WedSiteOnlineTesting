package com.ok.dto.request.teacher;

import java.math.BigDecimal;
import java.util.List;

import com.ok.domain.enums.QuestionType;
import com.ok.dto.request.AnswerOptionRequest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateQuestionRequest(
        @NotNull(message = "Loại câu hỏi không được để trống")
        QuestionType questionType,

        @NotBlank(message = "Nội dung câu hỏi không được để trống")
        @Size(max = 2000, message = "Nội dung câu hỏi không được vượt quá 2000 ký tự")
        String content,

        @NotNull(message = "Điểm của câu hỏi không được để trống")
        @DecimalMin(value = "0.01", message = "Điểm của câu hỏi phải lớn hơn 0")
        @Digits(integer = 3, fraction = 2, message = "Điểm của câu hỏi tối đa 999.99 và có tối đa 2 chữ số thập phân")
        BigDecimal points,

        List<@NotNull @Valid AnswerOptionRequest> options
) {}
