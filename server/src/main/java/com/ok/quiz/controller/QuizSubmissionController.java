package com.ok.quiz.controller;

import java.security.Principal;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import com.ok.dto.student.QuizAttemptResponse;
import com.ok.dto.student.QuizResultResponse;
import com.ok.dto.student.SubmitQuizRequest;
import com.ok.quiz.service.QuizSubmissionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/exams/{examId}/quiz-submissions")
@RequiredArgsConstructor
public class QuizSubmissionController {
    private final QuizSubmissionService service;

    @PostMapping("/start")
    @ResponseStatus(HttpStatus.CREATED)
    public QuizAttemptResponse start(@PathVariable Long examId, Principal principal) {
        return service.start(examId, principal.getName());
    }

    @GetMapping("/attempt")
    public QuizAttemptResponse attempt(@PathVariable Long examId, Principal principal) {
        return service.getAttempt(examId, principal.getName());
    }

    @PutMapping("/answers")
    public QuizAttemptResponse saveAnswers(@PathVariable Long examId,
            @Valid @RequestBody SubmitQuizRequest request, Principal principal) {
        return service.saveAnswers(examId, request, principal.getName());
    }

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
