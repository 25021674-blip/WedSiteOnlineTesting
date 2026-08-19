package com.ok.exam.teacher.service;

import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.ok.domain.enums.Role;
import com.ok.domain.enums.ExamStatus;
import com.ok.dto.request.teacher.UpdateExamConfigurationRequest;
import com.ok.dto.response.teacher.ExamConfigurationResponse;
import com.ok.dto.response.teacher.ExamConfigurationResponse.Recipient;
import com.ok.dto.response.teacher.StudentRecipientCandidateResponse;
import com.ok.dto.response.teacher.TeacherExamDetailResponse;
import com.ok.dto.response.teacher.TeacherExamSubmissionResponse;
import com.ok.dto.response.teacher.TeacherExamSummaryResponse;
import com.ok.dto.response.teacher.TeacherStudentAttemptDetailResponse;
import com.ok.dto.response.teacher.TeacherStudentAttemptDetailResponse.AnswerDetail;
import com.ok.dto.response.teacher.TeacherStudentAttemptDetailResponse.OptionDetail;
import com.ok.dto.response.teacher.TeacherStudentAttemptDetailResponse.QuestionDetail;
import com.ok.entity.ExamAttemptEntity;
import com.ok.entity.ExamEntity;
import com.ok.entity.ExamRecipientEntity;
import com.ok.entity.QuestionEntity;
import com.ok.entity.QuestionOptionEntity;
import com.ok.entity.StudentAnswerEntity;
import com.ok.entity.UserEntity;
import com.ok.repository.StudentAnswerRepository;
import com.ok.repository.ExamRecipientRepositoryDemo;
import com.ok.repository.StudentExamAttemptRepository;
import com.ok.repository.StudentQuestionRepository;
import com.ok.repository.TeacherExamRepository;
import com.ok.repository.UserRepository;
import com.ok.exam.service.ExamService;

import lombok.AllArgsConstructor;

@AllArgsConstructor
@Service
public class TeacherExamService {

    private final UserRepository userRepository;
    private final TeacherExamRepository teacherExamRepository;
    private final StudentExamAttemptRepository studentExamAttemptRepository;
    private final StudentQuestionRepository studentQuestionRepository;
    private final StudentAnswerRepository studentAnswerRepository;
    private final ExamService examService;
    private final ExamRecipientRepositoryDemo examRecipientRepository;

    @Transactional(readOnly = true)
    public ExamConfigurationResponse getConfiguration(
            Long examId,
            String authenticatedEmail
    ) {
        ExamEntity exam = findManagedExam(
                examId,
                authenticatedEmail
        );
        return createConfigurationResponse(exam);
    }

    @Transactional
    public ExamConfigurationResponse updateConfiguration(
            Long examId,
            UpdateExamConfigurationRequest request,
            String authenticatedEmail
    ) {
        ExamEntity exam = findManagedExamForUpdate(
                examId,
                authenticatedEmail
        );
        requireDraft(exam);

        if (request == null || request.recipientStudentIds() == null) {
            throw new ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Cấu hình bài kiểm tra không hợp lệ"
            );
        }

