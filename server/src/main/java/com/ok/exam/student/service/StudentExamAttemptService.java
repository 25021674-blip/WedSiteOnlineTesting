package com.ok.exam.student.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.GONE;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.ok.domain.enums.ExamAttemptStatus;
import com.ok.domain.enums.QuestionType;
import com.ok.domain.enums.Role;
import com.ok.dto.response.student.StudentExamScreenResponse;
import com.ok.dto.response.student.StudentExamScreenResponse.AnswerInfo;
import com.ok.dto.response.student.StudentExamScreenResponse.ExamInfo;
import com.ok.dto.response.student.StudentExamScreenResponse.OptionInfo;
import com.ok.dto.response.student.StudentExamScreenResponse.Progress;
import com.ok.dto.response.student.StudentExamScreenResponse.QuestionInfo;
import com.ok.entity.ExamAttemptEntity;
import com.ok.entity.ExamEntity;
import com.ok.entity.QuestionEntity;
import com.ok.entity.StudentAnswerEntity;
import com.ok.entity.UserEntity;
import com.ok.repository.StudentAnswerRepository;
import com.ok.repository.StudentExamAttemptRepository;
import com.ok.repository.StudentExamRepository;
import com.ok.repository.StudentQuestionRepository;
import com.ok.repository.StudentUserRepository;

import lombok.AllArgsConstructor;

@AllArgsConstructor
@Service
public class StudentExamAttemptService {

    private final StudentUserRepository studentUserRepository;
    private final StudentExamRepository examRepository;
    private final StudentExamAttemptRepository attemptRepository;
    private final StudentQuestionRepository questionRepository;
    private final StudentAnswerRepository answerRepository;

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

        Optional<ExamAttemptEntity> latestAttempt = attemptRepository
                .findFirstByExam_IdAndStudent_IdOrderByStartedAtDesc(
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

        ExamAttemptEntity attempt = latestAttempt
                .map(existingAttempt -> validateResumableAttempt(
                        existingAttempt,
                        serverTime
                ))
                .orElseGet(() -> createAttempt(exam, student, serverTime));

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

    private void validateExamConfiguration(
            ExamEntity exam,
            Instant serverTime
    ) {
        if (serverTime.isBefore(exam.getStartAt())) {
            throw new ResponseStatusException(
                    CONFLICT,
                    "Bài kiểm tra chưa đến thời gian bắt đầu"
            );
        }

        if (exam.getDurationMinutes() == null
                || exam.getDurationMinutes() <= 0) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "Thời lượng bài kiểm tra không hợp lệ"
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
        Instant durationDeadline = serverTime.plus(
                Duration.ofMinutes(exam.getDurationMinutes())
        );

        Instant deadlineAt = durationDeadline.isBefore(exam.getExpiresAt())
                ? durationDeadline
                : exam.getExpiresAt();

        ExamAttemptEntity attempt = new ExamAttemptEntity(
                exam,
                student,
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
                        attempt.getDeadlineAt()
                ).toSeconds()
        );

        int screenExitCount = attempt.getScreenExitCount() == null
                ? 0
                : attempt.getScreenExitCount();

        return new StudentExamScreenResponse(
                attempt.getId(),
                new ExamInfo(
                        exam.getId(),
                        exam.getTitle(),
                        exam.getDescription(),
                        exam.getType()
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
