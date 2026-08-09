package com.ok.exam.student.scheduler;

import java.time.Clock;
import java.time.Instant;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ok.domain.enums.ExamAttemptStatus;
import com.ok.exam.student.service.ExamAttemptCompletionService;
import com.ok.repository.StudentExamAttemptRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ExamAttemptAutoSubmitScheduler {

    private final StudentExamAttemptRepository attemptRepository;
    private final ExamAttemptCompletionService completionService;
    private final Clock clock;

    @Scheduled(
            fixedDelayString =
                    "${app.exam.auto-submit-delay-ms:1000}"
    )
    public void autoSubmitExpiredAttempts() {
        Instant serverTime = clock.instant();
        attemptRepository.findExpiredAttemptIds(
                ExamAttemptStatus.IN_PROGRESS,
                serverTime
        ).forEach(attemptId -> completionService
                .autoSubmitExpiredAttempt(attemptId, serverTime));
    }
}
