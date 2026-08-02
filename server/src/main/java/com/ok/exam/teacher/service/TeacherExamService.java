package com.ok.exam.teacher.service;

import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.ok.domain.enums.Role;
import com.ok.dto.response.teacher.TeacherExamDetailResponse;
import com.ok.dto.response.teacher.TeacherExamSummaryResponse;
import com.ok.entity.UserEntity;
import com.ok.repository.TeacherExamRepository;
import com.ok.repository.UserRepository;

import lombok.AllArgsConstructor;

@AllArgsConstructor
@Service
public class TeacherExamService {

    private final UserRepository userRepository;
    private final TeacherExamRepository teacherExamRepository;

    @Transactional(readOnly = true)
    public List<TeacherExamSummaryResponse> getExamSummaries(
            String authenticatedEmail
    ) {
        UserEntity teacher = userRepository
                .findByEmailIgnoreCase(authenticatedEmail)
                .orElseThrow(() -> new ResponseStatusException(
                        UNAUTHORIZED,
                        "Tài khoản chưa được xác thực"
                ));

        if (teacher.getRole() != Role.TEACHER) {
            throw new ResponseStatusException(
                    FORBIDDEN,
                    "Chỉ giáo viên được xem danh sách bài kiểm tra"
            );
        }

        return teacherExamRepository
                .findSummariesByTeacherId(teacher.getId())
                .stream()
                .map(summary -> new TeacherExamSummaryResponse(
                        summary.getExamId(),
                        summary.getTitle(),
                        summary.getCompletedStudentCount()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public TeacherExamDetailResponse getExamDetail(
            Long examId,
            String authenticatedEmail
    ) {
        UserEntity teacher = userRepository
                .findByEmailIgnoreCase(authenticatedEmail)
                .orElseThrow(() -> new ResponseStatusException(
                        UNAUTHORIZED,
                        "Tài khoản chưa được xác thực"
                ));

        if (teacher.getRole() != Role.TEACHER) {
            throw new ResponseStatusException(
                    FORBIDDEN,
                    "Chỉ giáo viên được xem chi tiết bài kiểm tra"
            );
        }

        return teacherExamRepository
                .findDetailByExamIdAndTeacherId(
                        examId,
                        teacher.getId()
                )
                .map(detail -> new TeacherExamDetailResponse(
                        detail.getExamId(),
                        detail.getTitle(),
                        detail.getType(),
                        detail.getCompletedStudentCount(),
                        detail.getCreatedAt(),
                        detail.getExpiresAt(),
                        detail.getDurationMinutes(),
                        detail.getMaxScore()
                ))
                .orElseThrow(() -> new ResponseStatusException(
                        NOT_FOUND,
                        "Không tìm thấy bài kiểm tra"
                ));
    }
}
