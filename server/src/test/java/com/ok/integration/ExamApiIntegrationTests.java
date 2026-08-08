package com.ok.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.ok.domain.enums.ExamStatus;
import com.ok.domain.enums.ExamType;
import com.ok.domain.enums.Role;
import com.ok.dto.request.teacher.CreateExamRequest;
import com.ok.dto.request.UpdateExamRequest;
import com.ok.dto.request.UpdateExamStatusRequest;
import com.ok.entity.ExamEntity;
import com.ok.entity.UserEntity;

class ExamApiIntegrationTests extends AbstractApiIntegrationTest {

    @Test
    void teacherCreatesTrimmedDraftQuiz() throws Exception {
        UserEntity teacher = user("teacher@example.com", Role.TEACHER);
        CreateExamRequest request = new CreateExamRequest("  Java Quiz  ", "  Basics  ",
                ExamType.MULTIPLE_CHOICE, NOW.plusHours(1), NOW.plusHours(2), 30, BigDecimal.TEN);

        mvc.perform(post("/api/exams").header("Authorization", bearer(teacher))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Java Quiz"))
                .andExpect(jsonPath("$.description").value("Basics"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.createdById").value(teacher.getId()));

        assertThat(examRepository.count()).isEqualTo(1);
    }

    @Test
    void studentCannotCreateExamButAdminCan() throws Exception {
        CreateExamRequest request = new CreateExamRequest("Quiz", null, ExamType.MULTIPLE_CHOICE,
                NOW.plusHours(1), NOW.plusHours(2), 30, BigDecimal.TEN);
        UserEntity student = user("student@example.com", Role.STUDENT);
        UserEntity admin = user("admin@example.com", Role.ADMIN);

        mvc.perform(post("/api/exams").header("Authorization", bearer(student))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/exams").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test
    void createRejectsInvalidTimeAndTypeSpecificDuration() throws Exception {
        UserEntity teacher = user("teacher@example.com", Role.TEACHER);

        assertCreateBadRequest(teacher, new CreateExamRequest("Quiz", null, ExamType.MULTIPLE_CHOICE,
                NOW.plusHours(2), NOW.plusHours(1), 30, BigDecimal.TEN));
        assertCreateBadRequest(teacher, new CreateExamRequest("Quiz", null, ExamType.MULTIPLE_CHOICE,
                NOW.plusHours(1), NOW.plusHours(2), null, BigDecimal.TEN));
        assertCreateBadRequest(teacher, new CreateExamRequest("Essay", null, ExamType.ESSAY,
                NOW.plusHours(1), NOW.plusHours(2), 30, BigDecimal.TEN));
    }

    @Test
    void createValidationRejectsBlankAndOversizedFields() throws Exception {
        UserEntity teacher = user("teacher@example.com", Role.TEACHER);
        assertCreateBadRequest(teacher, new CreateExamRequest(" ", null, ExamType.MULTIPLE_CHOICE,
                NOW.plusHours(1), NOW.plusHours(2), 30, BigDecimal.TEN));
        assertCreateBadRequest(teacher, new CreateExamRequest("A".repeat(201), null,
                ExamType.MULTIPLE_CHOICE, NOW.plusHours(1), NOW.plusHours(2), 30, BigDecimal.TEN));
        assertCreateBadRequest(teacher, new CreateExamRequest("Quiz", "A".repeat(2001),
                ExamType.MULTIPLE_CHOICE, NOW.plusHours(1), NOW.plusHours(2), 30, BigDecimal.TEN));
    }

    @Test
    void examListsAreFilteredByRoleAndOwnership() throws Exception {
        UserEntity firstTeacher = user("first@example.com", Role.TEACHER);
        UserEntity secondTeacher = user("second@example.com", Role.TEACHER);
        UserEntity student = user("student@example.com", Role.STUDENT);
        UserEntity admin = user("admin@example.com", Role.ADMIN);
        quiz(firstTeacher, ExamStatus.DRAFT);
        quiz(firstTeacher, ExamStatus.PUBLISHED);
        quiz(secondTeacher, ExamStatus.DRAFT);

        mvc.perform(get("/api/exams").header("Authorization", bearer(firstTeacher)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/api/exams").header("Authorization", bearer(student)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("PUBLISHED"));
        mvc.perform(get("/api/exams").header("Authorization", bearer(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    void draftCanOnlyBeViewedAndManagedByOwnerOrAdmin() throws Exception {
        UserEntity owner = user("owner@example.com", Role.TEACHER);
        UserEntity other = user("other@example.com", Role.TEACHER);
        UserEntity student = user("student@example.com", Role.STUDENT);
        UserEntity admin = user("admin@example.com", Role.ADMIN);
        ExamEntity draft = quiz(owner, ExamStatus.DRAFT);

        mvc.perform(get("/api/exams/{id}", draft.getId()).header("Authorization", bearer(owner)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/exams/{id}", draft.getId()).header("Authorization", bearer(admin)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/exams/{id}", draft.getId()).header("Authorization", bearer(other)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/exams/{id}", draft.getId()).header("Authorization", bearer(student)))
                .andExpect(status().isForbidden());
    }

    @Test
    void ownerUpdatesDraftButCannotUpdatePublishedExam() throws Exception {
        UserEntity owner = user("owner@example.com", Role.TEACHER);
        ExamEntity draft = quiz(owner, ExamStatus.DRAFT);
        UpdateExamRequest request = new UpdateExamRequest("Updated", " New description ",
                NOW.plusHours(1), NOW.plusHours(3), 45);

        mvc.perform(put("/api/exams/{id}", draft.getId()).header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated"))
                .andExpect(jsonPath("$.durationMinutes").value(45));

        draft.changeStatus(ExamStatus.PUBLISHED);
        examRepository.saveAndFlush(draft);
        mvc.perform(put("/api/exams/{id}", draft.getId()).header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void nonOwnerCannotUpdateOrDeleteDraft() throws Exception {
        UserEntity owner = user("owner@example.com", Role.TEACHER);
        UserEntity other = user("other@example.com", Role.TEACHER);
        ExamEntity draft = quiz(owner, ExamStatus.DRAFT);
        UpdateExamRequest request = new UpdateExamRequest("Updated", null,
                NOW.plusHours(1), NOW.plusHours(2), 30);

        mvc.perform(put("/api/exams/{id}", draft.getId()).header("Authorization", bearer(other))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/exams/{id}", draft.getId()).header("Authorization", bearer(other)))
                .andExpect(status().isForbidden());
    }

    @Test
    void quizCannotPublishWithoutQuestionAndEssayCannotPublishWithoutFile() throws Exception {
        UserEntity owner = user("owner@example.com", Role.TEACHER);
        ExamEntity quiz = quiz(owner, ExamStatus.DRAFT);
        ExamEntity essay = essay(owner, ExamStatus.DRAFT);

        assertPublishConflict(owner, quiz);
        assertPublishConflict(owner, essay);
    }

    @Test
    void quizWithQuestionCanPublish() throws Exception {
        UserEntity owner = user("owner@example.com", Role.TEACHER);
        ExamEntity quiz = quiz(owner, ExamStatus.DRAFT);
        question(quiz, 2);

        mvc.perform(patch("/api/exams/{id}/status", quiz.getId())
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateExamStatusRequest(ExamStatus.PUBLISHED))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
    }

    @Test
    void deletingDraftRemovesItsQuestionsAndReturnsNoContent() throws Exception {
        UserEntity owner = user("owner@example.com", Role.TEACHER);
        ExamEntity draft = quiz(owner, ExamStatus.DRAFT);
        question(draft, 2);

        mvc.perform(delete("/api/exams/{id}", draft.getId()).header("Authorization", bearer(owner)))
                .andExpect(status().isNoContent());

        assertThat(examRepository.findById(draft.getId())).isEmpty();
        assertThat(questionRepository.countByExamId(draft.getId())).isZero();
    }

    @Test
    void missingExamReturnsNotFoundContract() throws Exception {
        UserEntity admin = user("admin@example.com", Role.ADMIN);
        mvc.perform(get("/api/exams/999999").header("Authorization", bearer(admin)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    private void assertCreateBadRequest(UserEntity user, CreateExamRequest request) throws Exception {
        mvc.perform(post("/api/exams").header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    private void assertPublishConflict(UserEntity owner, ExamEntity exam) throws Exception {
        mvc.perform(patch("/api/exams/{id}/status", exam.getId())
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateExamStatusRequest(ExamStatus.PUBLISHED))))
                .andExpect(status().isConflict());
    }
}
