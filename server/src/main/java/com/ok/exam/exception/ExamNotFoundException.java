package com.ok.exam.exception;

import org.springframework.http.HttpStatus;//HttpStatus: chứa các mã trạng thái HTTP (200 OK, 404 NOT_FOUND, 500 INTERNAL_SERVER_ERROR…).
import org.springframework.web.server.ResponseStatusException;//ResponseStatusException: class của Spring, cho phép bạn ném exception kèm theo mã HTTP và thông báo lỗi.

//Nghĩa là khi bạn ném ExamNotFoundException, Spring sẽ tự động trả về response với mã HTTP và message bạn định nghĩa.
public class ExamNotFoundException extends ResponseStatusException {

    public ExamNotFoundException(Long examId) {
        super(HttpStatus.NOT_FOUND, "Không tìm thấy bài kiểm tra có id " + examId);
    }
}
