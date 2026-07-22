package com.ok.essay.controller;

import java.nio.charset.StandardCharsets;
import java.security.Principal;

import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.ok.dto.EssayAssignmentFileResponse;
import com.ok.essay.service.EssayAssignmentFileService;
import com.ok.essay.service.EssayAssignmentFileService.DownloadedAssignment;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/exams/{examId}/essay-assignment-file")
@RequiredArgsConstructor
public class EssayAssignmentFileController {
    private final EssayAssignmentFileService service;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public EssayAssignmentFileResponse upload(@PathVariable Long examId,
                                               @RequestPart("file") MultipartFile file,
                                               Principal principal) {
        return service.upload(examId, file, principal.getName());
    }

    @GetMapping("/info")
    public EssayAssignmentFileResponse info(@PathVariable Long examId, Principal principal) {
        return service.getInfo(examId, principal.getName());
    }

    @GetMapping
    public ResponseEntity<Resource> download(@PathVariable Long examId, Principal principal) {
        DownloadedAssignment download = service.download(examId, principal.getName());
        ContentDisposition disposition = ContentDisposition.inline()
                .filename(download.fileName(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(download.resource());
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long examId, Principal principal) {
        service.delete(examId, principal.getName());
    }
}
