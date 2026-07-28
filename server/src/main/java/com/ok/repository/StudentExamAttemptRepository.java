package com.ok.repository;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ok.domain.enums.ExamAttemptStatus;
import com.ok.entity.ExamAttemptEntity;

import jakarta.persistence.LockModeType;

public interface StudentExamAttemptRepository
        extends JpaRepository<ExamAttemptEntity, Long> {

    Optional<ExamAttemptEntity>
            findFirstByExam_IdAndStudent_IdOrderByStartedAtDesc(
                    Long examId,
                    Long studentId
            );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"exam", "student"})
    @Query("""
            SELECT attempt
            FROM ExamAttemptEntity attempt
            WHERE attempt.id = :attemptId
              AND LOWER(attempt.student.email) = LOWER(:studentEmail)
            """)
    Optional<ExamAttemptEntity> findOwnedByIdForUpdate(
            @Param("attemptId") Long attemptId,
            @Param("studentEmail") String studentEmail
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE ExamAttemptEntity attempt
            SET attempt.status = :newStatus,
                attempt.submittedAt = :submittedAt,
                attempt.version = attempt.version + 1
            WHERE attempt.id = :attemptId
              AND attempt.status = :expectedStatus
            """)
    int markAutoSubmitted(
            @Param("attemptId") Long attemptId,
            @Param("expectedStatus") ExamAttemptStatus expectedStatus,
            @Param("newStatus") ExamAttemptStatus newStatus,
            @Param("submittedAt") Instant submittedAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE ExamAttemptEntity attempt
            SET attempt.status = :newStatus,
                attempt.submittedAt = :serverTime,
                attempt.version = attempt.version + 1
            WHERE attempt.status = :expectedStatus
              AND (
                    attempt.deadlineAt <= :serverTime
                    OR EXISTS (
                        SELECT exam.id
                        FROM ExamEntity exam
                        WHERE exam = attempt.exam
                          AND exam.expiresAt <= :serverTime
                    )
              )
            """)
    int markExpiredAttemptsAutoSubmitted(
            @Param("expectedStatus") ExamAttemptStatus expectedStatus,
            @Param("newStatus") ExamAttemptStatus newStatus,
            @Param("serverTime") Instant serverTime
    );
}
