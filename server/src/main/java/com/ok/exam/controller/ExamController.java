package com.ok.exam.controller;

import java.security.Principal;  //Principal: đối tượng đại diện cho user hiện tại (thường lấy username qua principal.getName()).
import java.util.List;

import org.springframework.http.HttpStatus; //quy định mã HTTP trả về (201 khi tạo mới, 204 khi xóa).
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;  //@PathVariable: lấy giá trị từ URL (ví dụ /api/exams/{id}).
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;  //@RequestBody: lấy dữ liệu JSON từ body request và map vào DTO.
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ok.dto.request.UpdateExamRequest;
import com.ok.dto.request.UpdateExamStatusRequest;
import com.ok.dto.request.teacher.CreateExamRequest;
import com.ok.dto.response.ExamResponse;
import com.ok.exam.service.ExamService;

import jakarta.validation.Valid; // kích hoạt validation theo annotation trong DTO (ví dụ @NotBlank, @Size, @NotNull).
import lombok.RequiredArgsConstructor;

@RestController //đánh dấu đây là REST API controller, trả dữ liệu dạng JSON.
@RequestMapping("/api/exams")  //tất cả endpoint trong class này sẽ bắt đầu bằng /api/exams.
@RequiredArgsConstructor       //Lombok tự sinh constructor cho examService (field final), giúp inject service mà không cần viết code thủ công.
public class ExamController {
    private final ExamService examService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED) //quy định mã HTTP trả về (201 khi tạo mới, 204 khi xóa).
    public ExamResponse create(@Valid @RequestBody CreateExamRequest request, Principal principal) {
        return examService.createExam(request, principal.getName());
    }


    @GetMapping
    public List<ExamResponse> getAll(Principal principal) {
        return examService.getAllExams(principal.getName());
    }

    @GetMapping("/{id}")
    public ExamResponse getOne(@PathVariable Long id, Principal principal) {
        return examService.getExamById(id, principal.getName());
    }

    @PutMapping("/{id}")
    public ExamResponse update(@PathVariable Long id,
                               @Valid @RequestBody UpdateExamRequest request,
                               Principal principal) {
        return examService.updateExam(id, request, principal.getName());
    }

    @PatchMapping("/{id}/status")
    public ExamResponse changeStatus(@PathVariable Long id,
                                     @Valid @RequestBody UpdateExamStatusRequest request,
                                     Principal principal) {
        return examService.changeStatus(id, request.status(), principal.getName());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, Principal principal) {
        examService.deleteExam(id, principal.getName());
    }
}



// RESTful API: phân biệt các HTTP method (POST, GET, PUT, PATCH, DELETE).
// DTO & Validation: dùng record/class để truyền dữ liệu, kết hợp annotation để kiểm tra hợp lệ.
// Dependency Injection: Spring Boot + Lombok giúp inject service dễ dàng.
// ResponseEntity/ResponseStatus: kiểm soát mã HTTP trả về.
// Principal/Security: lấy thông tin user hiện tại từ Spring Security.