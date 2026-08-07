package com.ok.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import com.ok.dto.common.ExamStatus;
import com.ok.dto.common.ExamType;
import com.ok.dto.common.Role;
import com.ok.dto.teacher_admin.GradeEssayRequest;
import com.ok.dto.teacher_admin.UpdateExamStatusRequest;
import com.ok.entity.ExamEntity;
import com.ok.entity.UserEntity;
import com.ok.essay.entity.EssayAssignmentFileEntity;
import com.ok.essay.entity.EssaySubmissionEntity;

class EssayApiIntegrationTests extends AbstractApiIntegrationTest {

    @Test
    void ownerUploadsReadsAndDownloadsAssignmentPdf() throws Exception {
        UserEntity owner = user("owner@example.com", Role.TEACHER);
        ExamEntity exam = essay(owner, ExamStatus.DRAFT);

        uploadAssignment(owner, exam, pdf("assignment.pdf", "assignment"), 201);

        mvc.perform(get("/api/exams/{id}/essay-assignment-file/info", exam.getId())
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.originalFileName").value("assignment.pdf"));
        mvc.perform(get("/api/exams/{id}/essay-assignment-file", exam.getId())
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        org.hamcrest.Matchers.containsString("assignment.pdf")));
    }

    @Test
    void assignmentUploadRequiresOwnerEssayAndDraftStatus() throws Exception {
        UserEntity owner = user("owner@example.com", Role.TEACHER);
        UserEntity other = user("other@example.com", Role.TEACHER);
        ExamEntity essay = essay(owner, ExamStatus.DRAFT);
        ExamEntity quiz = quiz(owner, ExamStatus.DRAFT);
        ExamEntity published = essay(owner, ExamStatus.PUBLISHED);

        uploadAssignment(other, essay, pdf("a.pdf", "a"), 403);
        uploadAssignment(owner, quiz, pdf("a.pdf", "a"), 400);
        uploadAssignment(owner, published, pdf("a.pdf", "a"), 409);
    }

    @Test
    void replacingAndDeletingAssignmentCleansPhysicalFiles() throws Exception {
        UserEntity owner = user("owner@example.com", Role.TEACHER);
        ExamEntity exam = essay(owner, ExamStatus.DRAFT);
        uploadAssignment(owner, exam, pdf("first.pdf", "first"), 201);
        EssayAssignmentFileEntity first = assignmentFileRepository.findByExamId(exam.getId()).orElseThrow();
        Path oldPath = Path.of(first.getStoragePath());
        assertThat(Files.exists(oldPath)).isTrue();

        uploadAssignment(owner, exam, pdf("second.pdf", "second"), 201);
        EssayAssignmentFileEntity second = assignmentFileRepository.findByExamId(exam.getId()).orElseThrow();
        Path newPath = Path.of(second.getStoragePath());
        assertThat(Files.exists(oldPath)).isFalse();
        assertThat(Files.exists(newPath)).isTrue();

        mvc.perform(delete("/api/exams/{id}/essay-assignment-file", exam.getId())
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isNoContent());
        assertThat(assignmentFileRepository.findByExamId(exam.getId())).isEmpty();
        assertThat(Files.exists(newPath)).isFalse();
    }

    @Test
    void studentAccessesAssignmentOnlyWhilePublishedAndOpen() throws Exception {
        UserEntity owner = user("owner@example.com", Role.TEACHER);
        UserEntity student = user("student@example.com", Role.STUDENT);
        ExamEntity exam = essay(owner, ExamStatus.DRAFT);
        uploadAssignment(owner, exam, pdf("assignment.pdf", "assignment"), 201);

        mvc.perform(get("/api/exams/{id}/essay-assignment-file/info", exam.getId())
                        .header("Authorization", bearer(student)))
                .andExpect(status().isForbidden());
        publish(owner, exam);
        mvc.perform(get("/api/exams/{id}/essay-assignment-file/info", exam.getId())
                        .header("Authorization", bearer(student)))
                .andExpect(status().isOk());
    }

