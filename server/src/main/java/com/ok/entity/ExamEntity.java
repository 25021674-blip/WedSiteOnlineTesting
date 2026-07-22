package com.ok.entity;

import java.time.LocalDateTime;

import com.ok.dto.ExamStatus;
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

@Getter
@NoArgsConstructor
@Entity
@Table(name = "exams")
public class ExamEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ExamType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ExamStatus status;

    @Column(nullable = false)
    private LocalDateTime startTime;

    @Column(nullable = false)
    private LocalDateTime deadline;

    private Integer durationMinutes;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private UserEntity createdBy;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public ExamEntity(
            String title,
            String description,
            ExamType type,
            LocalDateTime startTime,
            LocalDateTime deadline,
            Integer durationMinutes,
            UserEntity createdBy
    ) {
        this.title = title;
        this.description = description;
        this.type = type;
        this.status = ExamStatus.DRAFT;
        this.startTime = startTime;
        this.deadline = deadline;
        this.durationMinutes = durationMinutes;
        this.createdBy = createdBy;
    }

    public void update(String title, String description, LocalDateTime startTime,
                       LocalDateTime deadline, Integer durationMinutes) {
        this.title = title;
        this.description = description;
        this.startTime = startTime;
        this.deadline = deadline;
        this.durationMinutes = durationMinutes;
    }

    public void changeStatus(ExamStatus status) {
        this.status = status;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = ExamStatus.DRAFT;
        }
    }
}
