package com.ok.essay.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.ok.dto.EssaySubmissionResponse;
import com.ok.dto.ExamStatus;
import com.ok.dto.ExamType;
import com.ok.dto.GradeEssayRequest;
import com.ok.dto.Role;
import com.ok.entity.ExamEntity;
import com.ok.entity.UserEntity;
import com.ok.essay.entity.EssaySubmissionEntity;
import com.ok.essay.service.FileStorageService.StoredFile;
import com.ok.exam.service.ExamService;
import com.ok.repository.EssaySubmissionRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EssaySubmissionService {
    private final EssaySubmissionRepository repository;
    private final ExamService examService;
    private final FileStorageService storageService;

    @Transactional
    public EssaySubmissionResponse submit(Long examId, MultipartFile file, String email) {
        ExamEntity exam = examService.findExam(examId);
        UserEntity student = examService.currentUser(email);
        requireStudent(student);
        requireOpenEssay(exam);
        if (repository.existsByExamIdAndStudentId(examId, student.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bạn đã nộp bài tự luận này");
        }
        StoredFile stored = storageService.storeSubmissionPdf(file, examId, student.getId());
        try {
            EssaySubmissionEntity submission = new EssaySubmissionEntity(exam, student,
                    stored.originalName(), stored.storedName(), stored.path(), stored.size());
            return toResponse(repository.save(submission));
        } catch (RuntimeException exception) {
            storageService.deleteSubmissionQuietly(stored.path());
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public EssaySubmissionResponse getMine(Long examId, String email) {
        UserEntity user = examService.currentUser(email);
        return repository.findByExamIdAndStudentId(examId, user.getId())
                .map(this::toResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bạn chưa nộp bài này"));
    }

    @Transactional(readOnly = true)
    public List<EssaySubmissionResponse> getForExam(Long examId, String email) {
        ExamEntity exam = examService.findExam(examId);
        examService.requireOwnerOrAdmin(exam, examService.currentUser(email));
        return repository.findByExamIdOrderBySubmittedAtDesc(examId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public EssaySubmissionResponse grade(Long id, GradeEssayRequest request, String email) {
        EssaySubmissionEntity submission = find(id);
        examService.requireOwnerOrAdmin(submission.getExam(), examService.currentUser(email));
        submission.grade(request.score(), normalize(request.feedback()));
        return toResponse(submission);
    }

    @Transactional(readOnly = true)
    public DownloadedSubmission download(Long id, String email) {
        EssaySubmissionEntity submission = find(id);
        UserEntity user = examService.currentUser(email);
        boolean ownSubmission = submission.getStudent().getId().equals(user.getId());
        boolean managesExam = user.getRole() == Role.ADMIN
                || submission.getExam().getCreatedBy().getId().equals(user.getId());
        if (!ownSubmission && !managesExam) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền tải file này");
        }
        return new DownloadedSubmission(storageService.loadSubmission(submission.getStoragePath()),
                submission.getOriginalFileName());
    }

    private EssaySubmissionEntity find(Long id) {
        return repository.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy bài tự luận đã nộp"));
    }

    private void requireOpenEssay(ExamEntity exam) {
        if (exam.getType() != ExamType.ESSAY) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Đây không phải đề tự luận");
        LocalDateTime now = LocalDateTime.now();
        if (exam.getStatus() != ExamStatus.PUBLISHED || now.isBefore(exam.getStartTime()) || now.isAfter(exam.getDeadline())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bài kiểm tra chưa mở hoặc đã hết hạn nộp");
        }
    }

    private void requireStudent(UserEntity user) {
        if (user.getRole() != Role.STUDENT) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Chỉ học sinh được nộp bài");
    }

    private String normalize(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    private EssaySubmissionResponse toResponse(EssaySubmissionEntity value) {
        return new EssaySubmissionResponse(value.getId(), value.getExam().getId(), value.getStudent().getId(),
                value.getStudent().getFullName(), value.getOriginalFileName(), value.getFileSize(),
                value.getSubmittedAt(), value.getScore(), value.getFeedback());
    }

    public record DownloadedSubmission(Resource resource, String fileName) {}
}
