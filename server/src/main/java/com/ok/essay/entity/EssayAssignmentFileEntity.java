package com.ok.essay.entity;

import java.time.LocalDateTime;

import com.ok.entity.ExamEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Entity
@Table(name = "essay_assignment_files")
public class EssayAssignmentFileEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exam_id", nullable = false, unique = true)
    private ExamEntity exam;

    @Column(nullable = false, length = 255)
    private String originalFileName;

    @Column(nullable = false, unique = true, length = 100)
    private String storedFileName;

    @Column(nullable = false, length = 1000)
    private String storagePath;

    @Column(nullable = false)
    private Long fileSize;

    @Column(nullable = false)
    private LocalDateTime uploadedAt;

    public EssayAssignmentFileEntity(ExamEntity exam, String originalFileName,
                                     String storedFileName, String storagePath, long fileSize) {
        this.exam = exam;
        replaceFile(originalFileName, storedFileName, storagePath, fileSize);
    }

    public void replaceFile(String originalFileName, String storedFileName,
                            String storagePath, long fileSize) {
        this.originalFileName = originalFileName;
        this.storedFileName = storedFileName;
        this.storagePath = storagePath;
        this.fileSize = fileSize;
        this.uploadedAt = LocalDateTime.now();
    }
}
