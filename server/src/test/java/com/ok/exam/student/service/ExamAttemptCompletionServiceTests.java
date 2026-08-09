package com.ok.exam.student.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ok.domain.enums.ExamAttemptStatus;
import com.ok.domain.enums.ExamType;
import com.ok.domain.enums.QuestionType;
import com.ok.domain.enums.Role;
import com.ok.entity.ExamAttemptEntity;
import com.ok.entity.ExamEntity;
import com.ok.entity.QuestionEntity;
import com.ok.entity.QuestionOptionEntity;
import com.ok.entity.StudentAnswerEntity;
import com.ok.entity.UserEntity;
import com.ok.repository.StudentAnswerRepository;
import com.ok.repository.StudentExamAttemptRepository;
import com.ok.repository.StudentQuestionRepository;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
class ExamAttemptCompletionServiceTests {

    @Mock StudentExamAttemptRepository attemptRepository;
    @Mock StudentQuestionRepository questionRepository;
    @Mock StudentAnswerRepository answerRepository;
    @InjectMocks ExamAttemptCompletionService service;

    @Test
    void completeGradesMultipleChoiceAnswersAndStoresTotalScore() {
        UserEntity teacher = new UserEntity("Teacher", "teacher@example.com", "encoded", Role.TEACHER);
        UserEntity student = new UserEntity("Student", "student@example.com", "encoded", Role.STUDENT);
        Instant now = Instant.parse("2030-01-15T03:00:00Z");
        ExamEntity exam = new ExamEntity(teacher, "Quiz", null, now.minusSeconds(60),
                now.plusSeconds(3600), 30, BigDecimal.valueOf(5), ExamType.MULTIPLE_CHOICE);
        ExamAttemptEntity attempt = new ExamAttemptEntity(exam, student, now.plusSeconds(1800), now);
        QuestionEntity question = new QuestionEntity(
                exam, "Question", QuestionType.MULTIPLE_CHOICE, BigDecimal.valueOf(5), 1);
        QuestionOptionEntity correct = new QuestionOptionEntity("Correct", true, 1);
        question.addOption(correct);
        StudentAnswerEntity answer = new StudentAnswerEntity(attempt, question, correct, null);

        when(questionRepository.findByExam_IdOrderByQuestionOrderAsc(any()))
                .thenReturn(List.of(question));
        when(answerRepository.findByAttempt_Id(any())).thenReturn(List.of(answer));
        when(attemptRepository.saveAndFlush(attempt)).thenReturn(attempt);

        var result = service.complete(attempt, now.plusSeconds(120), false);

        assertThat(attempt.getStatus()).isEqualTo(ExamAttemptStatus.SUBMITTED);
        assertThat(attempt.getScore()).isEqualByComparingTo("5");
        assertThat(answer.getCorrect()).isTrue();
        assertThat(answer.getScore()).isEqualByComparingTo("5");
        assertThat(result.answeredCount()).isEqualTo(1);
        assertThat(result.totalQuestions()).isEqualTo(1);
    }
}
