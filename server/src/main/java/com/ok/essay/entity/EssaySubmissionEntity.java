package com.ok.essay.entity;

import java.time.LocalDateTime;
import com.ok.entity.ExamEntity;
import com.ok.entity.UserEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Entity
@Table(name = "essay_submissions", uniqueConstraints = @UniqueConstraint(
        columnNames = {"exam_id", "student_id"}
))
public class EssaySubmissionEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exam_id", nullable = false)
    private ExamEntity exam;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private UserEntity student;

    @Column(nullable = false, length = 255)
    private String originalFileName;

    @Column(nullable = false, unique = true, length = 100)
    private String storedFileName;

    @Column(nullable = false, length = 1000)
    private String storagePath;

    @Column(nullable = false)
    private Long fileSize;

    @Column(nullable = false)
    private LocalDateTime submittedAt;

    private Double score;

    @Column(length = 2000)
    private String feedback;

    public EssaySubmissionEntity(ExamEntity exam, UserEntity student, String originalFileName,
                                 String storedFileName, String storagePath, long fileSize) {
        this.exam = exam;
        this.student = student;
        this.originalFileName = originalFileName;
        this.storedFileName = storedFileName;
        this.storagePath = storagePath;
        this.fileSize = fileSize;
        this.submittedAt = LocalDateTime.now();
    }

    public void grade(double score, String feedback) {
        this.score = score;
        this.feedback = feedback;
    }
}
