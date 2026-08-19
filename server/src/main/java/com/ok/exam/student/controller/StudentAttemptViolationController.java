package com.ok.exam.student.controller;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ok.dto.request.student.RecordAttemptViolationRequest;
import com.ok.dto.response.student.AttemptViolationResponse;
import com.ok.exam.student.service.StudentExamRealtimeService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/student/exam-attempts")
public class StudentAttemptViolationController {

    private final StudentExamRealtimeService realtimeService;

    @PostMapping("/{attemptId}/violations")
    public AttemptViolationResponse recordViolation(
            @PathVariable Long attemptId,
            @Valid @RequestBody RecordAttemptViolationRequest request,
            Authentication authentication
    ) {
        return realtimeService.recordViolation(
                attemptId,
                request,
                authentication.getName()
        );
    }
}
