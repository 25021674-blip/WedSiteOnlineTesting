package com.ok.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.springframework.dao.DataIntegrityViolationException;

import com.ok.dto.common.ExamStatus;
import com.ok.dto.common.Role;
import com.ok.entity.ExamEntity;
import com.ok.entity.UserEntity;
import com.ok.essay.entity.EssayAssignmentFileEntity;
import com.ok.essay.entity.EssaySubmissionEntity;
import com.ok.quiz.entity.QuestionEntity;
import com.ok.quiz.entity.QuizSubmissionAnswerEntity;
import com.ok.quiz.entity.QuizSubmissionEntity;

@Tag("persistence")
class PersistenceConstraintIntegrationTests extends AbstractApiIntegrationTest {

    @Test
    void onlyOneQuizAttemptPerStudentAndExamIsAllowed() {
        UserEntity owner = user("owner@example.com", Role.TEACHER);
        UserEntity student = user("student@example.com", Role.STUDENT);
        ExamEntity exam = quiz(owner, ExamStatus.PUBLISHED);
        quizSubmissionRepository.saveAndFlush(new QuizSubmissionEntity(exam, student,
                NOW.minusMinutes(1), NOW.plusMinutes(10)));

        assertThatThrownBy(() -> quizSubmissionRepository.saveAndFlush(
                new QuizSubmissionEntity(exam, student, NOW, NOW.plusMinutes(20))))
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
        QuizSubmissionEntity attempt = quizSubmissionRepository.saveAndFlush(
                new QuizSubmissionEntity(exam, student, NOW, NOW.plusMinutes(10)));
        quizAnswerRepository.saveAndFlush(new QuizSubmissionAnswerEntity(attempt, question,
                question.getOptions().getFirst(), true, 2));

        assertThatThrownBy(() -> quizAnswerRepository.saveAndFlush(new QuizSubmissionAnswerEntity(attempt, question,
                question.getOptions().getLast(), false, 0)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void quizAttemptVersionIncrementsWhenStateChanges() {
        UserEntity owner = user("owner@example.com", Role.TEACHER);
        UserEntity student = user("student@example.com", Role.STUDENT);
        ExamEntity exam = quiz(owner, ExamStatus.PUBLISHED);
        QuizSubmissionEntity attempt = quizSubmissionRepository.saveAndFlush(
                new QuizSubmissionEntity(exam, student, NOW, NOW.plusMinutes(10)));
        Long initialVersion = attempt.getVersion();

        attempt.submit(0, 0, NOW.plusMinutes(1));
        quizSubmissionRepository.saveAndFlush(attempt);

        Long persistedVersion = quizSubmissionRepository.findById(attempt.getId()).orElseThrow().getVersion();
        assertThat(persistedVersion).isGreaterThan(initialVersion);
    }
}
