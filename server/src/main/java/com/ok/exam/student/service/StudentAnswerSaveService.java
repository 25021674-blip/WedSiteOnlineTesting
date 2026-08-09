package com.ok.exam.student.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.GONE;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.ok.domain.enums.ExamAttemptStatus;
import com.ok.domain.enums.QuestionType;
import com.ok.domain.enums.Role;
import com.ok.dto.request.student.SaveStudentAnswerRequest;
import com.ok.dto.response.student.SaveStudentAnswerResponse;
import com.ok.entity.ExamAttemptEntity;
import com.ok.entity.QuestionEntity;
import com.ok.entity.QuestionOptionEntity;
import com.ok.entity.StudentAnswerEntity;
import com.ok.repository.StudentAnswerRepository;
import com.ok.repository.StudentExamAttemptRepository;
import com.ok.repository.StudentQuestionRepository;

import lombok.AllArgsConstructor;

@AllArgsConstructor
@Service
public class StudentAnswerSaveService {

    private final StudentExamAttemptRepository attemptRepository;
    private final StudentQuestionRepository questionRepository;
    private final StudentAnswerRepository answerRepository;
    private final ExamAttemptCompletionService completionService;
    private final Clock clock;

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public SaveStudentAnswerResponse saveAnswer(
            Long attemptId,
            Long questionId,
            SaveStudentAnswerRequest request,
            String authenticatedEmail
    ) {
        validateBasicRequest(
                attemptId,
                questionId,
                request,
                authenticatedEmail
        );

        Instant serverTime = clock.instant();

        ExamAttemptEntity attempt = attemptRepository
                .findOwnedByIdForUpdate(
                        attemptId,
                        authenticatedEmail
                )
                .orElseThrow(() -> new ResponseStatusException(
                        NOT_FOUND,
                        "Không tìm thấy lượt làm bài"
                ));

        validateAttempt(attempt, serverTime);

        QuestionEntity question = questionRepository
                .findByIdAndExam_Id(
                        questionId,
                        attempt.getExam().getId()
                )
                .orElseThrow(() -> new ResponseStatusException(
                        NOT_FOUND,
                        "Câu hỏi không thuộc bài kiểm tra này"
                ));

        ValidatedAnswer validatedAnswer = validateAnswer(
                question,
                request
        );

        Optional<StudentAnswerEntity> existingAnswer = answerRepository
                .findByAttempt_IdAndQuestion_Id(
                        attemptId,
                        questionId
                );

        if (existingAnswer.isPresent()) {
            StudentAnswerEntity answer = existingAnswer.get();
            long storedRevision = getStoredRevision(answer);

            if (request.clientRevision() < storedRevision) {
                throw staleRevisionException();
            }

            if (request.clientRevision() == storedRevision) {
                if (hasSameContent(answer, validatedAnswer)) {
                    return createResponse(question, answer);
                }

                throw new ResponseStatusException(
                        CONFLICT,
                        "Client revision đã được dùng cho một đáp án khác"
                );
            }
        }

        StudentAnswerEntity answer = existingAnswer
                .orElseGet(() -> new StudentAnswerEntity(
                        attempt,
                        question,
                        null,
                        null
                ));

        applyAnswer(
                question,
                answer,
                validatedAnswer,
                request.clientRevision(),
                serverTime
        );

        attempt.recordActivity(serverTime);

        StudentAnswerEntity savedAnswer = answerRepository
                .saveAndFlush(answer);

        return createResponse(question, savedAnswer);
    }

