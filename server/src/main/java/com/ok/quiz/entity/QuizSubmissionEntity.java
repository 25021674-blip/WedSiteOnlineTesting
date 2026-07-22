package com.ok.quiz.entity;

import java.time.LocalDateTime;
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

    @Column(nullable = false)
    private Double score;

    @Column(nullable = false)
    private Double totalPoints;

    @Column(nullable = false)
    private LocalDateTime submittedAt;

    public QuizSubmissionEntity(ExamEntity exam, UserEntity student, double score, double totalPoints) {
        this.exam = exam;
        this.student = student;
        this.score = score;
        this.totalPoints = totalPoints;
        this.submittedAt = LocalDateTime.now();
    }
}
