package com.ok.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.ok.dto.ExamType;

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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Getter
@Entity
@Table(name = "exams")
public class ExamEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "teacher_id", nullable = false)
    private UserEntity teacher;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "duration_minutes", nullable = false)
    private Integer durationMinutes;

    @Column(name = "max_score", nullable = false, precision = 10, scale = 2)
    private BigDecimal maxScore;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ExamType type;

    public ExamEntity(
            UserEntity teacher,
            String title,
            String description,
            LocalDateTime startAt,
            LocalDateTime expiresAt,
            Integer durationMinutes,
            BigDecimal maxScore,
            ExamType type
    ) {
        this.teacher = teacher;
        this.title = title;
        this.description = description;
        this.startAt = startAt;
        this.expiresAt = expiresAt;
        this.durationMinutes = durationMinutes;
        this.maxScore = maxScore;
        this.type = type;
    }

    @PrePersist
    private void setCreatedAt() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