    private void validateBasicRequest(
            Long attemptId,
            Long questionId,
            SaveStudentAnswerRequest request,
            String authenticatedEmail
    ) {
        if (authenticatedEmail == null || authenticatedEmail.isBlank()) {
            throw new ResponseStatusException(
                    UNAUTHORIZED,
                    "Tài khoản chưa được xác thực"
            );
        }

        if (attemptId == null || attemptId <= 0
                || questionId == null || questionId <= 0) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "Mã lượt làm bài và câu hỏi phải lớn hơn 0"
            );
        }

        if (request == null
                || request.clientRevision() == null
                || request.clientRevision() <= 0) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "Client revision phải lớn hơn 0"
            );
        }
    }

    private void validateAttempt(
            ExamAttemptEntity attempt,
            Instant serverTime
    ) {
        if (attempt.getStudent().getRole() != Role.STUDENT) {
            throw new ResponseStatusException(
                    FORBIDDEN,
                    "Chỉ học sinh được lưu đáp án"
            );
        }

        if (attempt.getStatus() != ExamAttemptStatus.IN_PROGRESS) {
            throw new ResponseStatusException(
                    CONFLICT,
                    "Lượt làm bài không còn ở trạng thái đang thực hiện"
            );
        }

        Instant effectiveDeadline = attempt.getDeadlineAt()
                .isBefore(attempt.getExam().getExpiresAt())
                ? attempt.getDeadlineAt()
                : attempt.getExam().getExpiresAt();

        if (!serverTime.isBefore(effectiveDeadline)) {
            completionService.complete(attempt, serverTime, true);

            throw new ResponseStatusException(
                    GONE,
                    "Lượt làm bài đã hết thời gian"
            );
        }
    }

    private ValidatedAnswer validateAnswer(
            QuestionEntity question,
            SaveStudentAnswerRequest request
    ) {
        if (question.getQuestionType()
                == QuestionType.MULTIPLE_CHOICE) {
            return validateMultipleChoice(question, request);
        }

        if (question.getQuestionType() == QuestionType.ESSAY) {
            return validateEssay(request);
        }

        throw new ResponseStatusException(
                BAD_REQUEST,
                "Loại câu hỏi không được hỗ trợ"
        );
    }

    private ValidatedAnswer validateMultipleChoice(
            QuestionEntity question,
            SaveStudentAnswerRequest request
    ) {
        if (request.selectedOptionId() == null) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "Câu trắc nghiệm phải có phương án được chọn"
            );
        }

        if (request.essayContent() != null
                && !request.essayContent().isBlank()) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "Câu trắc nghiệm không được có nội dung tự luận"
            );
        }

        QuestionOptionEntity selectedOption = question
                .getOptions()
                .stream()
                .filter(option -> Objects.equals(
                        option.getId(),
                        request.selectedOptionId()
                ))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        BAD_REQUEST,
                        "Phương án không thuộc câu hỏi này"
                ));

        return new ValidatedAnswer(selectedOption, null);
    }

    private ValidatedAnswer validateEssay(
            SaveStudentAnswerRequest request
    ) {
        if (request.selectedOptionId() != null) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "Câu tự luận không được có phương án lựa chọn"
            );
        }

        if (request.essayContent() == null) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "Nội dung tự luận không được để null"
            );
        }

        return new ValidatedAnswer(
                null,
                request.essayContent()
        );
    }

    private void applyAnswer(
            QuestionEntity question,
            StudentAnswerEntity answer,
            ValidatedAnswer validatedAnswer,
            Long clientRevision,
            Instant updatedAt
    ) {
        if (question.getQuestionType()
                == QuestionType.MULTIPLE_CHOICE) {
            answer.updateMultipleChoice(
                    validatedAnswer.selectedOption(),
                    clientRevision,
                    updatedAt
            );
            return;
        }

        answer.updateEssay(
                validatedAnswer.essayContent(),
                clientRevision,
                updatedAt
        );
    }

    private boolean hasSameContent(
            StudentAnswerEntity answer,
            ValidatedAnswer validatedAnswer
    ) {
        Long storedOptionId = answer.getSelectedOption() == null
                ? null
                : answer.getSelectedOption().getId();

        Long requestedOptionId = validatedAnswer.selectedOption() == null
                ? null
                : validatedAnswer.selectedOption().getId();

        return Objects.equals(storedOptionId, requestedOptionId)
                && Objects.equals(
                        answer.getEssayAnswer(),
                        validatedAnswer.essayContent()
                );
    }

    private long getStoredRevision(StudentAnswerEntity answer) {
        return answer.getClientRevision() == null
                ? 0L
                : answer.getClientRevision();
    }

    private ResponseStatusException staleRevisionException() {
        return new ResponseStatusException(
                CONFLICT,
                "Đáp án mới hơn đã được lưu trên server"
        );
    }

    private SaveStudentAnswerResponse createResponse(
            QuestionEntity question,
            StudentAnswerEntity answer
    ) {
        Long selectedOptionId = answer.getSelectedOption() == null
                ? null
                : answer.getSelectedOption().getId();

        String essayContent = question.getQuestionType()
                == QuestionType.ESSAY
                ? answer.getEssayAnswer()
                : null;

        boolean answered = question.getQuestionType()
                == QuestionType.MULTIPLE_CHOICE
                ? selectedOptionId != null
                : essayContent != null && !essayContent.isBlank();

        return new SaveStudentAnswerResponse(
                answer.getAttempt().getId(),
                question.getId(),
                question.getQuestionType(),
                selectedOptionId,
                essayContent,
                answer.getClientRevision(),
                answer.getUpdatedAt(),
                answered
        );
    }

    private record ValidatedAnswer(
            QuestionOptionEntity selectedOption,
            String essayContent
    ) {
    }
}
