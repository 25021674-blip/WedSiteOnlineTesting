package com.ok.essay.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

class FileStorageServiceTests {
    @TempDir
    Path tempDir;

    @Test
    void storesARealPdfSignature() throws Exception {
        FileStorageService service = new FileStorageService(tempDir.toString());
        MockMultipartFile file = new MockMultipartFile("file", "answer.pdf", "application/pdf",
                "%PDF-1.4 test".getBytes());

        var stored = service.storeSubmissionPdf(file, 10L, 20L);

        assertEquals("answer.pdf", stored.originalName());
        assertTrue(Files.exists(Path.of(stored.path())));
    }

    @Test
    void rejectsAFileThatOnlyHasPdfExtension() {
        FileStorageService service = new FileStorageService(tempDir.toString());
        MockMultipartFile file = new MockMultipartFile("file", "fake.pdf", "application/pdf",
                "not a pdf".getBytes());

        assertThrows(ResponseStatusException.class, () -> service.storeSubmissionPdf(file, 10L, 20L));
    }

    @Test
    void storesTeacherAssignmentSeparatelyFromStudentSubmission() throws Exception {
        Path submissionRoot = tempDir.resolve("submissions");
        Path assignmentRoot = tempDir.resolve("assignments");
        FileStorageService service = new FileStorageService(
                submissionRoot.toString(), assignmentRoot.toString());
        MockMultipartFile file = new MockMultipartFile("file", "exam.pdf", "application/pdf",
                "%PDF-1.4 assignment".getBytes());

        var stored = service.storeAssignmentPdf(file, 10L);

        assertTrue(Path.of(stored.path()).startsWith(assignmentRoot));
        assertTrue(Files.exists(Path.of(stored.path())));
    }
}