        if (Boolean.TRUE.equals(request.timeLimitEnabled())
                && (exam.getDurationMinutes() == null
                    || exam.getDurationMinutes() <= 0)) {
            throw new ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Bài kiểm tra phải có thời lượng hợp lệ khi bật giới hạn thời gian"
            );
        }

        Set<Long> requestedStudentIds = new HashSet<>(
                request.recipientStudentIds()
        );
        List<UserEntity> requestedStudents = userRepository
                .findAllById(requestedStudentIds);

        if (requestedStudents.size() != requestedStudentIds.size()) {
            throw new ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Danh sách có mã học sinh không tồn tại"
            );
        }

        boolean containsNonStudent = requestedStudents.stream()
                .anyMatch(user -> user.getRole() != Role.STUDENT);
        if (containsNonStudent) {
            throw new ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Chỉ tài khoản học sinh mới được nhận bài kiểm tra"
            );
        }

        exam.updateConfiguration(
                request.showCorrectAnswersAfterSubmit(),
                request.showScoreAfterSubmit(),
                request.maxAttempts(),
                request.timeLimitEnabled(),
                request.requireFullscreen(),
                request.trackTabSwitches()
        );

        replaceRecipients(exam, requestedStudents, requestedStudentIds);
        return createConfigurationResponse(exam);
    }

    @Transactional(readOnly = true)
    public List<StudentRecipientCandidateResponse> getRecipientCandidates(
            Long examId,
            String query,
            String authenticatedEmail
    ) {
        findManagedExam(examId, authenticatedEmail);

        Set<Long> selectedStudentIds = examRecipientRepository
                .findByExam_IdOrderByStudent_FullNameAscStudent_IdAsc(examId)
                .stream()
                .map(recipient -> recipient.getStudent().getId())
                .collect(java.util.stream.Collectors.toSet());

        String normalizedQuery = query == null
                ? ""
                : query.trim().toLowerCase(Locale.ROOT);

        return userRepository
                .findByRoleOrderByFullNameAscIdAsc(Role.STUDENT)
                .stream()
                .filter(student -> matchesQuery(student, normalizedQuery))
                .map(student -> new StudentRecipientCandidateResponse(
                        student.getId(),
                        student.getFullName(),
                        student.getEmail(),
                        selectedStudentIds.contains(student.getId())
                ))
                .toList();
    }

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

    @Transactional(readOnly = true)
    public List<TeacherExamSubmissionResponse> getExamSubmissions(
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
                    "Chỉ giáo viên được xem danh sách bài nộp"
            );
        }

        teacherExamRepository
                .findDetailByExamIdAndTeacherId(
                        examId,
                        teacher.getId()
                )
                .orElseThrow(() -> new ResponseStatusException(
                        NOT_FOUND,
                        "Không tìm thấy bài kiểm tra"
                ));

        return teacherExamRepository
                .findSubmissionsByExamIdAndTeacherId(
                        examId,
                        teacher.getId()
                )
                .stream()
                .map(submission -> new TeacherExamSubmissionResponse(
                        submission.getAttemptId(),
                        submission.getAttemptNumber(),
                        submission.getStudentId(),
                        submission.getStudentName()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public TeacherStudentAttemptDetailResponse getStudentAttemptDetail(
            Long examId,
            Long attemptId,
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
                    "Chỉ giáo viên được xem bài làm của học sinh"
            );
        }

        ExamAttemptEntity attempt = studentExamAttemptRepository
                .findSubmittedForTeacherReview(
                        examId,
                        attemptId,
                        teacher.getId()
                )
                .orElseThrow(() -> new ResponseStatusException(
                        NOT_FOUND,
                        "Không tìm thấy bài làm đã nộp"
                ));

        List<QuestionEntity> questions = studentQuestionRepository
                .findByExam_IdOrderByQuestionOrderAsc(examId);
        List<StudentAnswerEntity> answers = studentAnswerRepository
                .findByAttempt_Id(attemptId);

        Map<Long, StudentAnswerEntity> answerByQuestionId = new HashMap<>();
        for (StudentAnswerEntity answer : answers) {
            answerByQuestionId.put(
                    answer.getQuestion().getId(),
                    answer
            );
        }

        List<QuestionDetail> questionDetails = questions
                .stream()
                .map(question -> createQuestionDetail(
                        question,
                        answerByQuestionId.get(question.getId())
                ))
                .toList();

        int screenExitCount = attempt.getScreenExitCount() == null
                ? 0
                : attempt.getScreenExitCount();

        return new TeacherStudentAttemptDetailResponse(
                attempt.getId(),
                attempt.getAttemptNumber(),
                attempt.getExam().getTitle(),
                attempt.getStudent().getId(),
                attempt.getStudent().getFullName(),
                attempt.getStartedAt(),
                attempt.getSubmittedAt(),
                screenExitCount,
                attempt.getScore(),
                attempt.getExam().getMaxScore(),
                questionDetails
        );
    }

    private QuestionDetail createQuestionDetail(
            QuestionEntity question,
            StudentAnswerEntity answer
    ) {
        List<OptionDetail> options = question.getOptions()
                .stream()
                .map(this::createOptionDetail)
                .toList();

        AnswerDetail answerDetail = answer == null
                ? null
                : createAnswerDetail(answer);

        return new QuestionDetail(
                question.getId(),
                question.getQuestionOrder(),
                question.getQuestionType(),
                question.getContent(),
                question.getMaxScore(),
                options,
                answerDetail
        );
    }

    private OptionDetail createOptionDetail(
            QuestionOptionEntity option
    ) {
        return new OptionDetail(
                option.getId(),
                option.getContent(),
                option.isCorrect(),
                option.getOptionOrder()
        );
    }

    private AnswerDetail createAnswerDetail(
            StudentAnswerEntity answer
    ) {
        Long selectedOptionId = answer.getSelectedOption() == null
                ? null
                : answer.getSelectedOption().getId();

        return new AnswerDetail(
                selectedOptionId,
                answer.getEssayAnswer(),
                answer.getScore(),
                answer.getCorrect()
        );
    }

    private ExamEntity findManagedExam(
            Long examId,
            String authenticatedEmail
    ) {
        ExamEntity exam = examService.findExam(examId);
        UserEntity user = examService.currentUser(authenticatedEmail);
        examService.requireOwnerOrAdmin(exam, user);
        return exam;
    }

    private ExamEntity findManagedExamForUpdate(
            Long examId,
            String authenticatedEmail
    ) {
        ExamEntity exam = examService.findExamForUpdate(examId);
        UserEntity user = examService.currentUser(authenticatedEmail);
        examService.requireOwnerOrAdmin(exam, user);
        return exam;
    }

    private void requireDraft(ExamEntity exam) {
        if (exam.getStatus() != ExamStatus.DRAFT) {
            throw new ResponseStatusException(
                    org.springframework.http.HttpStatus.CONFLICT,
                    "Chỉ được thay đổi cấu hình khi bài kiểm tra còn là bản nháp"
            );
        }
    }

    private void replaceRecipients(
            ExamEntity exam,
            List<UserEntity> requestedStudents,
            Set<Long> requestedStudentIds
    ) {
        List<ExamRecipientEntity> existingRecipients = examRecipientRepository
                .findByExam_IdOrderByStudent_FullNameAscStudent_IdAsc(
                        exam.getId()
                );

        List<ExamRecipientEntity> removedRecipients = existingRecipients
                .stream()
                .filter(recipient -> !requestedStudentIds.contains(
                        recipient.getStudent().getId()
                ))
                .toList();

        if (!removedRecipients.isEmpty()) {
            examRecipientRepository.deleteAll(removedRecipients);
            examRecipientRepository.flush();
        }

        Set<Long> existingStudentIds = existingRecipients
                .stream()
                .map(recipient -> recipient.getStudent().getId())
                .collect(java.util.stream.Collectors.toSet());

        List<ExamRecipientEntity> addedRecipients = requestedStudents
                .stream()
                .filter(student -> !existingStudentIds.contains(
                        student.getId()
                ))
                .map(student -> new ExamRecipientEntity(exam, student))
                .toList();

        if (!addedRecipients.isEmpty()) {
            examRecipientRepository.saveAllAndFlush(addedRecipients);
        }
    }

    private ExamConfigurationResponse createConfigurationResponse(
            ExamEntity exam
    ) {
        List<Recipient> recipients = examRecipientRepository
                .findByExam_IdOrderByStudent_FullNameAscStudent_IdAsc(
                        exam.getId()
                )
                .stream()
                .map(recipient -> new Recipient(
                        recipient.getStudent().getId(),
                        recipient.getStudent().getFullName(),
                        recipient.getStudent().getEmail(),
                        recipient.getAssignedAt()
                ))
                .toList();

        return new ExamConfigurationResponse(
                exam.getId(),
                exam.getStatus(),
                exam.isShowCorrectAnswersAfterSubmit(),
                exam.isShowScoreAfterSubmit(),
                exam.getMaxAttempts(),
                exam.isTimeLimitEnabled(),
                exam.isRequireFullscreen(),
                exam.isTrackTabSwitches(),
                recipients
        );
    }

    private boolean matchesQuery(
            UserEntity student,
            String normalizedQuery
    ) {
        if (normalizedQuery.isEmpty()) {
            return true;
        }

        return student.getFullName()
                .toLowerCase(Locale.ROOT)
                .contains(normalizedQuery)
                || student.getEmail()
                .toLowerCase(Locale.ROOT)
                .contains(normalizedQuery);
    }
}
