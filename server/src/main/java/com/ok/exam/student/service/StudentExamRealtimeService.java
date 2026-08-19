package com.ok.exam.student.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.GONE;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import java.time.Duration;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.ok.domain.enums.ExamAttemptStatus;
import com.ok.domain.enums.ExamStatus;
import com.ok.domain.enums.AttemptViolationType;
import com.ok.domain.enums.Role;
import com.ok.dto.request.student.RecordAttemptViolationRequest;
import com.ok.dto.response.student.AttemptViolationResponse;
import com.ok.dto.response.student.StudentHeartbeatResponse;
import com.ok.entity.AttemptViolationEntity;
import com.ok.entity.ExamAttemptEntity;
import com.ok.repository.AttemptViolationRepository;
import com.ok.repository.StudentExamAttemptRepository;
import com.ok.exam.service.ExamService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class StudentExamRealtimeService {

    private final StudentExamAttemptRepository attemptRepository;
    private final AttemptViolationRepository violationRepository;
    private final ExamService examService;

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public StudentHeartbeatResponse recordHeartbeat(
            Long attemptId,
            String authenticatedEmail
    ) {
        validateIdentity(attemptId, authenticatedEmail);

        Instant serverTime = Instant.now();
        ExamAttemptEntity attempt = findActiveOwnedAttempt(
                attemptId,
                authenticatedEmail,
                serverTime
        );

        attempt.recordHeartbeat(serverTime);
        ExamAttemptEntity savedAttempt = attemptRepository
                .saveAndFlush(attempt);

        Instant effectiveDeadline = getEffectiveDeadline(savedAttempt);
        long remainingSeconds = Math.max(
                0L,
                Duration.between(
                        serverTime,
                        effectiveDeadline
                ).toSeconds()
        );

        return new StudentHeartbeatResponse(
                savedAttempt.getId(),
                savedAttempt.getStatus(),
                serverTime,
                savedAttempt.getLastHeartbeatAt(),
                remainingSeconds
        );
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public AttemptViolationResponse recordViolation(
            Long attemptId,
            RecordAttemptViolationRequest request,
            String authenticatedEmail
    ) {
        validateIdentity(attemptId, authenticatedEmail);
        validateViolationRequest(request);

        Instant serverTime = Instant.now();
        ExamAttemptEntity attempt = findActiveOwnedAttempt(
                attemptId,
                authenticatedEmail,
                serverTime
        );
        validateViolationPolicy(attempt, request.type());

        int screenExitCount = attempt.recordViolation(serverTime);
        AttemptViolationEntity violation =
                new AttemptViolationEntity(
                        attempt,
                        request.type(),
                        request.clientTime(),
                        request.metadata()
                );

        AttemptViolationEntity savedViolation =
                violationRepository.saveAndFlush(violation);

        return new AttemptViolationResponse(
                savedViolation.getId(),
                attempt.getId(),
                savedViolation.getType(),
                savedViolation.getClientTime(),
                savedViolation.getOccurredAt(),
                screenExitCount
        );
    }

    private void validateIdentity(
            Long attemptId,
            String authenticatedEmail
    ) {
        if (authenticatedEmail == null || authenticatedEmail.isBlank()) {
            throw new ResponseStatusException(
                    UNAUTHORIZED,
                    "WebSocket session chưa được xác thực"
            );
        }

        if (attemptId == null || attemptId <= 0) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "Mã lượt làm bài phải lớn hơn 0"
            );
        }
    }

    private void validateViolationRequest(
            RecordAttemptViolationRequest request
    ) {
        if (request == null
                || request.type() == null
                || request.clientTime() == null) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "Thông tin vi phạm không hợp lệ"
            );
        }

        if (request.metadata() != null
                && request.metadata().length() > 4000) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "Metadata không được vượt quá 4000 ký tự"
            );
        }
    }

    private void validateViolationPolicy(
            ExamAttemptEntity attempt,
            AttemptViolationType violationType
    ) {
        boolean enabled = switch (violationType) {
            case FULLSCREEN_EXIT -> attempt.getExam()
                    .isRequireFullscreen();
            case TAB_HIDDEN, WINDOW_BLUR, PAGE_LEAVE -> attempt
                    .getExam()
                    .isTrackTabSwitches();
        };

        if (!enabled) {
            throw new ResponseStatusException(
                    CONFLICT,
                    "Loại giám sát này chưa được bật cho bài kiểm tra"
            );
        }
    }

    private ExamAttemptEntity findActiveOwnedAttempt(
            Long attemptId,
            String authenticatedEmail,
            Instant serverTime
    ) {
        ExamAttemptEntity attempt = attemptRepository
                .findOwnedByIdForUpdate(
                        attemptId,
                        authenticatedEmail
                )
                .orElseThrow(() -> new ResponseStatusException(
                        NOT_FOUND,
                        "Không tìm thấy lượt làm bài"
                ));

        if (attempt.getStudent().getRole() != Role.STUDENT) {
            throw new ResponseStatusException(
                    FORBIDDEN,
                    "Chỉ học sinh được gửi dữ liệu thời gian thực"
            );
        }

        examService.requireAssignedStudent(
                attempt.getExam(),
                attempt.getStudent()
        );

        if (attempt.getExam().getStatus() != ExamStatus.PUBLISHED) {
            attempt.autoSubmit(serverTime);
            throw new ResponseStatusException(
                    GONE,
                    "Bài kiểm tra đã đóng"
            );
        }

        if (attempt.getStatus() != ExamAttemptStatus.IN_PROGRESS) {
            throw new ResponseStatusException(
                    CONFLICT,
                    "Lượt làm bài không còn ở trạng thái đang thực hiện"
            );
        }

        if (!serverTime.isBefore(getEffectiveDeadline(attempt))) {
            attempt.autoSubmit(serverTime);

            throw new ResponseStatusException(
                    GONE,
                    "Lượt làm bài đã hết thời gian"
            );
        }

        return attempt;
    }

    private Instant getEffectiveDeadline(
            ExamAttemptEntity attempt
    ) {
        return attempt.getDeadlineAt()
                .isBefore(attempt.getExam().getExpiresAt())
                ? attempt.getDeadlineAt()
                : attempt.getExam().getExpiresAt();
    }
}
