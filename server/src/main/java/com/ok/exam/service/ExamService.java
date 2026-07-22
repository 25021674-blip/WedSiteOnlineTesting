package com.ok.exam.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.ok.dto.CreateExamRequest;
import com.ok.dto.EssayAssignmentFileResponse;
import com.ok.dto.ExamResponse;
import com.ok.dto.ExamStatus;
import com.ok.dto.ExamType;
import com.ok.dto.Role;
import com.ok.dto.UpdateExamRequest;
import com.ok.entity.ExamEntity;
import com.ok.entity.UserEntity;
import com.ok.exam.exception.ExamNotFoundException;
import com.ok.essay.entity.EssayAssignmentFileEntity;
import com.ok.essay.service.FileStorageService;
import com.ok.repository.EssayAssignmentFileRepository;
import com.ok.repository.ExamRepository;
import com.ok.repository.UserRepository;
import com.ok.repository.QuestionRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ExamService {
    private final ExamRepository examRepository;
    private final UserRepository userRepository;
    private final QuestionRepository questionRepository;
    private final EssayAssignmentFileRepository assignmentFileRepository;
    private final FileStorageService storageService;

    @Transactional
    public ExamResponse createExam(CreateExamRequest request, String email) {
        validateTime(request.startTime(), request.deadline());
        UserEntity creator = currentUser(email);
        requireTeacher(creator);
        ExamEntity exam = new ExamEntity(request.title().trim(), normalize(request.description()),
                request.type(), request.startTime(), request.deadline(),
                request.durationMinutes(), creator);
        return toResponse(examRepository.save(exam));
    }

    @Transactional(readOnly = true)
    public List<ExamResponse> getAllExams(String email) {
        UserEntity user = currentUser(email);
        List<ExamEntity> exams;
        if (user.getRole() == Role.ADMIN) {
            exams = examRepository.findAll();
        } else if (user.getRole() == Role.TEACHER) {
            exams = examRepository.findByCreatedByIdOrderByCreatedAtDesc(user.getId());
        } else {
            exams = examRepository.findByStatusOrderByCreatedAtDesc(ExamStatus.PUBLISHED);
        }
        return exams.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ExamResponse getExamById(Long id, String email) {
        ExamEntity exam = findExam(id);
        requireCanView(exam, currentUser(email));
        return toResponse(exam);
    }

    @Transactional
    public ExamResponse updateExam(Long id, UpdateExamRequest request, String email) {
        validateTime(request.startTime(), request.deadline());
        ExamEntity exam = findExam(id);
        requireOwnerOrAdmin(exam, currentUser(email));
        requireDraft(exam);
        exam.update(request.title().trim(), normalize(request.description()), request.startTime(),
                request.deadline(), request.durationMinutes());
        return toResponse(exam);
    }

    @Transactional
    public ExamResponse changeStatus(Long id, ExamStatus status, String email) {
        ExamEntity exam = findExam(id);
        requireOwnerOrAdmin(exam, currentUser(email));
        if (status == ExamStatus.PUBLISHED) validateReadyToPublish(exam);
        exam.changeStatus(status);
        return toResponse(exam);
    }

    @Transactional
    public void deleteExam(Long id, String email) {
        ExamEntity exam = findExam(id);
        requireOwnerOrAdmin(exam, currentUser(email));
        requireDraft(exam);
        questionRepository.deleteByExamId(id);
        assignmentFileRepository.findByExamId(id).ifPresent(assignment -> {
            String path = assignment.getStoragePath();
            assignmentFileRepository.delete(assignment);
            assignmentFileRepository.flush();
            storageService.deleteAssignmentQuietly(path);
        });
        examRepository.delete(exam);
    }

    public ExamEntity findExam(Long id) {
        return examRepository.findById(id).orElseThrow(() -> new ExamNotFoundException(id));
    }

    public UserEntity currentUser(String email) {
        return userRepository.findByEmailIgnoreCase(email).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Không tìm thấy tài khoản"));
    }

    public void requireOwnerOrAdmin(ExamEntity exam, UserEntity user) {
        if (user.getRole() != Role.ADMIN && !exam.getCreatedBy().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền quản lý bài kiểm tra này");
        }
    }

    private void requireTeacher(UserEntity user) {
        if (user.getRole() != Role.TEACHER && user.getRole() != Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Chỉ giáo viên hoặc quản trị viên được tạo đề");
        }
    }

    private void requireCanView(ExamEntity exam, UserEntity user) {
        if (exam.getStatus() == ExamStatus.PUBLISHED || user.getRole() == Role.ADMIN
                || exam.getCreatedBy().getId().equals(user.getId())) return;
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bài kiểm tra chưa được công khai");
    }

    private void requireDraft(ExamEntity exam) {
        if (exam.getStatus() != ExamStatus.DRAFT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Chỉ có thể sửa hoặc xóa đề nháp");
        }
    }

    private void validateTime(java.time.LocalDateTime start, java.time.LocalDateTime deadline) {
        if (!deadline.isAfter(start)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Hạn nộp phải sau thời gian bắt đầu");
        }
    }

    private void validateReadyToPublish(ExamEntity exam) {
        if (exam.getType() == ExamType.ESSAY && !assignmentFileRepository.existsByExamId(exam.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Phải tải file đề tự luận lên trước khi công khai bài kiểm tra");
        }
        if (exam.getType() == ExamType.MULTIPLE_CHOICE && questionRepository.countByExamId(exam.getId()) == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Phải thêm ít nhất một câu hỏi trước khi công khai bài kiểm tra");
        }
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public ExamResponse toResponse(ExamEntity exam) {
        EssayAssignmentFileResponse assignmentFile = assignmentFileRepository.findByExamId(exam.getId())
                .map(this::toAssignmentResponse)
                .orElse(null);
        return new ExamResponse(exam.getId(), exam.getTitle(), exam.getDescription(), exam.getType(),
                exam.getStatus(), exam.getStartTime(), exam.getDeadline(), exam.getDurationMinutes(),
                exam.getCreatedBy().getId(), exam.getCreatedBy().getFullName(), exam.getCreatedAt(),
                assignmentFile);
    }

    private EssayAssignmentFileResponse toAssignmentResponse(EssayAssignmentFileEntity value) {
        return new EssayAssignmentFileResponse(value.getId(), value.getExam().getId(),
                value.getOriginalFileName(), value.getFileSize(), value.getUploadedAt());
    }
}
