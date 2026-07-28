package com.ok.quiz.service;

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

import com.ok.dto.*;
import com.ok.entity.ExamEntity;
import com.ok.entity.UserEntity;
import com.ok.exam.service.ExamService;
import com.ok.quiz.entity.*;
import com.ok.repository.QuestionRepository;
import com.ok.repository.QuizSubmissionAnswerRepository;
import com.ok.repository.QuizSubmissionRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class QuizSubmissionService {
    private final QuizSubmissionRepository submissionRepository;
    private final QuizSubmissionAnswerRepository answerRepository;
    private final QuestionRepository questionRepository;
    private final ExamService examService;

    @Transactional
    public QuizAttemptResponse start(Long examId, String email) {
        ExamEntity exam = examService.findExam(examId);
        UserEntity student = examService.currentUser(email);
        requireQuizCanStart(exam, student);

        QuizSubmissionEntity existing = submissionRepository
                .findByExamIdAndStudentId(examId, student.getId()).orElse(null);
        if (existing != null) {
            finalizeIfExpired(existing, LocalDateTime.now());
            return attemptResponse(existing);
        }

        LocalDateTime startedAt = LocalDateTime.now();
        LocalDateTime durationEnd = startedAt.plusMinutes(exam.getDurationMinutes());
        LocalDateTime expiresAt = durationEnd.isBefore(exam.getDeadline())
                ? durationEnd : exam.getDeadline();
        QuizSubmissionEntity attempt = submissionRepository.save(
                new QuizSubmissionEntity(exam, student, startedAt, expiresAt));
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
        if (attempt.getStatus() != QuizAttemptStatus.IN_PROGRESS) {
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
        if (attempt.getStatus() == QuizAttemptStatus.IN_PROGRESS) {
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
                        QuizAttemptStatus.IN_PROGRESS, LocalDateTime.now(), PageRequest.of(0, 200));
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
            AnswerOptionEntity option = question.getOptions().stream()
                    .filter(value -> value.getId().equals(selectedAnswer.selectedOptionId()))
                    .findFirst()
                    .orElseThrow(() -> invalid("Lựa chọn không thuộc câu hỏi " + question.getId()));
            boolean correct = option.isCorrect();
            double awardedPoints = correct ? question.getPoints() : 0;
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
        if (attempt.getStatus() == QuizAttemptStatus.IN_PROGRESS
                && !now.isBefore(attempt.getExpiresAt())) {
            finalizeAttempt(attempt, true, now);
        }
    }

    private void finalizeAttempt(QuizSubmissionEntity attempt, boolean automatic, LocalDateTime now) {
        if (attempt.getStatus() != QuizAttemptStatus.IN_PROGRESS) return;
        List<QuestionEntity> questions = questionRepository.findByExamIdOrderById(attempt.getExam().getId());
        double totalPoints = questions.stream().mapToDouble(QuestionEntity::getPoints).sum();
        double score = answerRepository.findBySubmissionIdOrderById(attempt.getId()).stream()
                .mapToDouble(QuizSubmissionAnswerEntity::getAwardedPoints).sum();
        if (automatic) attempt.autoSubmit(score, totalPoints);
        else attempt.submit(score, totalPoints, now);
    }

    private QuizSubmissionEntity findAttempt(Long examId, Long studentId) {
        return submissionRepository.findByExamIdAndStudentId(examId, studentId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Bạn chưa bắt đầu bài trắc nghiệm này"));
    }

    private void requireQuizCanStart(ExamEntity exam, UserEntity student) {
        requireStudent(student);
        if (exam.getType() != ExamType.MULTIPLE_CHOICE) throw invalid("Đây không phải đề trắc nghiệm");
        if (exam.getDurationMinutes() == null || exam.getDurationMinutes() <= 0) {
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
        if (attempt.getStatus() != QuizAttemptStatus.IN_PROGRESS) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bài làm đã kết thúc");
        }
        LocalDateTime now = LocalDateTime.now();
        if (!now.isBefore(attempt.getExpiresAt())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bài làm đã hết thời gian");
        }
    }

    private QuizAttemptResponse attemptResponse(QuizSubmissionEntity attempt) {
        return new QuizAttemptResponse(attempt.getId(), attempt.getExam().getId(), attempt.getStudent().getId(),
                attempt.getStatus(), attempt.getStartedAt(), attempt.getExpiresAt(),
                attempt.getSubmittedAt(), LocalDateTime.now());
    }

    private QuizResultResponse resultResponse(QuizSubmissionEntity attempt) {
        List<QuizSubmissionAnswerEntity> answers =
                answerRepository.findBySubmissionIdOrderById(attempt.getId());
        return new QuizResultResponse(attempt.getId(), attempt.getExam().getId(), attempt.getStudent().getId(),
                attempt.getStatus(), attempt.getStartedAt(), attempt.getExpiresAt(),
                attempt.getScore(), attempt.getTotalPoints(), attempt.getSubmittedAt(),
                answers.stream().map(answer -> new QuizAnswerResultResponse(answer.getQuestion().getId(),
                        answer.getSelectedOption().getId(), answer.isCorrect(),
                        answer.getAwardedPoints())).toList());
    }

    private ResponseStatusException invalid(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
