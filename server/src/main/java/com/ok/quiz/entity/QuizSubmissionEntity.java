package com.ok.quiz.entity;

import java.time.LocalDateTime;
import com.ok.dto.QuizAttemptStatus;
import com.ok.entity.ExamEntity;
import com.ok.entity.UserEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Entity
@Table(name = "quiz_submissions", uniqueConstraints = @UniqueConstraint(
        columnNames = {"exam_id", "student_id"}
))
public class QuizSubmissionEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exam_id", nullable = false)
    private ExamEntity exam;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private UserEntity student;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private QuizAttemptStatus status;

    @Column(nullable = false, updatable = false)
    private LocalDateTime startedAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime expiresAt;

    private Double score;

    private Double totalPoints;

    private LocalDateTime submittedAt;

    @Version
    private Long version;

    public QuizSubmissionEntity(ExamEntity exam, UserEntity student,
            LocalDateTime startedAt, LocalDateTime expiresAt) {
        this.exam = exam;
        this.student = student;
        this.status = QuizAttemptStatus.IN_PROGRESS;
        this.startedAt = startedAt;
        this.expiresAt = expiresAt;
    }

    public void submit(double score, double totalPoints, LocalDateTime submittedAt) {
        this.status = QuizAttemptStatus.SUBMITTED;
        this.score = score;
        this.totalPoints = totalPoints;
        this.submittedAt = submittedAt;
    }

    public void autoSubmit(double score, double totalPoints) {
        this.status = QuizAttemptStatus.AUTO_SUBMITTED;
        this.score = score;
        this.totalPoints = totalPoints;
        this.submittedAt = expiresAt;
    }
}
