package com.ok.controller;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ok.dto.TeacherExamSummaryResponseDemo;
import com.ok.service.TeacherExamServiceDemo;

import lombok.AllArgsConstructor;

@AllArgsConstructor
@RestController
@RequestMapping("/api/teacher/exams")
public class TeacherExamControllerDemo {

    private final TeacherExamServiceDemo teacherExamService;

    @GetMapping
    public List<TeacherExamSummaryResponseDemo> getExamSummaries(
            Authentication authentication
    ) {
        return teacherExamService.getExamSummaries(authentication.getName());
    }
}
