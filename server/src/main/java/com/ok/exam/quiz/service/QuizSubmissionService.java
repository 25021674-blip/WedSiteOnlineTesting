package com.ok.exam.quiz.service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.ok.domain.enums.ExamAttemptStatus;
import com.ok.domain.enums.ExamStatus;
import com.ok.domain.enums.ExamType;
import com.ok.domain.enums.Role;
import com.ok.dto.request.SelectedAnswerRequest;
import com.ok.dto.request.SubmitQuizRequest;
import com.ok.dto.response.QuizAnswerResultResponse;
import com.ok.dto.response.QuizAttemptResponse;
import com.ok.dto.response.QuizResultResponse;
import com.ok.entity.QuestionOptionEntity;
import com.ok.entity.ExamEntity;
import com.ok.entity.QuestionEntity;
import com.ok.entity.QuizSubmissionAnswerEntity;
import com.ok.entity.QuizSubmissionEntity;
import com.ok.entity.UserEntity;
import com.ok.exam.service.ExamService;
import com.ok.repository.QuestionRepository;
import com.ok.repository.QuizSubmissionAnswerRepository;
import com.ok.repository.QuizSubmissionRepository;
import com.ok.repository.StudentExamAttemptRepository;
import com.ok.repository.StudentUserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class QuizSubmissionService {
    private final QuizSubmissionRepository submissionRepository;
    private final QuizSubmissionAnswerRepository answerRepository;
    private final QuestionRepository questionRepository;
    private final ExamService examService;
    private final StudentExamAttemptRepository studentExamAttemptRepository;
    private final StudentUserRepository studentUserRepository;

    @Transactional
    public QuizAttemptResponse start(Long examId, String email) {
        ExamEntity exam = examService.findExam(examId);
        UserEntity student = studentUserRepository
                .findByEmailForUpdate(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Không tìm thấy tài khoản"
                ));
        requireQuizCanStart(exam, student);

        QuizSubmissionEntity existing = submissionRepository
                .findFirstByExamIdAndStudentIdOrderByAttemptNumberDesc(
                        examId,
                        student.getId()
                ).orElse(null);
        if (existing != null) {
            finalizeIfExpired(existing, LocalDateTime.now());
            if (existing.getStatus() == ExamAttemptStatus.IN_PROGRESS) {
                return attemptResponse(existing);
            }
        }

        long usedAttempts = submissionRepository
                .countByExamIdAndStudentId(examId, student.getId());
        if (usedAttempts >= exam.getMaxAttempts()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Bạn đã sử dụng hết số lần làm bài"
            );
        }

        LocalDateTime startedAt = LocalDateTime.now();
        LocalDateTime expiresAt = exam.getDeadline();
        if (exam.isTimeLimitEnabled()) {
            LocalDateTime durationEnd = startedAt.plusMinutes(
                    exam.getDurationMinutes()
            );
            expiresAt = durationEnd.isBefore(exam.getDeadline())
                    ? durationEnd
                    : exam.getDeadline();
        }

        QuizSubmissionEntity attempt = submissionRepository.save(
                new QuizSubmissionEntity(
                        exam,
                        student,
                        Math.toIntExact(usedAttempts + 1),
                        startedAt,
                        expiresAt
                ));
        return attemptResponse(attempt);
    }

    @Transactional
    public QuizAttemptResponse getAttempt(Long examId, String email) {
        UserEntity student = examService.currentUser(email);
        requireStudent(student);
        QuizSubmissionEntity attempt = findAttempt(examId, student.getId());
        finalizeIfExpired(attempt, LocalDateTime.now());
        return attemptResponse(attempt);
    }

    @Transactional
    public QuizAttemptResponse saveAnswers(Long examId, SubmitQuizRequest request, String email) {
        UserEntity student = examService.currentUser(email);
        requireStudent(student);
        QuizSubmissionEntity attempt = findAttempt(examId, student.getId());
        requireInProgressAndNotExpired(attempt);
        saveSelectedAnswers(attempt, request.answers());
        return attemptResponse(attempt);
    }

    @Transactional
    public QuizResultResponse submit(Long examId, SubmitQuizRequest request, String email) {
        UserEntity student = examService.currentUser(email);
        requireStudent(student);
        QuizSubmissionEntity attempt = findAttempt(examId, student.getId());
        if (attempt.getStatus() != ExamAttemptStatus.IN_PROGRESS) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bài làm đã được kết thúc trước đó");
        }

        LocalDateTime now = LocalDateTime.now();
        if (!now.isBefore(attempt.getExpiresAt())) {
            finalizeAttempt(attempt, true, now);
            return resultResponse(attempt);
        }

        saveSelectedAnswers(attempt, request.answers());
        finalizeAttempt(attempt, false, now);
        return resultResponse(attempt);
    }

    @Transactional
    public QuizResultResponse getMine(Long examId, String email) {
        UserEntity student = examService.currentUser(email);
        requireStudent(student);
        QuizSubmissionEntity attempt = findAttempt(examId, student.getId());
        finalizeIfExpired(attempt, LocalDateTime.now());
        if (attempt.getStatus() == ExamAttemptStatus.IN_PROGRESS) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bài làm chưa được nộp");
        }
        return resultResponse(attempt);
    }

    @Transactional
    public void requireActiveAttempt(Long examId, UserEntity student) {
        requireInProgressAndNotExpired(findAttempt(examId, student.getId()));
    }

    @Scheduled(fixedDelayString = "${app.quiz.expiration-check-ms:10000}")
    @Transactional
    public void autoSubmitExpiredAttempts() {
        List<QuizSubmissionEntity> expired = submissionRepository
                .findByStatusAndExpiresAtLessThanEqualOrderByExpiresAtAsc(
                        ExamAttemptStatus.IN_PROGRESS, LocalDateTime.now(), PageRequest.of(0, 200));
        LocalDateTime now = LocalDateTime.now();
        expired.forEach(attempt -> finalizeAttempt(attempt, true, now));
    }

    private void saveSelectedAnswers(QuizSubmissionEntity attempt, List<SelectedAnswerRequest> answers) {
        Map<Long, SelectedAnswerRequest> selected = indexAnswers(answers);
        Map<Long, QuestionEntity> questions = questionRepository
                .findByExamIdOrderById(attempt.getExam().getId()).stream()
                .collect(Collectors.toMap(QuestionEntity::getId, question -> question));

        for (SelectedAnswerRequest selectedAnswer : selected.values()) {
            QuestionEntity question = questions.get(selectedAnswer.questionId());
            if (question == null) throw invalid("Đáp án không thuộc đề trắc nghiệm này");
            QuestionOptionEntity option = question.getOptions().stream()
                    .filter(value -> value.getId().equals(selectedAnswer.selectedOptionId()))
                    .findFirst()
                    .orElseThrow(() -> invalid("Lựa chọn không thuộc câu hỏi " + question.getId()));
            boolean correct = option.isCorrect();
            double awardedPoints = correct ? question.getMaxScore().doubleValue() : 0;
            QuizSubmissionAnswerEntity saved = answerRepository
                    .findBySubmissionIdAndQuestionId(attempt.getId(), question.getId()).orElse(null);
            if (saved == null) {
                answerRepository.save(new QuizSubmissionAnswerEntity(
                        attempt, question, option, correct, awardedPoints));
            } else {
                saved.select(option, correct, awardedPoints);
            }
        }
    }

    private Map<Long, SelectedAnswerRequest> indexAnswers(List<SelectedAnswerRequest> answers) {
        Map<Long, SelectedAnswerRequest> result = new HashMap<>();
        Set<Long> seen = new HashSet<>();
        for (SelectedAnswerRequest answer : answers) {
            if (!seen.add(answer.questionId())) {
                throw invalid("Một câu hỏi không được gửi nhiều lần trong cùng request");
            }
            result.put(answer.questionId(), answer);
        }
        return result;
    }

    private void finalizeIfExpired(QuizSubmissionEntity attempt, LocalDateTime now) {
        if (attempt.getStatus() == ExamAttemptStatus.IN_PROGRESS
                && (attempt.getExam().getStatus() != ExamStatus.PUBLISHED
                || !now.isBefore(attempt.getExpiresAt()))) {
            finalizeAttempt(attempt, true, now);
        }
    }

    private void finalizeAttempt(QuizSubmissionEntity attempt, boolean automatic, LocalDateTime now) {
        if (attempt.getStatus() != ExamAttemptStatus.IN_PROGRESS) return;
        List<QuestionEntity> questions = questionRepository.findByExamIdOrderById(attempt.getExam().getId());
        double totalPoints = questions.stream()
                .mapToDouble(question -> question.getMaxScore().doubleValue())
                .sum();
        double score = answerRepository.findBySubmissionIdOrderById(attempt.getId()).stream()
                .mapToDouble(QuizSubmissionAnswerEntity::getAwardedPoints).sum();
        if (automatic) attempt.autoSubmit(score, totalPoints);
        else attempt.submit(score, totalPoints, now);
    }

    private QuizSubmissionEntity findAttempt(Long examId, Long studentId) {
        QuizSubmissionEntity attempt = submissionRepository
                .findFirstByExamIdAndStudentIdOrderByAttemptNumberDesc(
                        examId,
                        studentId
                )
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Bạn chưa bắt đầu bài trắc nghiệm này"));
        examService.requireAssignedStudent(
                attempt.getExam(),
                attempt.getStudent()
        );
        return attempt;
    }

    private void requireQuizCanStart(ExamEntity exam, UserEntity student) {
        requireStudent(student);
        examService.requireAssignedStudent(exam, student);
        if (studentExamAttemptRepository.countByExam_IdAndStudent_Id(
                exam.getId(),
                student.getId()
        ) > 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Bài kiểm tra đã được bắt đầu bằng luồng làm bài mới"
            );
        }
        if (exam.isRequireFullscreen() || exam.isTrackTabSwitches()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Bài kiểm tra có giám sát phải dùng API làm bài mới"
            );
        }
        if (exam.getType() != ExamType.MULTIPLE_CHOICE) throw invalid("Đây không phải đề trắc nghiệm");
        if (exam.isTimeLimitEnabled()
                && (exam.getDurationMinutes() == null
                || exam.getDurationMinutes() <= 0)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Đề chưa có thời lượng làm bài hợp lệ");
        }
        LocalDateTime now = LocalDateTime.now();
        if (exam.getStatus() != ExamStatus.PUBLISHED
                || now.isBefore(exam.getStartTime()) || !now.isBefore(exam.getDeadline())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bài kiểm tra chưa mở hoặc đã kết thúc");
        }
    }

    private void requireStudent(UserEntity student) {
        if (student.getRole() != Role.STUDENT) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Chỉ học sinh được làm bài");
        }
    }

    private void requireInProgressAndNotExpired(QuizSubmissionEntity attempt) {
        if (attempt.getStatus() != ExamAttemptStatus.IN_PROGRESS) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bài làm đã kết thúc");
        }
        LocalDateTime now = LocalDateTime.now();
        if (attempt.getExam().getStatus() != ExamStatus.PUBLISHED
                || !now.isBefore(attempt.getExpiresAt())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bài làm đã hết thời gian");
        }
    }

    private QuizAttemptResponse attemptResponse(QuizSubmissionEntity attempt) {
        return new QuizAttemptResponse(attempt.getId(), attempt.getAttemptNumber(),
                attempt.getExam().getId(), attempt.getStudent().getId(),
                attempt.getStatus(), attempt.getStartedAt(), attempt.getExpiresAt(),
                attempt.getSubmittedAt(), LocalDateTime.now());
    }

    private QuizResultResponse resultResponse(QuizSubmissionEntity attempt) {
        List<QuizSubmissionAnswerEntity> answers =
                answerRepository.findBySubmissionIdOrderById(attempt.getId());
        boolean scoreVisible = attempt.getExam().isShowScoreAfterSubmit();
        boolean correctAnswersVisible = canRevealCorrectAnswers(attempt);
        List<QuizAnswerResultResponse> answerResponses = answers.stream()
                .map(answer -> new QuizAnswerResultResponse(
                        answer.getQuestion().getId(),
                        answer.getSelectedOption().getId(),
                        correctAnswersVisible
                                ? answer.getQuestion().getOptions()
                                .stream()
                                .filter(QuestionOptionEntity::isCorrect)
                                .map(QuestionOptionEntity::getId)
                                .findFirst()
                                .orElse(null)
                                : null,
                        correctAnswersVisible
                                ? answer.isCorrect()
                                : null,
                        scoreVisible && correctAnswersVisible
                                ? answer.getAwardedPoints()
                                : null
                ))
                .toList();

        return new QuizResultResponse(attempt.getId(), attempt.getAttemptNumber(),
                attempt.getExam().getId(), attempt.getStudent().getId(),
                attempt.getStatus(), attempt.getStartedAt(), attempt.getExpiresAt(),
                scoreVisible ? attempt.getScore() : null,
                scoreVisible ? attempt.getTotalPoints() : null,
                attempt.getSubmittedAt(), answerResponses);
    }

    private boolean canRevealCorrectAnswers(
            QuizSubmissionEntity attempt
    ) {
        ExamEntity exam = attempt.getExam();
        if (!exam.isShowCorrectAnswersAfterSubmit()) {
            return false;
        }

        int attemptNumber = attempt.getAttemptNumber() == null
                ? 1
                : attempt.getAttemptNumber();
        return attemptNumber >= exam.getMaxAttempts()
                || exam.getStatus() == ExamStatus.CLOSED
                || !LocalDateTime.now().isBefore(exam.getDeadline());
    }

    private ResponseStatusException invalid(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
