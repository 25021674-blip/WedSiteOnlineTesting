package com.ok.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

import com.ok.domain.enums.ExamAttemptStatus;

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
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Getter
@Entity
@Table(
    name = "exam_attempts",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_attempt_exam_student_number",
        columnNames = {"exam_id", "student_id", "attempt_number"}
    )
)
public class ExamAttemptEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exam_id", nullable = false)
    private ExamEntity exam;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private UserEntity student;

    @Column(name = "attempt_number", nullable = false, updatable = false)
    private Integer attemptNumber = 1;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "deadline_at", nullable = false, updatable = false)
    private Instant deadlineAt;

    @Column(name = "last_heartbeat_at", nullable = false)
    private Instant lastHeartbeatAt;

    @Column(name = "last_activity_at", nullable = false)
    private Instant lastActivityAt;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ExamAttemptStatus status;

    @Column(name = "screen_exit_count", nullable = false)
    private Integer screenExitCount = 0;

    @Column(precision = 10, scale = 2)
    private BigDecimal score;

    public ExamAttemptEntity(
            ExamEntity exam,
            UserEntity student,
            Instant deadlineAt
    ) {
        this(exam, student, 1, deadlineAt);
    }

    public ExamAttemptEntity(
            ExamEntity exam,
            UserEntity student,
            Integer attemptNumber,
            Instant deadlineAt
    ) {
        this.exam = exam;
        this.student = student;
        this.attemptNumber = Objects.requireNonNull(attemptNumber);
        this.startedAt = Instant.now();
        this.deadlineAt = deadlineAt;
        this.lastHeartbeatAt = Instant.now();
        this.lastActivityAt = this.lastHeartbeatAt;
        this.status = ExamAttemptStatus.IN_PROGRESS;
    }

    public void recordActivity(Instant activityAt) {
        Instant newActivityAt = Objects.requireNonNull(activityAt);

        if (lastActivityAt == null
                || newActivityAt.isAfter(lastActivityAt)) {
            this.lastActivityAt = newActivityAt;
        }
    }

    public void recordHeartbeat(Instant heartbeatAt) {
        Instant newHeartbeatAt = Objects.requireNonNull(heartbeatAt);

        if (lastHeartbeatAt == null
                || newHeartbeatAt.isAfter(lastHeartbeatAt)) {
            this.lastHeartbeatAt = newHeartbeatAt;
        }
    }

    public int recordViolation(Instant activityAt) {
        int currentCount = screenExitCount == null
                ? 0
                : screenExitCount;

        this.screenExitCount = currentCount + 1;
        recordActivity(activityAt);
        return this.screenExitCount;
    }

    public void autoSubmit(Instant submittedAt) {
        if (status != ExamAttemptStatus.IN_PROGRESS) {
            return;
        }

        this.status = ExamAttemptStatus.AUTO_SUBMITTED;
        this.submittedAt = Objects.requireNonNull(submittedAt);
    }

    public void submit(Instant submittedAt) {
        if (status != ExamAttemptStatus.IN_PROGRESS) {
            return;
        }

        Instant submissionTime = Objects.requireNonNull(submittedAt);
        this.status = ExamAttemptStatus.SUBMITTED;
        this.submittedAt = submissionTime;
        recordActivity(submissionTime);
    }

    public void assignScore(BigDecimal score) {
        this.score = score;
    }
}
