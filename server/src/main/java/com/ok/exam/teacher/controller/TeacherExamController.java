package com.ok.exam.teacher.controller;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ok.dto.request.teacher.UpdateExamConfigurationRequest;
import com.ok.dto.response.teacher.ExamConfigurationResponse;
import com.ok.dto.response.teacher.StudentRecipientCandidateResponse;
import com.ok.dto.response.teacher.TeacherExamDetailResponse;
import com.ok.dto.response.teacher.TeacherExamSubmissionResponse;
import com.ok.dto.response.teacher.TeacherExamSummaryResponse;
import com.ok.dto.response.teacher.TeacherStudentAttemptDetailResponse;
import com.ok.exam.teacher.service.TeacherExamService;

import lombok.AllArgsConstructor;
import jakarta.validation.Valid;

@AllArgsConstructor
@RestController
@RequestMapping("/api/teacher/exams")
public class TeacherExamController {

    private final TeacherExamService teacherExamService;

    @GetMapping("/{examId}/configuration")
    public ExamConfigurationResponse getConfiguration(
            @PathVariable Long examId,
            Authentication authentication
    ) {
        return teacherExamService.getConfiguration(
                examId,
                authentication.getName()
        );
    }

    @PostMapping("/{examId}/publish")
    public ExamConfigurationResponse publishExam(
            @PathVariable Long examId,
            @Valid @RequestBody UpdateExamConfigurationRequest request,
            Authentication authentication
    ) {
        return teacherExamService.publishExam(
                examId,
                request,
                authentication.getName()
        );
    }

    @GetMapping("/{examId}/recipient-candidates")
    public List<StudentRecipientCandidateResponse>
            getRecipientCandidates(
                    @PathVariable Long examId,
                    @RequestParam(defaultValue = "") String query,
                    Authentication authentication
            ) {
        return teacherExamService.getRecipientCandidates(
                examId,
                query,
                authentication.getName()
        );
    }

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
