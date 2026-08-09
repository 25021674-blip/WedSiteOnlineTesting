package com.ok.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import com.ok.domain.enums.ExamAttemptStatus;
import com.ok.domain.enums.ExamStatus;
import com.ok.domain.enums.ExamType;
import com.ok.domain.enums.Role;
import com.ok.dto.request.student.SaveStudentAnswerRequest;
import com.ok.entity.ExamAttemptEntity;
import com.ok.entity.ExamEntity;
import com.ok.entity.QuestionEntity;
import com.ok.entity.QuestionOptionEntity;
import com.ok.entity.StudentAnswerEntity;
import com.ok.entity.UserEntity;
import com.ok.exam.student.scheduler.ExamAttemptAutoSubmitScheduler;

class StudentExamAttemptApiIntegrationTests extends AbstractApiIntegrationTest {

    @Autowired ExamAttemptAutoSubmitScheduler autoSubmitScheduler;

    @Test
    void startCreatesOneResumableAttemptUsingPersonalDuration() throws Exception {
        UserEntity owner = user("owner@example.com", Role.TEACHER);
        UserEntity student = user("student@example.com", Role.STUDENT);
        ExamEntity exam = quiz(owner, ExamStatus.PUBLISHED);
        question(exam, 2);

        long firstAttemptId = start(exam, student);
        long resumedAttemptId = start(exam, student);

        assertThat(resumedAttemptId).isEqualTo(firstAttemptId);
        assertThat(attemptRepository.count()).isEqualTo(1);
    }

    @Test
    void commonDeadlineCapsPersonalDuration() throws Exception {
        UserEntity owner = user("owner@example.com", Role.TEACHER);
        UserEntity student = user("student@example.com", Role.STUDENT);
        ExamEntity exam = exam(owner, ExamType.MULTIPLE_CHOICE, ExamStatus.PUBLISHED,
                NOW.minusMinutes(1), NOW.plusMinutes(10), 30);
        question(exam, 2);

        mvc.perform(post("/api/student/exams/{id}/attempts/start", exam.getId())
                        .header("Authorization", bearer(student)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deadlineAt").value(TEST_INSTANT.plusSeconds(600).toString()));
    }

    @Test
    void startRejectsWrongRoleTypeStatusAndTimeWindow() throws Exception {
        UserEntity teacher = user("teacher@example.com", Role.TEACHER);
        UserEntity student = user("student@example.com", Role.STUDENT);
        ExamEntity draft = quiz(teacher, ExamStatus.DRAFT);
        ExamEntity essay = essay(teacher, ExamStatus.PUBLISHED);
        ExamEntity future = exam(teacher, ExamType.MULTIPLE_CHOICE, ExamStatus.PUBLISHED,
                NOW.plusMinutes(1), NOW.plusHours(1), 30);
        ExamEntity ended = exam(teacher, ExamType.MULTIPLE_CHOICE, ExamStatus.PUBLISHED,
                NOW.minusHours(2), NOW, 30);

        assertStartStatus(teacher, draft, 403);
        assertStartStatus(student, draft, 409);
        assertStartStatus(student, essay, 400);
        assertStartStatus(student, future, 409);
        assertStartStatus(student, ended, 410);
    }

    @Test
    void savingAnAnswerUpdatesTheSameRowAndRejectsStaleRevision() throws Exception {
        Fixture fixture = startedQuiz();
        QuestionOptionEntity wrong = fixture.question.getOptions().stream()
                .filter(option -> !option.isCorrect()).findFirst().orElseThrow();
        QuestionOptionEntity correct = fixture.question.getOptions().stream()
                .filter(QuestionOptionEntity::isCorrect).findFirst().orElseThrow();

        saveAnswer(fixture, wrong.getId(), 1, 200);
        saveAnswer(fixture, correct.getId(), 2, 200);
        saveAnswer(fixture, wrong.getId(), 1, 409);

        assertThat(studentAnswerRepository.count()).isEqualTo(1);
        assertThat(studentAnswerRepository.findAll().getFirst().getSelectedOption().getId())
                .isEqualTo(correct.getId());
    }

    @Test
    void questionAndOptionMustBelongToTheCurrentAttemptExam() throws Exception {
        Fixture fixture = startedQuiz();
        ExamEntity otherExam = quiz(fixture.owner, ExamStatus.PUBLISHED);
        QuestionEntity otherQuestion = question(otherExam, 1);

        mvc.perform(put("/api/student/exam-attempts/{attemptId}/questions/{questionId}/answer",
                        fixture.attemptId, otherQuestion.getId())
                        .header("Authorization", bearer(fixture.student))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SaveStudentAnswerRequest(
                                otherQuestion.getOptions().getFirst().getId(), null, 1L))))
                .andExpect(status().isNotFound());

        saveAnswer(fixture, otherQuestion.getOptions().getFirst().getId(), 1, 400);
        assertThat(studentAnswerRepository.count()).isZero();
    }

