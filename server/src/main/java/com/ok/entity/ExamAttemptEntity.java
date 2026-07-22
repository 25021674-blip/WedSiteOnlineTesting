package com.ok.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.ok.dto.ExamAttemptStatusDemo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Getter
@Entity
@Table(name = "exam_attempts")
public class ExamAttemptEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exam_id", nullable = false)
    private ExamEntity exam;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private UserEntity student;

    @Column(name = "started_at", nullable = false, updatable = false)
    private LocalDateTime startedAt;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ExamAttemptStatusDemo status;

    @Column(name = "screen_exit_count", nullable = false)
    private Integer screenExitCount = 0;

    @Column(precision = 5, scale = 2)
    private BigDecimal score;

    public ExamAttemptEntity(ExamEntity exam, UserEntity student) {
        this.exam = exam;
        this.student = student;
        this.startedAt = LocalDateTime.now();
        this.status = ExamAttemptStatusDemo.IN_PROGRESS;
    }
}
