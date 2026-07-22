package com.ok.dto;

import java.time.LocalDateTime;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record UpdateExamRequest(
        @NotBlank @Size(max = 200) String title, // @NotBlank → mặc định báo lỗi "must not be blank".
        @Size(max = 2000) String description, //@Size(max = 2000) → mặc định báo lỗi "size must be between 0 and 2000".
        @NotNull LocalDateTime startTime, //@NotNull → mặc định báo lỗi "must not be null".
        @NotNull LocalDateTime deadline,
        @Positive Integer durationMinutes //@Positive → mặc định báo lỗi "must be greater than 0".
) {}


// Dùng @NotNull cho mọi loại dữ liệu (object, số, ngày giờ…) để đảm bảo không null.
// Dùng @NotBlank cho chuỗi để đảm bảo có nội dung thực sự, không chỉ là khoảng trắng.
//Ví dụ:
//@NotNull String name;   // "   " vẫn hợp lệ
//@NotBlank String title; // "   " sẽ bị lỗi