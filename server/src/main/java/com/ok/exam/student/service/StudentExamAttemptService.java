package com.ok.exam.student.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.GONE;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.ok.domain.enums.ExamAttemptStatus;
import com.ok.domain.enums.ExamStatus;
import com.ok.domain.enums.ExamType;
import com.ok.domain.enums.QuestionType;
import com.ok.domain.enums.Role;
import com.ok.dto.response.student.StudentExamScreenResponse;
import com.ok.dto.response.student.StudentExamResultResponse;
import com.ok.dto.response.student.StudentExamResultResponse.QuestionResult;
import com.ok.dto.response.student.StudentExamScreenResponse.AnswerInfo;
import com.ok.dto.response.student.StudentExamScreenResponse.ExamInfo;
import com.ok.dto.response.student.StudentExamScreenResponse.OptionInfo;
import com.ok.dto.response.student.StudentExamScreenResponse.Progress;
import com.ok.dto.response.student.StudentExamScreenResponse.QuestionInfo;
import com.ok.dto.response.student.SubmitStudentExamResponse;
import com.ok.entity.ExamAttemptEntity;
import com.ok.entity.ExamEntity;
import com.ok.entity.QuestionEntity;
import com.ok.entity.StudentAnswerEntity;
import com.ok.entity.UserEntity;
import com.ok.repository.StudentAnswerRepository;
import com.ok.repository.EssaySubmissionRepository;
import com.ok.repository.QuizSubmissionRepository;
import com.ok.repository.StudentExamAttemptRepository;
import com.ok.repository.StudentExamRepository;
import com.ok.repository.StudentQuestionRepository;
import com.ok.repository.StudentUserRepository;
import com.ok.exam.service.ExamService;

import lombok.AllArgsConstructor;

@AllArgsConstructor
@Service
public class StudentExamAttemptService {

