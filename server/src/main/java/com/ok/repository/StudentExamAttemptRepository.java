package com.ok.repository;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"exam", "student"})
    @Query("""
            SELECT attempt
            FROM ExamAttemptEntity attempt
            WHERE attempt.id = :attemptId
            """)
    Optional<ExamAttemptEntity> findByIdForUpdate(
            @Param("attemptId") Long attemptId
    );

    @Query("""
            SELECT attempt.id
            FROM ExamAttemptEntity attempt
            WHERE attempt.status = :status
              AND (attempt.deadlineAt <= :serverTime OR attempt.exam.expiresAt <= :serverTime)
            """)
    java.util.List<Long> findExpiredAttemptIds(
            @Param("status") ExamAttemptStatus status,
            @Param("serverTime") Instant serverTime
    );

    @EntityGraph(attributePaths = {"exam", "student"})
    @Query("""
            SELECT attempt
            FROM ExamAttemptEntity attempt
            WHERE attempt.id = :attemptId
              AND attempt.exam.id = :examId
              AND attempt.exam.teacher.id = :teacherId
              AND attempt.submittedAt IS NOT NULL
            """)
    Optional<ExamAttemptEntity> findSubmittedForTeacherReview(
            @Param("examId") Long examId,
            @Param("attemptId") Long attemptId,
            @Param("teacherId") Long teacherId
    );
}
