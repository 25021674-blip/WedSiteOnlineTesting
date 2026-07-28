package com.ok.exam.student.controller;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ok.dto.response.student.StudentExamScreenResponse;
import com.ok.dto.response.student.SubmitStudentExamResponseDemo;
import com.ok.exam.student.service.StudentExamAttemptService;

import lombok.AllArgsConstructor;

@AllArgsConstructor
@RestController
@RequestMapping("/api/student/exams")
public class StudentExamAttemptController {

    private final StudentExamAttemptService studentExamAttemptService;

    @PostMapping("/{examId}/attempts/start")
    public StudentExamScreenResponse startOrResume(
            @PathVariable Long examId,
            Authentication authentication
    ) {
        return studentExamAttemptService.startOrResume(
                examId,
                authentication.getName()
        );
    }

    @PostMapping("/{examId}/attempts/{attemptId}/submit")
    public SubmitStudentExamResponseDemo submitAttempt(
            @PathVariable Long examId,
            @PathVariable Long attemptId,
            Authentication authentication
    ) {
        return studentExamAttemptService.submitAttempt(
                examId,
                attemptId,
                authentication.getName()
        );
    }
}
