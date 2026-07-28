package com.ok.exam.student.controller;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ok.dto.request.student.SaveStudentAnswerRequest;
import com.ok.dto.response.student.SaveStudentAnswerResponse;
import com.ok.exam.student.service.StudentAnswerSaveService;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

@AllArgsConstructor
@RestController
@RequestMapping("/api/student/exam-attempts")
public class StudentAnswerSaveController {

    private final StudentAnswerSaveService studentAnswerSaveService;

    @PutMapping("/{attemptId}/questions/{questionId}/answer")
    public SaveStudentAnswerResponse saveAnswer(
            @PathVariable Long attemptId,
            @PathVariable Long questionId,
            @Valid @RequestBody SaveStudentAnswerRequest request,
            Authentication authentication
    ) {
        return studentAnswerSaveService.saveAnswer(
                attemptId,
                questionId,
                request,
                authentication.getName()
        );
    }
}
