package com.ok.dto;

import java.time.LocalDateTime;// biểu diễn ngày giờ (có cả ngày và thời gian, nhưng không kèm múi giờ).

import jakarta.validation.constraints.NotBlank;//Annotation để kiểm tra chuỗi (String) không được rỗng hoặc chỉ chứa khoảng trắng.
import jakarta.validation.constraints.NotNull;//Annotation để kiểm tra giá trị không được null.
import jakarta.validation.constraints.Positive;//Annotation để kiểm tra số phải lớn hơn 0.
import jakarta.validation.constraints.Size;//Annotation để kiểm tra độ dài của chuỗi.

public record CreateExamRequest(
        @NotBlank(message = "Tiêu đề không được để trống")
        @Size(max = 200, message = "Tiêu đề không được vượt quá 200 ký tự")
        String title,

        @Size(max = 2000, message = "Mô tả không được vượt quá 2000 ký tự")
        String description,

        @NotNull(message = "Loại bài kiểm tra không được để trống")
        ExamType type,

        @NotNull(message = "Thời gian bắt đầu không được để trống")
        LocalDateTime startTime,

        @NotNull(message = "Hạn nộp không được để trống")
        LocalDateTime deadline,

        @Positive(message = "Thời lượng phải lớn hơn 0")
        Integer durationMinutes
) {
}
