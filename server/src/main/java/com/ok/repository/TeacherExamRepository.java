package com.ok.repository;

import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.ok.entity.ExamEntity;

public interface TeacherExamRepositoryDemo
        extends Repository<ExamEntity, Long> {

    @Query("""
            SELECT
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
    List<TeacherExamSummaryViewDemo> findSummariesByTeacherId(
            @Param("teacherId") Long teacherId
    );
}
