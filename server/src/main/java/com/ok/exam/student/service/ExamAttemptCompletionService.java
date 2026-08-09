package com.ok.exam.student.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ok.domain.enums.ExamAttemptStatus;
import com.ok.domain.enums.QuestionType;
import com.ok.entity.ExamAttemptEntity;
import com.ok.entity.QuestionEntity;
import com.ok.entity.StudentAnswerEntity;
import com.ok.repository.StudentAnswerRepository;
import com.ok.repository.StudentExamAttemptRepository;
import com.ok.repository.StudentQuestionRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ExamAttemptCompletionService {

    private final StudentExamAttemptRepository attemptRepository;
    private final StudentQuestionRepository questionRepository;
    private final StudentAnswerRepository answerRepository;

    @Transactional
    public AttemptResult complete(
            ExamAttemptEntity attempt,
            Instant submittedAt,
            boolean automatic
    ) {
        AttemptResult result = calculateResult(attempt, true);

        if (attempt.getStatus() == ExamAttemptStatus.IN_PROGRESS) {
            if (automatic) {
                attempt.autoSubmit(submittedAt, result.score());
            } else {
                attempt.submit(submittedAt, result.score());
            }
        } else if (attempt.getScore() == null) {
            attempt.recordCalculatedScore(result.score());
        }

        attemptRepository.saveAndFlush(attempt);
        return result;
    }

    @Transactional
    public void autoSubmitExpiredAttempt(Long attemptId, Instant serverTime) {
        attemptRepository.findByIdForUpdate(attemptId)
                .filter(attempt -> attempt.getStatus() == ExamAttemptStatus.IN_PROGRESS)
                .filter(attempt -> !serverTime.isBefore(effectiveDeadline(attempt)))
                .ifPresent(attempt -> complete(attempt, serverTime, true));
    }

    @Transactional(readOnly = true)
    public AttemptResult summarize(ExamAttemptEntity attempt) {
        return calculateResult(attempt, false);
    }

    private AttemptResult calculateResult(
            ExamAttemptEntity attempt,
            boolean recordGrades
    ) {
        List<QuestionEntity> questions = questionRepository
                .findByExam_IdOrderByQuestionOrderAsc(attempt.getExam().getId());
        List<StudentAnswerEntity> answers = answerRepository
                .findByAttempt_Id(attempt.getId());

        BigDecimal totalPoints = questions.stream()
                .map(QuestionEntity::getMaxScore)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal score = BigDecimal.ZERO;
        for (StudentAnswerEntity answer : answers) {
            if (answer.getQuestion().getQuestionType() != QuestionType.MULTIPLE_CHOICE) {
                continue;
            }

            boolean correct = answer.getSelectedOption() != null
                    && answer.getSelectedOption().isCorrect();
            BigDecimal awardedPoints = correct
                    ? answer.getQuestion().getMaxScore()
                    : BigDecimal.ZERO;
            if (recordGrades) {
                answer.recordAutomaticGrade(correct, awardedPoints);
            }
            score = score.add(awardedPoints);
        }

        int answeredCount = (int) answers.stream()
                .filter(this::isAnswered)
                .count();

        return new AttemptResult(
                score,
                totalPoints,
                answeredCount,
                questions.size()
        );
    }

    private boolean isAnswered(StudentAnswerEntity answer) {
        if (answer.getQuestion().getQuestionType() == QuestionType.MULTIPLE_CHOICE) {
            return answer.getSelectedOption() != null;
        }
        return answer.getEssayAnswer() != null
                && !answer.getEssayAnswer().isBlank();
    }

    private Instant effectiveDeadline(ExamAttemptEntity attempt) {
        return attempt.getDeadlineAt().isBefore(attempt.getExam().getExpiresAt())
                ? attempt.getDeadlineAt()
                : attempt.getExam().getExpiresAt();
    }

    public record AttemptResult(
            BigDecimal score,
            BigDecimal totalPoints,
            int answeredCount,
            int totalQuestions
    ) {
    }
}
