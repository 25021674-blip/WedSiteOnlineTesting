package com.ok.repository;

import com.ok.domain.enums.ExamStatus;
import com.ok.entity.ExamEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface ExamRepository extends JpaRepository<ExamEntity, Long> {
    List<ExamEntity> findByCreatedByIdOrderByCreatedAtDesc(Long userId);
    List<ExamEntity> findByStatusOrderByCreatedAtDesc(ExamStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT exam FROM ExamEntity exam WHERE exam.id = :examId")
    Optional<ExamEntity> findByIdForUpdate(@Param("examId") Long examId);
}
