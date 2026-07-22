package com.ok.essay.service;

import java.time.LocalDateTime;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.ok.dto.EssayAssignmentFileResponse;
import com.ok.dto.ExamStatus;
import com.ok.dto.ExamType;
import com.ok.dto.Role;
import com.ok.entity.ExamEntity;
import com.ok.entity.UserEntity;
import com.ok.essay.entity.EssayAssignmentFileEntity;
import com.ok.essay.service.FileStorageService.StoredFile;
import com.ok.exam.service.ExamService;
import com.ok.repository.EssayAssignmentFileRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EssayAssignmentFileService {
    private final EssayAssignmentFileRepository repository;
    private final ExamService examService;
    private final FileStorageService storageService;

    @Transactional
    public EssayAssignmentFileResponse upload(Long examId, MultipartFile file, String email) {
        ExamEntity exam = examService.findExam(examId);
        requireEssay(exam);
        examService.requireOwnerOrAdmin(exam, examService.currentUser(email));
        requireDraft(exam);

        StoredFile stored = storageService.storeAssignmentPdf(file, examId);
        String oldPath = null;
        try {
            EssayAssignmentFileEntity assignment = repository.findByExamId(examId).orElse(null);
            if (assignment == null) {
                assignment = new EssayAssignmentFileEntity(exam, stored.originalName(), stored.storedName(),
                        stored.path(), stored.size());
            } else {
                oldPath = assignment.getStoragePath();
                assignment.replaceFile(stored.originalName(), stored.storedName(), stored.path(), stored.size());
            }
            EssayAssignmentFileEntity saved = repository.saveAndFlush(assignment);
            if (oldPath != null) storageService.deleteAssignmentQuietly(oldPath);
            return toResponse(saved);
        } catch (RuntimeException exception) {
            storageService.deleteAssignmentQuietly(stored.path());
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public EssayAssignmentFileResponse getInfo(Long examId, String email) {
        ExamEntity exam = examService.findExam(examId);
        requireCanAccess(exam, examService.currentUser(email));
        return toResponse(findByExam(examId));
    }

    @Transactional(readOnly = true)
    public DownloadedAssignment download(Long examId, String email) {
        ExamEntity exam = examService.findExam(examId);
        requireCanAccess(exam, examService.currentUser(email));
        EssayAssignmentFileEntity assignment = findByExam(examId);
        return new DownloadedAssignment(storageService.loadAssignment(assignment.getStoragePath()),
                assignment.getOriginalFileName());
    }

    @Transactional
    public void delete(Long examId, String email) {
        ExamEntity exam = examService.findExam(examId);
        requireEssay(exam);
        examService.requireOwnerOrAdmin(exam, examService.currentUser(email));
        requireDraft(exam);
        EssayAssignmentFileEntity assignment = findByExam(examId);
        String path = assignment.getStoragePath();
        repository.delete(assignment);
        repository.flush();
        storageService.deleteAssignmentQuietly(path);
    }

    private void requireCanAccess(ExamEntity exam, UserEntity user) {
        requireEssay(exam);
        boolean manager = user.getRole() == Role.ADMIN || exam.getCreatedBy().getId().equals(user.getId());
        if (manager) return;
        LocalDateTime now = LocalDateTime.now();
        if (user.getRole() != Role.STUDENT || exam.getStatus() != ExamStatus.PUBLISHED
                || now.isBefore(exam.getStartTime()) || now.isAfter(exam.getDeadline())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Đề tự luận chưa mở, đã kết thúc hoặc bạn không có quyền truy cập");
        }
    }

    private void requireEssay(ExamEntity exam) {
        if (exam.getType() != ExamType.ESSAY) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Đây không phải đề tự luận");
        }
    }

    private void requireDraft(ExamEntity exam) {
        if (exam.getStatus() != ExamStatus.DRAFT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Chỉ được thay đổi file khi đề còn là bản nháp");
        }
    }

    private EssayAssignmentFileEntity findByExam(Long examId) {
        return repository.findByExamId(examId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Bài tự luận chưa có file đề"));
    }

    private EssayAssignmentFileResponse toResponse(EssayAssignmentFileEntity value) {
        return new EssayAssignmentFileResponse(value.getId(), value.getExam().getId(),
                value.getOriginalFileName(), value.getFileSize(), value.getUploadedAt());
    }

    public record DownloadedAssignment(Resource resource, String fileName) {}
}