    private final StudentUserRepository studentUserRepository;
    private final StudentExamRepository examRepository;
    private final StudentExamAttemptRepository attemptRepository;
    private final StudentQuestionRepository questionRepository;
    private final StudentAnswerRepository answerRepository;
    private final ExamService examService;
    private final QuizSubmissionRepository quizSubmissionRepository;
    private final EssaySubmissionRepository essaySubmissionRepository;

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public StudentExamScreenResponse startOrResume(
            Long examId,
            String authenticatedEmail
    ) {
        if (authenticatedEmail == null || authenticatedEmail.isBlank()) {
            throw new ResponseStatusException(
                    UNAUTHORIZED,
                    "Tài khoản chưa được xác thực"
            );
        }

        UserEntity student = studentUserRepository
                .findByEmailForUpdate(authenticatedEmail)
                .orElseThrow(() -> new ResponseStatusException(
                        UNAUTHORIZED,
                        "Tài khoản chưa được xác thực"
                ));

        if (student.getRole() != Role.STUDENT) {
            throw new ResponseStatusException(
                    FORBIDDEN,
                    "Chỉ học sinh được làm bài kiểm tra"
            );
        }

        ExamEntity exam = examRepository
                .findById(examId)
                .orElseThrow(() -> new ResponseStatusException(
                        NOT_FOUND,
                        "Không tìm thấy bài kiểm tra"
                ));

        Instant serverTime = Instant.now();
        validateExamConfiguration(exam, serverTime);
        examService.requireAssignedStudent(exam, student);
        requireCanonicalAttemptFlow(exam, student);

        Optional<ExamAttemptEntity> latestAttempt = attemptRepository
                .findFirstByExam_IdAndStudent_IdOrderByAttemptNumberDesc(
                        exam.getId(),
                        student.getId()
                );

        if (!serverTime.isBefore(exam.getExpiresAt())) {
            latestAttempt
                    .filter(attempt -> attempt.getStatus()
                            == ExamAttemptStatus.IN_PROGRESS)
                    .ifPresent(attempt -> markAutoSubmitted(
                            attempt,
                            serverTime
                    ));

            throw new ResponseStatusException(
                    GONE,
                    "Bài kiểm tra đã kết thúc"
            );
        }

        ExamAttemptEntity attempt;
        if (latestAttempt.isPresent()
                && latestAttempt.get().getStatus()
                    == ExamAttemptStatus.IN_PROGRESS
                && serverTime.isBefore(getEffectiveDeadline(
                        latestAttempt.get()
                ))) {
            attempt = latestAttempt.get();
        } else {
            latestAttempt
                    .filter(existingAttempt -> existingAttempt.getStatus()
                            == ExamAttemptStatus.IN_PROGRESS)
                    .ifPresent(existingAttempt -> markAutoSubmitted(
                            existingAttempt,
                            serverTime
                    ));
            attempt = createAttempt(exam, student, serverTime);
        }

        List<QuestionEntity> questions = questionRepository
                .findByExam_IdOrderByQuestionOrderAsc(exam.getId());

        List<StudentAnswerEntity> answers = answerRepository
                .findByAttempt_Id(attempt.getId());

        return createScreenResponse(
                exam,
                attempt,
                questions,
                answers,
                serverTime
        );
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public StudentExamScreenResponse synchronizeAttempt(
            Long attemptId,
            String authenticatedEmail
    ) {
        validateSynchronizationRequest(
                attemptId,
                authenticatedEmail
        );

        Instant serverTime = Instant.now();
        ExamAttemptEntity attempt = attemptRepository
                .findOwnedByIdForUpdate(
                        attemptId,
                        authenticatedEmail
                )
                .orElseThrow(() -> new ResponseStatusException(
                        NOT_FOUND,
                        "Không tìm thấy lượt làm bài"
                ));

        if (attempt.getStudent().getRole() != Role.STUDENT) {
            throw new ResponseStatusException(
                    FORBIDDEN,
                    "Chỉ học sinh được đồng bộ lượt làm bài"
            );
        }

        examService.requireAssignedStudent(
                attempt.getExam(),
                attempt.getStudent()
        );

        ExamEntity exam = attempt.getExam();
        Instant effectiveDeadline = attempt.getDeadlineAt()
                .isBefore(exam.getExpiresAt())
                ? attempt.getDeadlineAt()
                : exam.getExpiresAt();

        if (attempt.getStatus() == ExamAttemptStatus.IN_PROGRESS
                && (exam.getStatus() != ExamStatus.PUBLISHED
                || !serverTime.isBefore(effectiveDeadline))) {
            attempt.autoSubmit(serverTime);
            attempt = attemptRepository.saveAndFlush(attempt);
        }

        List<QuestionEntity> questions = questionRepository
                .findByExam_IdOrderByQuestionOrderAsc(exam.getId());
        List<StudentAnswerEntity> answers = answerRepository
                .findByAttempt_Id(attempt.getId());

        return createScreenResponse(
                exam,
                attempt,
                questions,
                answers,
                serverTime
        );
    }

    @Transactional
    public SubmitStudentExamResponse submitAttempt(
            Long examId,
            Long attemptId,
            String authenticatedEmail
    ) {
        validateSubmitRequest(
                examId,
                attemptId,
                authenticatedEmail
        );

        ExamAttemptEntity attempt = attemptRepository
                .findOwnedByIdForUpdate(
                        attemptId,
                        authenticatedEmail
                )
                .orElseThrow(() -> new ResponseStatusException(
                        NOT_FOUND,
                        "Không tìm thấy lượt làm bài"
                ));

        validateSubmitOwnership(attempt, examId);
        examService.requireAssignedStudent(
                attempt.getExam(),
                attempt.getStudent()
        );

        Instant serverTime = Instant.now();

        if (attempt.getStatus() != ExamAttemptStatus.IN_PROGRESS) {
            gradeObjectiveAnswers(attempt);
            ExamAttemptEntity savedAttempt = attemptRepository
                    .saveAndFlush(attempt);
            return createSubmitResponse(savedAttempt, serverTime);
        }

        Instant effectiveDeadline = attempt.getDeadlineAt()
                .isBefore(attempt.getExam().getExpiresAt())
                ? attempt.getDeadlineAt()
                : attempt.getExam().getExpiresAt();

        if (attempt.getExam().getStatus() == ExamStatus.PUBLISHED
                && serverTime.isBefore(effectiveDeadline)) {
            attempt.submit(serverTime);
        } else {
            attempt.autoSubmit(serverTime);
        }

        gradeObjectiveAnswers(attempt);

        ExamAttemptEntity savedAttempt = attemptRepository
                .saveAndFlush(attempt);

        return createSubmitResponse(savedAttempt, serverTime);
    }

    @Transactional
    public StudentExamResultResponse getResult(
            Long examId,
            Long attemptId,
            String authenticatedEmail
    ) {
        validateSubmitRequest(examId, attemptId, authenticatedEmail);

        ExamAttemptEntity attempt = attemptRepository
                .findOwnedByIdForUpdate(attemptId, authenticatedEmail)
                .orElseThrow(() -> new ResponseStatusException(
                        NOT_FOUND,
                        "Không tìm thấy lượt làm bài"
                ));

        validateSubmitOwnership(attempt, examId);
        examService.requireAssignedStudent(
                attempt.getExam(),
                attempt.getStudent()
        );
        if (attempt.getStatus() == ExamAttemptStatus.IN_PROGRESS) {
            throw new ResponseStatusException(
                    CONFLICT,
                    "Chỉ xem được kết quả sau khi đã nộp bài"
            );
        }

        gradeObjectiveAnswers(attempt);
        ExamAttemptEntity savedAttempt = attemptRepository
                .saveAndFlush(attempt);
        return createResultResponse(savedAttempt);
    }

    private void validateSubmitRequest(
            Long examId,
            Long attemptId,
            String authenticatedEmail
    ) {
        if (authenticatedEmail == null || authenticatedEmail.isBlank()) {
            throw new ResponseStatusException(
                    UNAUTHORIZED,
                    "Tài khoản chưa được xác thực"
            );
        }

        if (examId == null || examId <= 0
                || attemptId == null || attemptId <= 0) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "Mã bài kiểm tra và lượt làm bài phải lớn hơn 0"
            );
        }
    }

    private void validateSynchronizationRequest(
            Long attemptId,
            String authenticatedEmail
    ) {
        if (authenticatedEmail == null || authenticatedEmail.isBlank()) {
            throw new ResponseStatusException(
                    UNAUTHORIZED,
                    "Tài khoản chưa được xác thực"
            );
        }

        if (attemptId == null || attemptId <= 0) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "Mã lượt làm bài phải lớn hơn 0"
            );
        }
    }

    private void validateSubmitOwnership(
            ExamAttemptEntity attempt,
            Long examId
    ) {
        if (attempt.getStudent().getRole() != Role.STUDENT) {
            throw new ResponseStatusException(
                    FORBIDDEN,
                    "Chỉ học sinh được nộp bài kiểm tra"
            );
        }

        if (!Objects.equals(attempt.getExam().getId(), examId)) {
            throw new ResponseStatusException(
                    NOT_FOUND,
                    "Lượt làm bài không thuộc bài kiểm tra này"
            );
        }
    }

    private SubmitStudentExamResponse createSubmitResponse(
            ExamAttemptEntity attempt,
            Instant serverTime
    ) {
        boolean scoreVisible = attempt.getExam()
                .isShowScoreAfterSubmit();

        return new SubmitStudentExamResponse(
                attempt.getId(),
                attempt.getStatus(),
                attempt.getSubmittedAt(),
                serverTime,
                scoreVisible,
                scoreVisible ? attempt.getScore() : null,
                scoreVisible ? attempt.getExam().getMaxScore() : null
        );
    }

    private void validateExamConfiguration(
            ExamEntity exam,
            Instant serverTime
    ) {
        if (exam.getStatus() != ExamStatus.PUBLISHED) {
            throw new ResponseStatusException(
                    CONFLICT,
                    "Bài kiểm tra chưa được xuất bản"
            );
        }

        if (serverTime.isBefore(exam.getStartAt())) {
            throw new ResponseStatusException(
                    CONFLICT,
                    "Bài kiểm tra chưa đến thời gian bắt đầu"
            );
        }

        if (exam.isTimeLimitEnabled()
                && (exam.getDurationMinutes() == null
                || exam.getDurationMinutes() <= 0)) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "Thời lượng bài kiểm tra không hợp lệ"
            );
        }

        if (exam.getMaxAttempts() < 1) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "Số lần làm bài tối đa không hợp lệ"
            );
        }
    }

    private ExamAttemptEntity validateResumableAttempt(
            ExamAttemptEntity attempt,
            Instant serverTime
    ) {
        if (attempt.getStatus() != ExamAttemptStatus.IN_PROGRESS) {
            throw new ResponseStatusException(
                    CONFLICT,
                    "Học sinh đã hoàn thành lượt làm bài này"
            );
        }

        if (!serverTime.isBefore(attempt.getDeadlineAt())) {
            markAutoSubmitted(attempt, serverTime);

            throw new ResponseStatusException(
                    GONE,
                    "Lượt làm bài đã hết thời gian"
            );
        }

        return attempt;
    }

    private void markAutoSubmitted(
            ExamAttemptEntity attempt,
            Instant submittedAt
    ) {
        attemptRepository.markAutoSubmitted(
                attempt.getId(),
                ExamAttemptStatus.IN_PROGRESS,
                ExamAttemptStatus.AUTO_SUBMITTED,
                submittedAt
        );
    }

    private ExamAttemptEntity createAttempt(
            ExamEntity exam,
            UserEntity student,
            Instant serverTime
    ) {
        long usedAttempts = attemptRepository
                .countByExam_IdAndStudent_Id(
                        exam.getId(),
                        student.getId()
                );

        if (usedAttempts >= exam.getMaxAttempts()) {
            throw new ResponseStatusException(
                    CONFLICT,
                    "Học sinh đã sử dụng hết số lần làm bài"
            );
        }

        int attemptNumber = Math.toIntExact(usedAttempts + 1);
        Instant deadlineAt = exam.getExpiresAt();

        if (exam.isTimeLimitEnabled()) {
            Instant durationDeadline = serverTime.plus(
                    Duration.ofMinutes(exam.getDurationMinutes())
            );

            deadlineAt = durationDeadline.isBefore(exam.getExpiresAt())
                    ? durationDeadline
                    : exam.getExpiresAt();
        }

        ExamAttemptEntity attempt = new ExamAttemptEntity(
                exam,
                student,
                attemptNumber,
                deadlineAt
        );

        return attemptRepository.save(attempt);
    }

    private StudentExamScreenResponse createScreenResponse(
            ExamEntity exam,
            ExamAttemptEntity attempt,
            List<QuestionEntity> questions,
            List<StudentAnswerEntity> answers,
            Instant serverTime
    ) {
        Map<Long, StudentAnswerEntity> answerByQuestionId = answers
                .stream()
                .collect(Collectors.toMap(
                        answer -> answer.getQuestion().getId(),
                        Function.identity(),
                        (firstAnswer, ignoredAnswer) -> firstAnswer
                ));

        List<Long> answeredQuestionIds = questions
                .stream()
                .filter(question -> isAnswered(
                        question,
                        answerByQuestionId.get(question.getId())
                ))
                .map(QuestionEntity::getId)
                .toList();

        List<QuestionInfo> questionResponses = questions
                .stream()
                .map(question -> createQuestionResponse(
                        question,
                        answerByQuestionId.get(question.getId())
                ))
                .toList();

        long remainingSeconds = Math.max(
                0,
                Duration.between(
                        serverTime,
                        getEffectiveDeadline(attempt)
                ).toSeconds()
        );

        int screenExitCount = attempt.getScreenExitCount() == null
                ? 0
                : attempt.getScreenExitCount();

        int attemptNumber = attempt.getAttemptNumber() == null
                ? 1
                : attempt.getAttemptNumber();

        return new StudentExamScreenResponse(
                attempt.getId(),
                attemptNumber,
                exam.getMaxAttempts(),
                Math.max(0, exam.getMaxAttempts() - attemptNumber),
                exam.isTimeLimitEnabled(),
                exam.isRequireFullscreen(),
                exam.isTrackTabSwitches(),
                new ExamInfo(
                        exam.getId(),
                        exam.getTitle(),
                        exam.getDescription(),
                        exam.getType(),
                        exam.getMaxScore()
                ),
                attempt.getStatus(),
                serverTime,
                attempt.getStartedAt(),
                attempt.getDeadlineAt(),
                remainingSeconds,
                screenExitCount,
                new Progress(
                        answeredQuestionIds.size(),
                        questions.size(),
                        answeredQuestionIds
                ),
                questionResponses
        );
    }

    private QuestionInfo createQuestionResponse(
            QuestionEntity question,
            StudentAnswerEntity answer
    ) {
        List<OptionInfo> options = question.getQuestionType()
                == QuestionType.MULTIPLE_CHOICE
                ? question.getOptions()
                        .stream()
                        .map(option -> new OptionInfo(
                                option.getId(),
                                option.getContent()
                        ))
                        .toList()
                : List.of();

        AnswerInfo answerResponse = answer == null
                ? null
                : createAnswerResponse(question, answer);

        return new QuestionInfo(
                question.getId(),
                question.getQuestionOrder(),
                question.getQuestionType(),
                question.getContent(),
                options,
                answerResponse
        );
    }

    private AnswerInfo createAnswerResponse(
            QuestionEntity question,
            StudentAnswerEntity answer
    ) {
        Long selectedOptionId = null;
        String essayContent = null;

        if (question.getQuestionType() == QuestionType.MULTIPLE_CHOICE
                && answer.getSelectedOption() != null) {
            selectedOptionId = answer.getSelectedOption().getId();
        }

        if (question.getQuestionType() == QuestionType.ESSAY) {
            essayContent = answer.getEssayAnswer();
        }

        Long clientRevision = answer.getClientRevision() == null
                ? 0L
                : answer.getClientRevision();

        return new AnswerInfo(
                selectedOptionId,
                essayContent,
                clientRevision
        );
    }

    private void gradeObjectiveAnswers(ExamAttemptEntity attempt) {
        List<QuestionEntity> questions = questionRepository
                .findByExam_IdOrderByQuestionOrderAsc(
                        attempt.getExam().getId()
                );
        List<StudentAnswerEntity> answers = answerRepository
                .findByAttempt_Id(attempt.getId());

        Map<Long, StudentAnswerEntity> answerByQuestionId = answers
                .stream()
                .collect(Collectors.toMap(
                        answer -> answer.getQuestion().getId(),
                        Function.identity(),
                        (firstAnswer, ignoredAnswer) -> firstAnswer
                ));

        BigDecimal objectiveScore = BigDecimal.ZERO;
        boolean needsManualGrading = false;

        for (QuestionEntity question : questions) {
            if (question.getQuestionType() != QuestionType.MULTIPLE_CHOICE) {
                needsManualGrading = true;
                continue;
            }

            StudentAnswerEntity answer = answerByQuestionId.get(
                    question.getId()
            );
            if (answer == null) {
                continue;
            }

            boolean correct = answer.getSelectedOption() != null
                    && answer.getSelectedOption().isCorrect();
            BigDecimal awardedScore = correct
                    ? question.getMaxScore()
                    : BigDecimal.ZERO;

            answer.grade(awardedScore, correct);
            objectiveScore = objectiveScore.add(awardedScore);
        }

        if (!answers.isEmpty()) {
            answerRepository.saveAll(answers);
        }

        if (!needsManualGrading
                && attempt.getExam().getType()
                    == ExamType.MULTIPLE_CHOICE) {
            attempt.assignScore(objectiveScore);
        }
    }

    private void requireCanonicalAttemptFlow(
            ExamEntity exam,
            UserEntity student
    ) {
        boolean hasLegacyAttempt = exam.getType()
                == ExamType.MULTIPLE_CHOICE
                && quizSubmissionRepository.countByExamIdAndStudentId(
                        exam.getId(),
                        student.getId()
                ) > 0
                || exam.getType() == ExamType.ESSAY
                && essaySubmissionRepository.countByExamIdAndStudentId(
                        exam.getId(),
                        student.getId()
                ) > 0;

        if (hasLegacyAttempt) {
            throw new ResponseStatusException(
                    CONFLICT,
                    "Bài kiểm tra đã được bắt đầu bằng luồng làm bài cũ"
            );
        }
    }

    private StudentExamResultResponse createResultResponse(
            ExamAttemptEntity attempt
    ) {
        ExamEntity exam = attempt.getExam();
        boolean scoreVisible = exam.isShowScoreAfterSubmit();
        boolean correctAnswersVisible = canRevealCorrectAnswers(
                exam,
                attempt.getAttemptNumber()
        );

        Map<Long, StudentAnswerEntity> answerByQuestionId = answerRepository
                .findByAttempt_Id(attempt.getId())
                .stream()
                .collect(Collectors.toMap(
                        answer -> answer.getQuestion().getId(),
                        Function.identity(),
                        (firstAnswer, ignoredAnswer) -> firstAnswer
                ));

        List<QuestionResult> questionResults = questionRepository
                .findByExam_IdOrderByQuestionOrderAsc(exam.getId())
                .stream()
                .map(question -> createQuestionResult(
                        question,
                        answerByQuestionId.get(question.getId()),
                        scoreVisible,
                        correctAnswersVisible
                ))
                .toList();

        return new StudentExamResultResponse(
                attempt.getId(),
                attempt.getAttemptNumber(),
                attempt.getStatus(),
                attempt.getSubmittedAt(),
                scoreVisible,
                correctAnswersVisible,
                scoreVisible ? attempt.getScore() : null,
                scoreVisible ? exam.getMaxScore() : null,
                questionResults
        );
    }

    private QuestionResult createQuestionResult(
            QuestionEntity question,
            StudentAnswerEntity answer,
            boolean scoreVisible,
            boolean correctAnswersVisible
    ) {
        Long selectedOptionId = answer == null
                || answer.getSelectedOption() == null
                ? null
                : answer.getSelectedOption().getId();
        String essayContent = answer == null
                || question.getQuestionType() != QuestionType.ESSAY
                ? null
                : answer.getEssayAnswer();

        Long correctOptionId = null;
        Boolean correct = null;
        if (correctAnswersVisible
                && question.getQuestionType()
                    == QuestionType.MULTIPLE_CHOICE) {
            correctOptionId = question.getOptions()
                    .stream()
                    .filter(option -> option.isCorrect())
                    .map(option -> option.getId())
                    .findFirst()
                    .orElse(null);
            correct = answer != null
                    && Boolean.TRUE.equals(answer.getCorrect());
        }

        return new QuestionResult(
                question.getId(),
                question.getQuestionOrder(),
                question.getQuestionType(),
                selectedOptionId,
                essayContent,
                correctOptionId,
                correct,
                scoreVisible
                        && correctAnswersVisible
                        && answer != null
                        ? answer.getScore()
                        : null,
                scoreVisible ? question.getMaxScore() : null
        );
    }

    private Instant getEffectiveDeadline(ExamAttemptEntity attempt) {
        return attempt.getDeadlineAt()
                .isBefore(attempt.getExam().getExpiresAt())
                ? attempt.getDeadlineAt()
                : attempt.getExam().getExpiresAt();
    }

    private boolean canRevealCorrectAnswers(
            ExamEntity exam,
            Integer attemptNumber
    ) {
        if (!exam.isShowCorrectAnswersAfterSubmit()) {
            return false;
        }

        int currentAttempt = attemptNumber == null ? 1 : attemptNumber;
        return currentAttempt >= exam.getMaxAttempts()
                || exam.getStatus() == ExamStatus.CLOSED
                || !Instant.now().isBefore(exam.getExpiresAt());
    }

    private boolean isAnswered(
            QuestionEntity question,
            StudentAnswerEntity answer
    ) {
        if (answer == null) {
            return false;
        }

        if (question.getQuestionType() == QuestionType.MULTIPLE_CHOICE) {
            return answer.getSelectedOption() != null;
        }

        return answer.getEssayAnswer() != null
                && !answer.getEssayAnswer().isBlank();
    }
}
