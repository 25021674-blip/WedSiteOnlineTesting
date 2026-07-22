package com.ok.essay.controller;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.List;

import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.ok.dto.EssaySubmissionResponse;
import com.ok.dto.GradeEssayRequest;
import com.ok.essay.service.EssaySubmissionService;
import com.ok.essay.service.EssaySubmissionService.DownloadedSubmission;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class EssaySubmissionController {
    private final EssaySubmissionService service;

    @PostMapping(value = "/api/exams/{examId}/essay-submissions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public EssaySubmissionResponse submit(@PathVariable Long examId, @RequestPart("file") MultipartFile file,
                                          Principal principal) {
        return service.submit(examId, file, principal.getName());
    }

    @GetMapping("/api/exams/{examId}/essay-submissions/me")
    public EssaySubmissionResponse mine(@PathVariable Long examId, Principal principal) {
        return service.getMine(examId, principal.getName());
    }

    @GetMapping("/api/exams/{examId}/essay-submissions")
    public List<EssaySubmissionResponse> list(@PathVariable Long examId, Principal principal) {
        return service.getForExam(examId, principal.getName());
    }

    @GetMapping("/api/essay-submissions/{id}/file")
    public ResponseEntity<Resource> download(@PathVariable Long id, Principal principal) {
        DownloadedSubmission download = service.download(id, principal.getName());
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(download.fileName(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString()).body(download.resource());
    }

    @PutMapping("/api/essay-submissions/{id}/grade")
    public EssaySubmissionResponse grade(@PathVariable Long id, @Valid @RequestBody GradeEssayRequest request,
                                         Principal principal) {
        return service.grade(id, request, principal.getName());
    }
}
