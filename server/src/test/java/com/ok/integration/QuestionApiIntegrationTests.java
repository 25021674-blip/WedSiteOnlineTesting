package com.ok.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.ok.domain.enums.ExamStatus;
import com.ok.domain.enums.Role;
import com.ok.dto.request.AnswerOptionRequest;
import com.ok.dto.request.teacher.CreateQuestionRequest;
import com.ok.entity.ExamEntity;
import com.ok.entity.UserEntity;
import com.ok.entity.QuestionEntity;
import com.ok.entity.ExamAttemptEntity;

class QuestionApiIntegrationTests extends AbstractApiIntegrationTest {

    @Test
    void ownerCreatesQuestionWithExactlyOneCorrectOption() throws Exception {
        UserEntity owner = user("owner@example.com", Role.TEACHER);
        ExamEntity exam = quiz(owner, ExamStatus.DRAFT);

        mvc.perform(post("/api/exams/{id}/questions", exam.getId())
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validQuestion())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.content").value("What is 2 + 2?"))
                .andExpect(jsonPath("$.points").value(2.5))
                .andExpect(jsonPath("$.options.length()").value(2))
                .andExpect(jsonPath("$.options[0].correct").value(true));
    }

    @Test
    void questionRequiresAtLeastTwoOptionsAndPositivePoints() throws Exception {
        UserEntity owner = user("owner@example.com", Role.TEACHER);
        ExamEntity exam = quiz(owner, ExamStatus.DRAFT);
        CreateQuestionRequest oneOption = new CreateQuestionRequest("Question", 1,
                List.of(new AnswerOptionRequest("Only", true)));
        CreateQuestionRequest zeroPoints = new CreateQuestionRequest("Question", 0,
                List.of(new AnswerOptionRequest("A", true), new AnswerOptionRequest("B", false)));

        assertQuestionBadRequest(owner, exam, oneOption);
        assertQuestionBadRequest(owner, exam, zeroPoints);
    }

    @Test
    void questionRequiresExactlyOneCorrectOption() throws Exception {
        UserEntity owner = user("owner@example.com", Role.TEACHER);
        ExamEntity exam = quiz(owner, ExamStatus.DRAFT);
        CreateQuestionRequest none = new CreateQuestionRequest("Question", 1,
                List.of(new AnswerOptionRequest("A", false), new AnswerOptionRequest("B", false)));
        CreateQuestionRequest two = new CreateQuestionRequest("Question", 1,
                List.of(new AnswerOptionRequest("A", true), new AnswerOptionRequest("B", true)));

        assertQuestionBadRequest(owner, exam, none);
        assertQuestionBadRequest(owner, exam, two);
    }

    @Test
    void questionCannotBeCreatedForEssayOrPublishedQuiz() throws Exception {
        UserEntity owner = user("owner@example.com", Role.TEACHER);
        ExamEntity essay = essay(owner, ExamStatus.DRAFT);
        ExamEntity publishedQuiz = quiz(owner, ExamStatus.PUBLISHED);

        assertQuestionBadRequest(owner, essay, validQuestion());
        mvc.perform(post("/api/exams/{id}/questions", publishedQuiz.getId())
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validQuestion())))
                .andExpect(status().isConflict());
    }

    @Test
    void nonOwnerAndStudentCannotManageQuestions() throws Exception {
        UserEntity owner = user("owner@example.com", Role.TEACHER);
        UserEntity other = user("other@example.com", Role.TEACHER);
        UserEntity student = user("student@example.com", Role.STUDENT);
        ExamEntity exam = quiz(owner, ExamStatus.DRAFT);

        mvc.perform(post("/api/exams/{id}/questions", exam.getId())
                        .header("Authorization", bearer(other))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validQuestion())))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/exams/{id}/questions", exam.getId())
                        .header("Authorization", bearer(student)))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateReplacesContentPointsAndOptions() throws Exception {
        UserEntity owner = user("owner@example.com", Role.TEACHER);
        ExamEntity exam = quiz(owner, ExamStatus.DRAFT);
        QuestionEntity question = question(exam, 1);
        CreateQuestionRequest replacement = new CreateQuestionRequest("Updated", 4,
                List.of(new AnswerOptionRequest("Wrong", false),
                        new AnswerOptionRequest("New correct", true),
                        new AnswerOptionRequest("Also wrong", false)));

        mvc.perform(put("/api/questions/{id}", question.getId())
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(replacement)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("Updated"))
                .andExpect(jsonPath("$.points").value(4.0))
                .andExpect(jsonPath("$.options.length()").value(3));
    }

    @Test
    void deleteQuestionRemovesItFromDraft() throws Exception {
        UserEntity owner = user("owner@example.com", Role.TEACHER);
        ExamEntity exam = quiz(owner, ExamStatus.DRAFT);
        QuestionEntity question = question(exam, 1);

        mvc.perform(delete("/api/questions/{id}", question.getId())
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isNoContent());

        assertThat(questionRepository.findById(question.getId())).isEmpty();
    }

    @Test
    void studentQuestionViewRequiresActiveAttemptAndNeverLeaksCorrectAnswer() throws Exception {
        UserEntity owner = user("owner@example.com", Role.TEACHER);
        UserEntity student = user("student@example.com", Role.STUDENT);
        ExamEntity exam = quiz(owner, ExamStatus.PUBLISHED);
        question(exam, 2);

        mvc.perform(get("/api/exams/{id}/quiz", exam.getId())
                        .header("Authorization", bearer(student)))
                .andExpect(status().isNotFound());

        attemptRepository.saveAndFlush(new ExamAttemptEntity(
                exam,
                student,
                TEST_INSTANT.plusSeconds(1200),
                TEST_INSTANT.minusSeconds(60)
        ));
        mvc.perform(get("/api/exams/{id}/quiz", exam.getId())
                        .header("Authorization", bearer(student)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].options[0].content").exists())
                .andExpect(jsonPath("$[0].options[0].correct").doesNotExist());
    }

    private CreateQuestionRequest validQuestion() {
        return new CreateQuestionRequest("  What is 2 + 2?  ", 2.5,
                List.of(new AnswerOptionRequest("  4  ", true),
                        new AnswerOptionRequest("5", false)));
    }

    private void assertQuestionBadRequest(UserEntity owner, ExamEntity exam,
            CreateQuestionRequest request) throws Exception {
        mvc.perform(post("/api/exams/{id}/questions", exam.getId())
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
