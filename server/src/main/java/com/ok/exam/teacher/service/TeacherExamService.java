package com.ok.service;

import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.ok.dto.Role;
import com.ok.dto.TeacherExamSummaryResponseDemo;
import com.ok.entity.UserEntity;
import com.ok.repository.TeacherExamRepositoryDemo;
import com.ok.repository.UserRepository;

import lombok.AllArgsConstructor;

@AllArgsConstructor
@Service
public class TeacherExamServiceDemo {

    private final UserRepository userRepository;
    private final TeacherExamRepositoryDemo teacherExamRepository;

    @Transactional(readOnly = true)
    public List<TeacherExamSummaryResponseDemo> getExamSummaries(
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
                .map(summary -> new TeacherExamSummaryResponseDemo(
                        summary.getTitle(),
                        summary.getCompletedStudentCount()
                ))
                .toList();
    }
}
