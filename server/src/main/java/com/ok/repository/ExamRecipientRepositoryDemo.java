package com.ok.repository;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ok.domain.enums.ExamStatus;
import com.ok.entity.ExamEntity;
import com.ok.entity.ExamRecipientEntity;

public interface ExamRecipientRepositoryDemo
        extends JpaRepository<ExamRecipientEntity, Long> {

    @EntityGraph(attributePaths = "student")
    @Query("""
            SELECT recipient
            FROM ExamRecipientEntity recipient
            WHERE recipient.exam.id = :examId
            ORDER BY recipient.student.fullName ASC,
                recipient.student.id ASC
            """)
    List<ExamRecipientEntity>
            findByExam_IdOrderByStudent_FullNameAscStudent_IdAsc(
                    @Param("examId") Long examId
            );

    boolean existsByExam_Id(Long examId);

    boolean existsByExam_IdAndStudent_Id(Long examId, Long studentId);

    long countByExam_Id(Long examId);

    void deleteByExam_Id(Long examId);

    @Query("""
            SELECT recipient.exam
            FROM ExamRecipientEntity recipient
            WHERE recipient.student.id = :studentId
              AND recipient.exam.status = :status
            ORDER BY recipient.exam.createdAt DESC
            """)
    List<ExamEntity> findAssignedExams(
            @Param("studentId") Long studentId,
            @Param("status") ExamStatus status
    );
}
