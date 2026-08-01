package com.ok.exam.student.scheduler;

import java.time.Instant;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.ok.domain.enums.ExamAttemptStatus;
import com.ok.repository.StudentExamAttemptRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ExamAttemptAutoSubmitScheduler {

    private final StudentExamAttemptRepository attemptRepository;

    @Scheduled(
            fixedDelayString =
                    "${app.exam.auto-submit-delay-ms:1000}"
    )
    @Transactional
    public void autoSubmitExpiredAttempts() {
        attemptRepository.markExpiredAttemptsAutoSubmitted(
                ExamAttemptStatus.IN_PROGRESS,
                ExamAttemptStatus.AUTO_SUBMITTED,
                Instant.now()
        );
    }
}
