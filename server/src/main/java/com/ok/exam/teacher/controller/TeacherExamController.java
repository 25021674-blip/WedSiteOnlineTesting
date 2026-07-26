package com.ok.exam.teacher.controller;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ok.dto.response.teacher.TeacherExamSummaryResponse;
import com.ok.exam.teacher.service.TeacherExamService;

import lombok.AllArgsConstructor;

@AllArgsConstructor
@RestController
@RequestMapping("/api/teacher/exams")
public class TeacherExamController {

    private final TeacherExamService teacherExamService;

    @GetMapping
    public List<TeacherExamSummaryResponse> getExamSummaries(
            Authentication authentication
    ) {
        return teacherExamService.getExamSummaries(authentication.getName());
    }
}