    @Test
    void submitCalculatesScoreAndResultUsesTheSameAttemptData() throws Exception {
        Fixture fixture = startedQuiz();
        QuestionEntity second = question(fixture.exam, 3);
        Long correctFirst = fixture.question.getOptions().stream()
                .filter(QuestionOptionEntity::isCorrect).findFirst().orElseThrow().getId();
        Long wrongSecond = second.getOptions().stream()
                .filter(option -> !option.isCorrect()).findFirst().orElseThrow().getId();

        saveAnswer(fixture, correctFirst, 1, 200);
        saveAnswer(new Fixture(fixture.owner, fixture.student, fixture.exam, second, fixture.attemptId),
                wrongSecond, 1, 200);

        mvc.perform(post("/api/student/exams/{examId}/attempts/{attemptId}/submit",
                        fixture.exam.getId(), fixture.attemptId)
                        .header("Authorization", bearer(fixture.student)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.score").value(2.0))
                .andExpect(jsonPath("$.maxScore").value(5.0))
                .andExpect(jsonPath("$.answeredCount").value(2));

        mvc.perform(get("/api/student/exams/{examId}/attempts/me", fixture.exam.getId())
                        .header("Authorization", bearer(fixture.student)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attemptId").value(fixture.attemptId))
                .andExpect(jsonPath("$.score").value(2.0))
                .andExpect(jsonPath("$.totalPoints").value(5.0));
    }

    @Test
    void resultIsUnavailableBeforeSubmission() throws Exception {
        Fixture fixture = startedQuiz();

        mvc.perform(get("/api/student/exams/{examId}/attempts/me", fixture.exam.getId())
                        .header("Authorization", bearer(fixture.student)))
                .andExpect(status().isConflict());
    }

    @Test
    void schedulerAutoSubmitsExpiredAttemptAndCalculatesScore() {
        UserEntity owner = user("owner@example.com", Role.TEACHER);
        UserEntity student = user("student@example.com", Role.STUDENT);
        ExamEntity exam = quiz(owner, ExamStatus.PUBLISHED);
        QuestionEntity question = question(exam, 2);
        ExamAttemptEntity attempt = attemptRepository.saveAndFlush(new ExamAttemptEntity(
                exam, student, TEST_INSTANT.minusSeconds(1), TEST_INSTANT.minusSeconds(1800)));
        studentAnswerRepository.saveAndFlush(new StudentAnswerEntity(
                attempt,
                question,
                question.getOptions().stream().filter(QuestionOptionEntity::isCorrect)
                        .findFirst().orElseThrow(),
                null));

        autoSubmitScheduler.autoSubmitExpiredAttempts();

        ExamAttemptEntity saved = attemptRepository.findById(attempt.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(ExamAttemptStatus.AUTO_SUBMITTED);
        assertThat(saved.getScore()).isEqualByComparingTo("2.00");
    }

    private Fixture startedQuiz() throws Exception {
        UserEntity owner = user("owner@example.com", Role.TEACHER);
        UserEntity student = user("student@example.com", Role.STUDENT);
        ExamEntity exam = quiz(owner, ExamStatus.PUBLISHED);
        QuestionEntity question = question(exam, 2);
        return new Fixture(owner, student, exam, question, start(exam, student));
    }

    private long start(ExamEntity exam, UserEntity student) throws Exception {
        MvcResult result = mvc.perform(post("/api/student/exams/{id}/attempts/start", exam.getId())
                        .header("Authorization", bearer(student)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("attemptId").asLong();
    }

    private void saveAnswer(Fixture fixture, Long optionId, long revision, int expectedStatus)
            throws Exception {
        mvc.perform(put("/api/student/exam-attempts/{attemptId}/questions/{questionId}/answer",
                        fixture.attemptId, fixture.question.getId())
                        .header("Authorization", bearer(fixture.student))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SaveStudentAnswerRequest(
                                optionId, null, revision))))
                .andExpect(status().is(expectedStatus));
    }

    private void assertStartStatus(UserEntity user, ExamEntity exam, int expectedStatus)
            throws Exception {
        mvc.perform(post("/api/student/exams/{id}/attempts/start", exam.getId())
                        .header("Authorization", bearer(user)))
                .andExpect(status().is(expectedStatus));
    }

    private record Fixture(
            UserEntity owner,
            UserEntity student,
            ExamEntity exam,
            QuestionEntity question,
            long attemptId
    ) {
    }
}
