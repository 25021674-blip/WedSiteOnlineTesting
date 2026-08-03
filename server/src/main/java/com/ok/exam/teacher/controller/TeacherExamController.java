package com.ok.exam.teacher.controller;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ok.dto.response.teacher.TeacherExamDetailResponse;
import com.ok.dto.response.teacher.TeacherExamSubmissionResponse;
import com.ok.dto.response.teacher.TeacherExamSummaryResponse;
import com.ok.dto.response.teacher.TeacherStudentAttemptDetailResponse;
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

    @GetMapping("/{examId}")
    public TeacherExamDetailResponse getExamDetail(
            @PathVariable Long examId,
            Authentication authentication
    ) {
        return teacherExamService.getExamDetail(
                examId,
                authentication.getName()
        );
    }

    @GetMapping("/{examId}/submissions")
    public List<TeacherExamSubmissionResponse> getExamSubmissions(
            @PathVariable Long examId,
            Authentication authentication
    ) {
        return teacherExamService.getExamSubmissions(
                examId,
                authentication.getName()
        );
    }

    @GetMapping("/{examId}/attempts/{attemptId}")
    public TeacherStudentAttemptDetailResponse getStudentAttemptDetail(
            @PathVariable Long examId,
            @PathVariable Long attemptId,
            Authentication authentication
    ) {
        return teacherExamService.getStudentAttemptDetail(
                examId,
                attemptId,
                authentication.getName()
        );
    }
}
