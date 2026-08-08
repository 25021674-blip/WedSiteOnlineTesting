package com.ok.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.ok.domain.enums.ExamStatus;
import com.ok.domain.enums.ExamType;
import com.ok.domain.enums.ExamAttemptStatus;
import com.ok.domain.enums.Role;
import com.ok.dto.request.SelectedAnswerRequest;
import com.ok.dto.request.SubmitQuizRequest;
import com.ok.entity.ExamEntity;
import com.ok.entity.UserEntity;
import com.ok.entity.QuestionOptionEntity;
import com.ok.entity.QuestionEntity;
import com.ok.entity.QuizSubmissionAnswerEntity;
import com.ok.entity.QuizSubmissionEntity;

class QuizSubmissionApiIntegrationTests extends AbstractApiIntegrationTest {

    @Test
    void startCreatesAttemptUsingPersonalDuration() throws Exception {
        UserEntity owner = user("owner@example.com", Role.TEACHER);
        UserEntity student = user("student@example.com", Role.STUDENT);
        ExamEntity exam = quiz(owner, ExamStatus.PUBLISHED);

        mvc.perform(post("/api/exams/{id}/quiz-submissions/start", exam.getId())
                        .header("Authorization", bearer(student)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.startedAt").value(NOW + ":00"))
                .andExpect(jsonPath("$.expiresAt").value(NOW.plusMinutes(30) + ":00"));
    }

    @Test
    void commonDeadlineCapsPersonalDuration() throws Exception {
        UserEntity owner = user("owner@example.com", Role.TEACHER);
        UserEntity student = user("student@example.com", Role.STUDENT);
        ExamEntity exam = exam(owner, ExamType.MULTIPLE_CHOICE, ExamStatus.PUBLISHED,
                NOW.minusMinutes(1), NOW.plusMinutes(10), 30);

        mvc.perform(post("/api/exams/{id}/quiz-submissions/start", exam.getId())
                        .header("Authorization", bearer(student)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.expiresAt").value(NOW.plusMinutes(10) + ":00"));
    }

    @Test
    void startIsIdempotentForSameStudentAndExam() throws Exception {
        UserEntity owner = user("owner@example.com", Role.TEACHER);
        UserEntity student = user("student@example.com", Role.STUDENT);
        ExamEntity exam = quiz(owner, ExamStatus.PUBLISHED);

        mvc.perform(post("/api/exams/{id}/quiz-submissions/start", exam.getId())
                .header("Authorization", bearer(student))).andExpect(status().isCreated());
        mvc.perform(post("/api/exams/{id}/quiz-submissions/start", exam.getId())
                .header("Authorization", bearer(student))).andExpect(status().isCreated());

        assertThat(quizSubmissionRepository.count()).isEqualTo(1);
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
        assertStartStatus(student, ended, 409);
    }

    @Test
    void saveAnswersCanChangeSelectionWithoutCreatingDuplicateRows() throws Exception {
        Fixture fixture = startedQuiz();
        QuestionOptionEntity correct = fixture.question.getOptions().stream().filter(QuestionOptionEntity::isCorrect).findFirst().orElseThrow();
        QuestionOptionEntity wrong = fixture.question.getOptions().stream().filter(o -> !o.isCorrect()).findFirst().orElseThrow();

        saveAnswers(fixture, new SubmitQuizRequest(List.of(
                new SelectedAnswerRequest(fixture.question.getId(), wrong.getId()))), 200);
        saveAnswers(fixture, new SubmitQuizRequest(List.of(
                new SelectedAnswerRequest(fixture.question.getId(), correct.getId()))), 200);

        assertThat(quizAnswerRepository.count()).isEqualTo(1);
        QuizSubmissionAnswerEntity saved = quizAnswerRepository.findAll().getFirst();
        assertThat(saved.getSelectedOption().getId()).isEqualTo(correct.getId());
        assertThat(saved.isCorrect()).isTrue();
    }

    @Test
    void duplicateQuestionInRequestIsRejectedAndTransactionRollsBack() throws Exception {
        Fixture fixture = startedQuiz();
        Long option = fixture.question.getOptions().getFirst().getId();
        SubmitQuizRequest request = new SubmitQuizRequest(List.of(
                new SelectedAnswerRequest(fixture.question.getId(), option),
                new SelectedAnswerRequest(fixture.question.getId(), option)));

        saveAnswers(fixture, request, 400);
        assertThat(quizAnswerRepository.count()).isZero();
    }

    @Test
    void questionAndOptionMustBelongToCurrentExamAndEachOther() throws Exception {
        Fixture fixture = startedQuiz();
        ExamEntity otherExam = quiz(fixture.owner, ExamStatus.PUBLISHED);
        QuestionEntity otherQuestion = question(otherExam, 1);

        saveAnswers(fixture, new SubmitQuizRequest(List.of(new SelectedAnswerRequest(
                otherQuestion.getId(), otherQuestion.getOptions().getFirst().getId()))), 400);
        saveAnswers(fixture, new SubmitQuizRequest(List.of(new SelectedAnswerRequest(
                fixture.question.getId(), otherQuestion.getOptions().getFirst().getId()))), 400);
        assertThat(quizAnswerRepository.count()).isZero();
    }

    @Test
    void submitCalculatesScoreAndCannotBeRepeated() throws Exception {
        Fixture fixture = startedQuiz();
        QuestionEntity second = question(fixture.exam, 3);
        Long correctFirst = fixture.question.getOptions().stream().filter(QuestionOptionEntity::isCorrect)
                .findFirst().orElseThrow().getId();
        Long wrongSecond = second.getOptions().stream().filter(o -> !o.isCorrect())
                .findFirst().orElseThrow().getId();
        SubmitQuizRequest request = new SubmitQuizRequest(List.of(
                new SelectedAnswerRequest(fixture.question.getId(), correctFirst),
                new SelectedAnswerRequest(second.getId(), wrongSecond)));

        mvc.perform(post("/api/exams/{id}/quiz-submissions", fixture.exam.getId())
                        .header("Authorization", bearer(fixture.student))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.score").value(2.0))
                .andExpect(jsonPath("$.totalPoints").value(5.0))
                .andExpect(jsonPath("$.answers.length()").value(2));

        mvc.perform(post("/api/exams/{id}/quiz-submissions", fixture.exam.getId())
                        .header("Authorization", bearer(fixture.student))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void expiredAttemptIsAutoSubmittedAndLatePayloadIsIgnored() throws Exception {
        UserEntity owner = user("owner@example.com", Role.TEACHER);
        UserEntity student = user("student@example.com", Role.STUDENT);
        ExamEntity exam = quiz(owner, ExamStatus.PUBLISHED);
        QuestionEntity question = question(exam, 5);
        QuizSubmissionEntity attempt = quizSubmissionRepository.saveAndFlush(
                new QuizSubmissionEntity(exam, student, NOW.minusHours(1), NOW.minusSeconds(1)));
        Long correct = question.getOptions().stream().filter(QuestionOptionEntity::isCorrect)
                .findFirst().orElseThrow().getId();

        mvc.perform(post("/api/exams/{id}/quiz-submissions", exam.getId())
                        .header("Authorization", bearer(student))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SubmitQuizRequest(List.of(
                                new SelectedAnswerRequest(question.getId(), correct))))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("AUTO_SUBMITTED"))
                .andExpect(jsonPath("$.score").value(0.0));

        assertThat(quizAnswerRepository.findBySubmissionIdOrderById(attempt.getId())).isEmpty();
    }

    @Test
    void resultIsUnavailableUntilSubmittedThenCanBeRead() throws Exception {
        Fixture fixture = startedQuiz();
        mvc.perform(get("/api/exams/{id}/quiz-submissions/me", fixture.exam.getId())
                        .header("Authorization", bearer(fixture.student)))
                .andExpect(status().isConflict());

        mvc.perform(post("/api/exams/{id}/quiz-submissions", fixture.exam.getId())
                        .header("Authorization", bearer(fixture.student))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SubmitQuizRequest(List.of()))))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/exams/{id}/quiz-submissions/me", fixture.exam.getId())
                        .header("Authorization", bearer(fixture.student)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"));
    }

    @Test
    void schedulerFinalizesExpiredAttemptsButLeavesActiveOnesAlone() {
        UserEntity owner = user("owner@example.com", Role.TEACHER);
        UserEntity expiredStudent = user("expired@example.com", Role.STUDENT);
        UserEntity activeStudent = user("active@example.com", Role.STUDENT);
        ExamEntity exam = quiz(owner, ExamStatus.PUBLISHED);
        question(exam, 2);
        QuizSubmissionEntity expired = quizSubmissionRepository.saveAndFlush(
                new QuizSubmissionEntity(exam, expiredStudent, NOW.minusHours(1), NOW.minusSeconds(1)));
        QuizSubmissionEntity active = quizSubmissionRepository.saveAndFlush(
                new QuizSubmissionEntity(exam, activeStudent, NOW.minusMinutes(1), NOW.plusMinutes(10)));

        quizSubmissionService.autoSubmitExpiredAttempts();

        assertThat(quizSubmissionRepository.findById(expired.getId()).orElseThrow().getStatus())
                .isEqualTo(ExamAttemptStatus.AUTO_SUBMITTED);
        assertThat(quizSubmissionRepository.findById(active.getId()).orElseThrow().getStatus())
                .isEqualTo(ExamAttemptStatus.IN_PROGRESS);
    }

    private Fixture startedQuiz() throws Exception {
        UserEntity owner = user("owner@example.com", Role.TEACHER);
        UserEntity student = user("student@example.com", Role.STUDENT);
        ExamEntity exam = quiz(owner, ExamStatus.PUBLISHED);
        QuestionEntity question = question(exam, 2);
        mvc.perform(post("/api/exams/{id}/quiz-submissions/start", exam.getId())
                        .header("Authorization", bearer(student)))
                .andExpect(status().isCreated());
        return new Fixture(owner, student, exam, question);
    }

    private void saveAnswers(Fixture fixture, SubmitQuizRequest request, int expectedStatus) throws Exception {
        mvc.perform(put("/api/exams/{id}/quiz-submissions/answers", fixture.exam.getId())
                        .header("Authorization", bearer(fixture.student))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().is(expectedStatus));
    }

    private void assertStartStatus(UserEntity user, ExamEntity exam, int expectedStatus) throws Exception {
        mvc.perform(post("/api/exams/{id}/quiz-submissions/start", exam.getId())
                        .header("Authorization", bearer(user)))
                .andExpect(status().is(expectedStatus));
    }

    private record Fixture(UserEntity owner, UserEntity student, ExamEntity exam, QuestionEntity question) {}
}