    @Test
    void studentSubmitsPdfOnceAndCanReadOwnSubmission() throws Exception {
        UserEntity owner = user("owner@example.com", Role.TEACHER);
        UserEntity student = user("student@example.com", Role.STUDENT);
        ExamEntity exam = essay(owner, ExamStatus.PUBLISHED);

        submitEssay(student, exam, pdf("answer.pdf", "answer"), 201);
        submitEssay(student, exam, pdf("second.pdf", "second"), 409);

        mvc.perform(get("/api/exams/{id}/essay-submissions/me", exam.getId())
                        .header("Authorization", bearer(student)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentId").value(student.getId()))
                .andExpect(jsonPath("$.originalFileName").value("answer.pdf"));
    }

    @Test
    void essaySubmissionRejectsWrongRoleTypeTimeAndInvalidFile() throws Exception {
        UserEntity teacher = user("teacher@example.com", Role.TEACHER);
        UserEntity student = user("student@example.com", Role.STUDENT);
        ExamEntity openEssay = essay(teacher, ExamStatus.PUBLISHED);
        ExamEntity quiz = quiz(teacher, ExamStatus.PUBLISHED);
        ExamEntity futureEssay = exam(teacher, ExamType.ESSAY, ExamStatus.PUBLISHED,
                NOW.plusMinutes(1), NOW.plusHours(1), null);

        submitEssay(teacher, openEssay, pdf("a.pdf", "a"), 403);
        submitEssay(student, quiz, pdf("a.pdf", "a"), 400);
        submitEssay(student, futureEssay, pdf("a.pdf", "a"), 409);
        submitEssay(student, openEssay,
                new MockMultipartFile("file", "fake.pdf", "application/pdf", "not-pdf".getBytes()), 400);
    }

    @Test
    void ownerListsDownloadsAndGradesWhileOtherUsersAreForbidden() throws Exception {
        UserEntity owner = user("owner@example.com", Role.TEACHER);
        UserEntity otherTeacher = user("other@example.com", Role.TEACHER);
        UserEntity firstStudent = user("first@example.com", Role.STUDENT);
        UserEntity secondStudent = user("second@example.com", Role.STUDENT);
        ExamEntity exam = essay(owner, ExamStatus.PUBLISHED);
        submitEssay(firstStudent, exam, pdf("answer.pdf", "answer"), 201);
        EssaySubmissionEntity submission = essaySubmissionRepository
                .findByExamIdAndStudentId(exam.getId(), firstStudent.getId()).orElseThrow();

        mvc.perform(get("/api/exams/{id}/essay-submissions", exam.getId())
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/exams/{id}/essay-submissions", exam.getId())
                        .header("Authorization", bearer(otherTeacher)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/essay-submissions/{id}/file", submission.getId())
                        .header("Authorization", bearer(secondStudent)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/essay-submissions/{id}/file", submission.getId())
                        .header("Authorization", bearer(firstStudent)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));

        mvc.perform(put("/api/essay-submissions/{id}/grade", submission.getId())
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new GradeEssayRequest(8.5, "  Good work  "))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(8.5))
                .andExpect(jsonPath("$.feedback").value("Good work"));
        mvc.perform(put("/api/essay-submissions/{id}/grade", submission.getId())
                        .header("Authorization", bearer(otherTeacher))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new GradeEssayRequest(9.0, null))))
                .andExpect(status().isForbidden());
    }

    @Test
    void gradeValidationRejectsNegativeScoreAndOversizedFeedback() throws Exception {
        UserEntity owner = user("owner@example.com", Role.TEACHER);
        UserEntity student = user("student@example.com", Role.STUDENT);
        ExamEntity exam = essay(owner, ExamStatus.PUBLISHED);
        submitEssay(student, exam, pdf("answer.pdf", "answer"), 201);
        Long id = essaySubmissionRepository.findByExamIdAndStudentId(exam.getId(), student.getId())
                .orElseThrow().getId();

        gradeBadRequest(owner, id, new GradeEssayRequest(-0.1, null));
        gradeBadRequest(owner, id, new GradeEssayRequest(8.0, "A".repeat(2001)));
    }

    @Test
    void missingSubmissionAndAssignmentReturnNotFound() throws Exception {
        UserEntity owner = user("owner@example.com", Role.TEACHER);
        UserEntity student = user("student@example.com", Role.STUDENT);
        ExamEntity exam = essay(owner, ExamStatus.DRAFT);

        mvc.perform(get("/api/exams/{id}/essay-assignment-file/info", exam.getId())
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/exams/{id}/essay-submissions/me", exam.getId())
                        .header("Authorization", bearer(student)))
                .andExpect(status().isNotFound());
    }

    private MockMultipartFile pdf(String name, String marker) {
        return new MockMultipartFile("file", name, "application/pdf",
                ("%PDF-1.4 " + marker).getBytes());
    }

    private void uploadAssignment(UserEntity user, ExamEntity exam,
            MockMultipartFile file, int expectedStatus) throws Exception {
        mvc.perform(multipart("/api/exams/{id}/essay-assignment-file", exam.getId())
                        .file(file).header("Authorization", bearer(user)))
                .andExpect(status().is(expectedStatus));
    }

    private void submitEssay(UserEntity user, ExamEntity exam,
            MockMultipartFile file, int expectedStatus) throws Exception {
        mvc.perform(multipart("/api/exams/{id}/essay-submissions", exam.getId())
                        .file(file).header("Authorization", bearer(user)))
                .andExpect(status().is(expectedStatus));
    }

    private void publish(UserEntity owner, ExamEntity exam) throws Exception {
        mvc.perform(patch("/api/exams/{id}/status", exam.getId())
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateExamStatusRequest(ExamStatus.PUBLISHED))))
                .andExpect(status().isOk());
    }

    private void gradeBadRequest(UserEntity owner, Long id, GradeEssayRequest request) throws Exception {
        mvc.perform(put("/api/essay-submissions/{id}/grade", id)
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
