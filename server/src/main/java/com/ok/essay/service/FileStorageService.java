package com.ok.essay.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FileStorageService {
    private static final long MAX_SIZE = 10L * 1024 * 1024;
    private final Path submissionRoot;
    private final Path assignmentRoot;

    @Autowired
    public FileStorageService(
            @Value("${app.storage.essay-path}") String submissionRootPath,
            @Value("${app.storage.assignment-path}") String assignmentRootPath
    ) {
        this.submissionRoot = Path.of(submissionRootPath).toAbsolutePath().normalize();
        this.assignmentRoot = Path.of(assignmentRootPath).toAbsolutePath().normalize();
    }

    FileStorageService(String rootPath) {
        this(rootPath, rootPath);
    }

    public StoredFile storeSubmissionPdf(MultipartFile file, Long examId, Long studentId) {
        return storePdf(file, submissionRoot, examId.toString(), studentId.toString());
    }

    public StoredFile storeAssignmentPdf(MultipartFile file, Long examId) {
        return storePdf(file, assignmentRoot, examId.toString());
    }

    private StoredFile storePdf(MultipartFile file, Path root, String... directories) {
        validatePdf(file);
        String originalName = safeOriginalName(file.getOriginalFilename());
        String storedName = UUID.randomUUID() + ".pdf";
        Path directory = root;
        for (String part : directories) directory = directory.resolve(part);
        directory = directory.normalize();
        Path target = directory.resolve(storedName).normalize();
        if (!target.startsWith(root)) throw invalid("Đường dẫn file không hợp lệ");
        try {
            Files.createDirectories(directory);
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return new StoredFile(originalName, storedName, target.toString(), file.getSize());
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Không thể lưu file PDF", exception);
        }
    }

    public Resource loadSubmission(String storagePath) {
        return load(storagePath, submissionRoot);
    }

    public Resource loadAssignment(String storagePath) {
        return load(storagePath, assignmentRoot);
    }

    private Resource load(String storagePath, Path root) {
        try {
            Path path = Path.of(storagePath).toAbsolutePath().normalize();
            if (!path.startsWith(root) || !Files.isRegularFile(path)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy file PDF");
            }
            return new UrlResource(path.toUri());
        } catch (java.net.MalformedURLException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy file PDF", exception);
        }
    }

    public void deleteSubmissionQuietly(String storagePath) {
        deleteQuietly(storagePath, submissionRoot);
    }

    public void deleteAssignmentQuietly(String storagePath) {
        deleteQuietly(storagePath, assignmentRoot);
    }

    private void deleteQuietly(String storagePath, Path root) {
        try {
            Path path = Path.of(storagePath).toAbsolutePath().normalize();
            if (path.startsWith(root)) Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }

    private void validatePdf(MultipartFile file) {
        if (file == null || file.isEmpty()) throw invalid("File PDF không được để trống");
        if (file.getSize() > MAX_SIZE) throw invalid("File PDF không được vượt quá 10 MB");
        String name = safeOriginalName(file.getOriginalFilename()).toLowerCase();
        if (!name.endsWith(".pdf")) throw invalid("Chỉ chấp nhận file PDF");
        try (InputStream input = file.getInputStream()) {
            byte[] signature = input.readNBytes(5);
            if (signature.length != 5 || signature[0] != '%' || signature[1] != 'P'
                    || signature[2] != 'D' || signature[3] != 'F' || signature[4] != '-') {
                throw invalid("Nội dung file không phải PDF hợp lệ");
            }
        } catch (IOException exception) {
            throw invalid("Không thể đọc file PDF");
        }
    }

    private String safeOriginalName(String name) {
        if (name == null || name.isBlank()) return "submission.pdf";
        String normalized = name.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        String fileName = slash >= 0 ? normalized.substring(slash + 1) : normalized;
        return fileName.replaceAll("[\\r\\n\\t]", "_");
    }

    private ResponseStatusException invalid(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    public record StoredFile(String originalName, String storedName, String path, long size) {}
}
