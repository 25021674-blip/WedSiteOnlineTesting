package com.ok.entity;

import java.time.Instant;

import com.ok.domain.enums.AttemptViolationType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Getter
@Entity
@Table(
        name = "attempt_violations",
        indexes = @Index(
                name = "idx_attempt_violation_attempt_occurred",
                columnList = "attempt_id, occurred_at"
        )
)
public class AttemptViolationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attempt_id", nullable = false)
    private ExamAttemptEntity attempt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AttemptViolationType type;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "client_time", nullable = false, updatable = false)
    private Instant clientTime;

    @Column(columnDefinition = "TEXT")
    private String metadata;

    public AttemptViolationEntity(
            ExamAttemptEntity attempt,
            AttemptViolationType type,
            Instant clientTime,
            String metadata
    ) {
        this.attempt = attempt;
        this.type = type;
        this.clientTime = clientTime;
        this.metadata = metadata;
    }

    @PrePersist
    private void setOccurredAt() {
        if (occurredAt == null) {
            occurredAt = Instant.now();
        }
    }
}
