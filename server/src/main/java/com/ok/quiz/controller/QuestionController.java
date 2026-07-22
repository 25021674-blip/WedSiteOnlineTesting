package com.ok.quiz.controller;

import java.security.Principal;//Principal: đại diện cho người dùng đang đăng nhập (Spring Security sẽ truyền vào).
import java.util.List;//List: kiểu danh sách trong Java, dùng để trả về nhiều câu hỏi.
import org.springframework.http.HttpStatus;//HttpStatus: để định nghĩa mã trạng thái HTTP (ví dụ: 201 CREATED).
import org.springframework.web.bind.annotation.*;//@RestController, @PostMapping, @GetMapping, @ResponseStatus, @PathVariable, @RequestBody… → các annotation của Spring để xây dựng REST API.
import com.ok.dto.*;//Đây là các class record bạn đã tạo (ví dụ: CreateQuestionRequest, QuestionManagementResponse, QuestionStudentResponse).
import com.ok.quiz.service.QuestionService;//Service chứa logic xử lý nghiệp vụ liên quan đến câu hỏi.Controller gọi service để thực hiện công việc (tạo câu hỏi, lấy danh sách…).
import jakarta.validation.Valid;//@Valid: dùng để kích hoạt cơ chế kiểm tra dữ liệu (validation) trên DTO. Ví dụ: nếu CreateQuestionRequest có @NotBlank, thì khi client gửi dữ liệu rỗng, Spring sẽ tự động báo lỗi.
import lombok.RequiredArgsConstructor;//Tự động sinh constructor với các field final. Giúp bạn không phải viết code khởi tạo QuestionService bằng tay.

@RestController
@RequiredArgsConstructor
public class QuestionController {
    private final QuestionService service;

    @PostMapping("/api/exams/{examId}/questions")
    @ResponseStatus(HttpStatus.CREATED)
    public QuestionManagementResponse create(@PathVariable Long examId,
            @Valid @RequestBody CreateQuestionRequest request, Principal principal) {
        return service.create(examId, request, principal.getName());
    }

    @GetMapping("/api/exams/{examId}/questions")
    public List<QuestionManagementResponse> management(@PathVariable Long examId, Principal principal) {
        return service.managementList(examId, principal.getName());
    }

    @GetMapping("/api/exams/{examId}/quiz")
    public List<QuestionStudentResponse> student(@PathVariable Long examId, Principal principal) {
        return service.studentList(examId, principal.getName());
    }

    @PutMapping("/api/questions/{id}")
    public QuestionManagementResponse update(@PathVariable Long id,
            @Valid @RequestBody CreateQuestionRequest request, Principal principal) {
        return service.update(id, request, principal.getName());
    }

    @DeleteMapping("/api/questions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, Principal principal) {
        service.delete(id, principal.getName());
    }
}
