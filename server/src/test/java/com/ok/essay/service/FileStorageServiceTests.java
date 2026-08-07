package com.ok.essay.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Tag("unit")
@Tag("filesystem")
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

    @Test
    void rejectsEmptyOversizedAndWrongExtensionFiles() {
        FileStorageService service = new FileStorageService(tempDir.toString());
        MockMultipartFile empty = new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]);
        MockMultipartFile oversized = new MockMultipartFile("file", "large.pdf", "application/pdf",
                new byte[10 * 1024 * 1024 + 1]);
        MockMultipartFile wrongExtension = new MockMultipartFile("file", "answer.txt", "application/pdf",
                "%PDF-1.4".getBytes());

        assertThrows(ResponseStatusException.class, () -> service.storeSubmissionPdf(empty, 1L, 1L));
        assertThrows(ResponseStatusException.class, () -> service.storeSubmissionPdf(oversized, 1L, 1L));
        assertThrows(ResponseStatusException.class, () -> service.storeSubmissionPdf(wrongExtension, 1L, 1L));
    }

    @Test
    void sanitizesOriginalFilenameAndNeverUsesItAsStoredPath() {
        FileStorageService service = new FileStorageService(tempDir.toString());
        MockMultipartFile file = new MockMultipartFile("file", "../folder/answer\r\n.pdf", "application/pdf",
                "%PDF-1.4".getBytes());

        var stored = service.storeSubmissionPdf(file, 1L, 2L);

        assertThat(stored.originalName()).doesNotContain("/", "\\", "\r", "\n");
        assertThat(stored.storedName()).endsWith(".pdf").isNotEqualTo(stored.originalName());
        assertThat(Path.of(stored.path())).startsWith(tempDir);
    }

    @Test
    void refusesToLoadMissingFileOrPathOutsideConfiguredRoot() throws Exception {
        FileStorageService service = new FileStorageService(tempDir.resolve("root").toString());
        Path outside = tempDir.resolve("outside.pdf");
        Files.writeString(outside, "%PDF-1.4");

        assertThrows(ResponseStatusException.class, () -> service.loadSubmission(outside.toString()));
        assertThrows(ResponseStatusException.class,
                () -> service.loadSubmission(tempDir.resolve("root/missing.pdf").toString()));
    }

    @Test
    void deleteOnlyRemovesFilesInsideConfiguredRoot() throws Exception {
        Path root = tempDir.resolve("root");
        FileStorageService service = new FileStorageService(root.toString());
        var stored = service.storeSubmissionPdf(new MockMultipartFile("file", "a.pdf", "application/pdf",
                "%PDF-1.4".getBytes()), 1L, 1L);
        Path outside = tempDir.resolve("outside.pdf");
        Files.writeString(outside, "%PDF-1.4");

        service.deleteSubmissionQuietly(stored.path());
        service.deleteSubmissionQuietly(outside.toString());

        assertThat(Path.of(stored.path())).doesNotExist();
        assertThat(outside).exists();
    }
}
