package com.ok.quiz.controller;

import java.security.Principal;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import com.ok.dto.QuizResultResponse;
import com.ok.dto.SubmitQuizRequest;
import com.ok.quiz.service.QuizSubmissionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/exams/{examId}/quiz-submissions")
@RequiredArgsConstructor
public class QuizSubmissionController {
    private final QuizSubmissionService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public QuizResultResponse submit(@PathVariable Long examId,
            @Valid @RequestBody SubmitQuizRequest request, Principal principal) {
        return service.submit(examId, request, principal.getName());
    }

    @GetMapping("/me")
    public QuizResultResponse mine(@PathVariable Long examId, Principal principal) {
        return service.getMine(examId, principal.getName());
    }
}
