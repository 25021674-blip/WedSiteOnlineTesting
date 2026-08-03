package com.ok.auth.controller;

import com.ok.dto.request.LoginRequest;
import com.ok.dto.request.RegisterRequest;
import com.ok.dto.response.AuthResponse;

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

    @PostMapping("/teacher/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse registerTeacher(@Valid @RequestBody RegisterRequest request) {
        return authService.registerTeacher(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }
}
