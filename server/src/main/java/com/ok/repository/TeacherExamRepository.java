package com.ok.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.ok.entity.ExamEntity;

public interface TeacherExamRepository
        extends Repository<ExamEntity, Long> {

    @Query("""
            SELECT
                exam.id AS examId,
                exam.title AS title,
                COUNT(DISTINCT attempt.student.id) AS completedStudentCount
            FROM ExamEntity exam
            LEFT JOIN ExamAttemptEntity attempt
                ON attempt.exam = exam
                AND attempt.submittedAt IS NOT NULL
            WHERE exam.teacher.id = :teacherId
            GROUP BY exam.id, exam.title, exam.createdAt
            ORDER BY exam.createdAt DESC
            """)
    List<TeacherExamSummaryView> findSummariesByTeacherId(
            @Param("teacherId") Long teacherId
    );

    @Query("""
            SELECT
                exam.id AS examId,
                exam.title AS title,
                exam.type AS type,
                COUNT(DISTINCT attempt.student.id) AS completedStudentCount,
                exam.createdAt AS createdAt,
                exam.expiresAt AS expiresAt,
                exam.durationMinutes AS durationMinutes,
                exam.maxScore AS maxScore
            FROM ExamEntity exam
            LEFT JOIN ExamAttemptEntity attempt
                ON attempt.exam = exam
                AND attempt.submittedAt IS NOT NULL
            WHERE exam.id = :examId
                AND exam.teacher.id = :teacherId
            GROUP BY
                exam.id,
                exam.title,
                exam.type,
                exam.createdAt,
                exam.expiresAt,
                exam.durationMinutes,
                exam.maxScore
            """)
    Optional<TeacherExamDetailView> findDetailByExamIdAndTeacherId(
            @Param("examId") Long examId,
            @Param("teacherId") Long teacherId
    );

    @Query("""
            SELECT
                attempt.id AS attemptId,
                attempt.student.id AS studentId,
                attempt.student.fullName AS studentName
            FROM ExamAttemptEntity attempt
            WHERE attempt.exam.id = :examId
                AND attempt.exam.teacher.id = :teacherId
                AND attempt.submittedAt IS NOT NULL
            ORDER BY attempt.student.fullName ASC, attempt.student.id ASC
            """)
    List<TeacherExamSubmissionViewDemo> findSubmissionsByExamIdAndTeacherId(
            @Param("examId") Long examId,
            @Param("teacherId") Long teacherId
    );
}
