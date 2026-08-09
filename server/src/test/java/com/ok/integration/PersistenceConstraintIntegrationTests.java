package com.ok.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import java.math.BigDecimal;
import org.springframework.dao.DataIntegrityViolationException;

import com.ok.domain.enums.ExamStatus;
import com.ok.domain.enums.Role;
import com.ok.entity.ExamEntity;
import com.ok.entity.UserEntity;
import com.ok.entity.EssayAssignmentFileEntity;
import com.ok.entity.EssaySubmissionEntity;
import com.ok.entity.QuestionEntity;
import com.ok.entity.ExamAttemptEntity;
import com.ok.entity.StudentAnswerEntity;

@Tag("persistence")
class PersistenceConstraintIntegrationTests extends AbstractApiIntegrationTest {

    @Test
    void onlyOneQuizAttemptPerStudentAndExamIsAllowed() {
        UserEntity owner = user("owner@example.com", Role.TEACHER);
        UserEntity student = user("student@example.com", Role.STUDENT);
        ExamEntity exam = quiz(owner, ExamStatus.PUBLISHED);
        attemptRepository.saveAndFlush(new ExamAttemptEntity(exam, student,
                TEST_INSTANT.plusSeconds(600), TEST_INSTANT.minusSeconds(60)));

        assertThatThrownBy(() -> attemptRepository.saveAndFlush(
                new ExamAttemptEntity(exam, student,
                        TEST_INSTANT.plusSeconds(1200), TEST_INSTANT)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void onlyOneEssaySubmissionPerStudentAndExamIsAllowed() {
        UserEntity owner = user("owner@example.com", Role.TEACHER);
        UserEntity student = user("student@example.com", Role.STUDENT);
        ExamEntity exam = essay(owner, ExamStatus.PUBLISHED);
        essaySubmissionRepository.saveAndFlush(new EssaySubmissionEntity(exam, student,
                "a.pdf", "a-stored.pdf", "a-path", 10));

        assertThatThrownBy(() -> essaySubmissionRepository.saveAndFlush(new EssaySubmissionEntity(exam, student,
                "b.pdf", "b-stored.pdf", "b-path", 10)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void onlyOneAssignmentFilePerExamIsAllowed() {
        UserEntity owner = user("owner@example.com", Role.TEACHER);
        ExamEntity exam = essay(owner, ExamStatus.DRAFT);
        assignmentFileRepository.saveAndFlush(new EssayAssignmentFileEntity(exam,
                "a.pdf", "a-stored.pdf", "a-path", 10));

        assertThatThrownBy(() -> assignmentFileRepository.saveAndFlush(new EssayAssignmentFileEntity(exam,
                "b.pdf", "b-stored.pdf", "b-path", 10)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void onlyOneSelectedAnswerPerSubmissionAndQuestionIsAllowed() {
        UserEntity owner = user("owner@example.com", Role.TEACHER);
        UserEntity student = user("student@example.com", Role.STUDENT);
        ExamEntity exam = quiz(owner, ExamStatus.PUBLISHED);
        QuestionEntity question = question(exam, 2);
        ExamAttemptEntity attempt = attemptRepository.saveAndFlush(
                new ExamAttemptEntity(exam, student,
                        TEST_INSTANT.plusSeconds(600), TEST_INSTANT));
        studentAnswerRepository.saveAndFlush(new StudentAnswerEntity(attempt, question,
                question.getOptions().getFirst(), null));

        assertThatThrownBy(() -> studentAnswerRepository.saveAndFlush(new StudentAnswerEntity(
                attempt, question, question.getOptions().getLast(), null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void quizAttemptVersionIncrementsWhenStateChanges() {
        UserEntity owner = user("owner@example.com", Role.TEACHER);
        UserEntity student = user("student@example.com", Role.STUDENT);
        ExamEntity exam = quiz(owner, ExamStatus.PUBLISHED);
        ExamAttemptEntity attempt = attemptRepository.saveAndFlush(
                new ExamAttemptEntity(exam, student,
                        TEST_INSTANT.plusSeconds(600), TEST_INSTANT));
        Long initialVersion = attempt.getVersion();

        attempt.submit(TEST_INSTANT.plusSeconds(60), BigDecimal.ZERO);
        attemptRepository.saveAndFlush(attempt);

        Long persistedVersion = attemptRepository.findById(attempt.getId()).orElseThrow().getVersion();
        assertThat(persistedVersion).isGreaterThan(initialVersion);
    }
}
