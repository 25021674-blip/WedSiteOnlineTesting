package com.ok.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import com.ok.domain.enums.ExamStatus;
import com.ok.domain.enums.ExamType;

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
    private Instant createdAt;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Column(name = "max_score", nullable = false, precision = 10, scale = 2)
    private BigDecimal maxScore;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ExamType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ExamStatus status = ExamStatus.DRAFT;

    public ExamEntity(
            UserEntity teacher,
            String title,
            String description,
            Instant startAt,
            Instant expiresAt,
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

    public UserEntity getCreatedBy() {
        return teacher;
    }

    public LocalDateTime getStartTime() {
        return startAt == null ? null : LocalDateTime.ofInstant(startAt, ZoneId.systemDefault());
    }

    public LocalDateTime getDeadline() {
        return expiresAt == null ? null : LocalDateTime.ofInstant(expiresAt, ZoneId.systemDefault());
    }

    public LocalDateTime getCreatedAt() {
        return createdAt == null ? null : LocalDateTime.ofInstant(createdAt, ZoneId.systemDefault());
    }

    public void update(
            String title,
            String description,
            LocalDateTime startTime,
            LocalDateTime deadline,
            Integer durationMinutes
    ) {
        ZoneId zoneId = ZoneId.systemDefault();
        this.title = title;
        this.description = description;
        this.startAt = startTime.atZone(zoneId).toInstant();
        this.expiresAt = deadline.atZone(zoneId).toInstant();
        this.durationMinutes = durationMinutes;
    }

    public void changeStatus(ExamStatus newStatus){
        this.status=newStatus;
    }

    @PrePersist
    private void setCreatedAt() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
