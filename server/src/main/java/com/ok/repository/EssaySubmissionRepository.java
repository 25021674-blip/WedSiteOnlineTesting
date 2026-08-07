package com.ok.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.ok.essay.entity.EssaySubmissionEntity;

public interface EssaySubmissionRepository extends JpaRepository<EssaySubmissionEntity, Long> {  //EssaySubmissionEntity chính là class ánh xạ tới bảng trong database, còn Long là kiểu dữ liệu của khóa chính (primary key)
    boolean existsByExamIdAndStudentId(Long examId, Long studentId);
    Optional<EssaySubmissionEntity> findByExamIdAndStudentId(Long examId, Long studentId);
    List<EssaySubmissionEntity> findByExamIdOrderBySubmittedAtDesc(Long examId);
}
