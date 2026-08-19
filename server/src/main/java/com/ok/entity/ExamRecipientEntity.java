package com.ok.entity;

import java.time.Instant;

import jakarta.persistence.Id;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
    name = "exam_recipients",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_recipient_exam_student",
        columnNames = {"exam_id", "student_id"}
    )
)
public class ExamRecipientEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exam_id", nullable = false)
    private ExamEntity exam;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private UserEntity student;

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt;

    public ExamRecipientEntity(ExamEntity exam, UserEntity student) {
        this.exam = exam;
        this.student = student;
    }

    @PrePersist
    private void assignTimestamp() {
        if (assignedAt == null) {
            assignedAt = Instant.now();
        }
    }
}
