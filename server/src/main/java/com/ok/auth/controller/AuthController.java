package com.ok.auth.controller;

import com.ok.dto.*;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

import com.ok.auth.service.AuthService;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@AllArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/student/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse registerStudent(@Valid @RequestBody RegisterRequest request) {
        return authService.registerStudent(request);
    }

    @PostMapping("/student/login")
    public AuthResponse loginStudent(@Valid @RequestBody LoginRequest request) {
        return authService.loginStudent(request);
    }

    @PostMapping("teacher/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse registerTeacher(@Valid @RequestBody RegisterRequest request) {
        return authService.registerTeacher(request);
    }

    @PostMapping("teacher/login")
    public AuthResponse loginTeacher(@Valid @RequestBody LoginRequest request) {
        return authService.loginTeacher(request);
    }
}
