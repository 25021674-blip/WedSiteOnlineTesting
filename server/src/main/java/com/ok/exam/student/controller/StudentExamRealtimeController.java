package com.ok.exam.student.controller;

import java.security.Principal;
import java.time.Instant;

import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.server.ResponseStatusException;

import com.ok.dto.request.student.RecordAttemptViolationRequest;
import com.ok.dto.request.student.SaveStudentAnswerRequest;
import com.ok.dto.response.student.AttemptViolationResponse;
import com.ok.dto.response.student.SaveStudentAnswerResponse;
import com.ok.dto.response.student.StudentExamWebSocketErrorResponse;
import com.ok.dto.response.student.StudentHeartbeatResponse;
import com.ok.exam.student.service.StudentAnswerSaveService;
import com.ok.exam.student.service.StudentExamRealtimeService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class StudentExamRealtimeController {

    private static final String QUEUE_PREFIX =
            "/queue/exam-attempts/";

    private final StudentAnswerSaveService answerSaveService;
    private final StudentExamRealtimeService realtimeService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping(
            "/student/exam-attempts/{attemptId}"
                    + "/questions/{questionId}/answer"
    )
    public void autosaveAnswer(
            @DestinationVariable Long attemptId,
            @DestinationVariable Long questionId,
            @Valid @Payload SaveStudentAnswerRequest request,
            Principal principal
    ) {
        String email = getAuthenticatedEmail(principal);
        SaveStudentAnswerResponse response =
                answerSaveService.saveAnswer(
                        attemptId,
                        questionId,
                        request,
                        email
                );

        sendToUser(
                email,
                attemptId,
                "answers",
                response
        );
    }

    @MessageMapping(
            "/student/exam-attempts/{attemptId}/heartbeat"
    )
    public void heartbeat(
            @DestinationVariable Long attemptId,
            Principal principal
    ) {
        String email = getAuthenticatedEmail(principal);
        StudentHeartbeatResponse response =
                realtimeService.recordHeartbeat(
                        attemptId,
                        email
                );

        sendToUser(
                email,
                attemptId,
                "heartbeat",
                response
        );
    }

    @MessageMapping(
            "/student/exam-attempts/{attemptId}/violations"
    )
    public void recordViolation(
            @DestinationVariable Long attemptId,
            @Valid @Payload
            RecordAttemptViolationRequest request,
            Principal principal
    ) {
        String email = getAuthenticatedEmail(principal);
        AttemptViolationResponse response =
                realtimeService.recordViolation(
                        attemptId,
                        request,
                        email
                );

        sendToUser(
                email,
                attemptId,
                "violations",
                response
        );
    }

    @MessageExceptionHandler(ResponseStatusException.class)
    public void handleResponseStatusException(
            ResponseStatusException exception,
            Principal principal
    ) {
        String email = getAuthenticatedEmail(principal);
        String message = exception.getReason() == null
                ? "Không thể xử lý WebSocket message"
                : exception.getReason();

        messagingTemplate.convertAndSendToUser(
                email,
                QUEUE_PREFIX + "errors",
                new StudentExamWebSocketErrorResponse(
                        exception.getStatusCode().value(),
                        message,
                        Instant.now()
                )
        );
    }

    private void sendToUser(
            String email,
            Long attemptId,
            String eventName,
            Object payload
    ) {
        messagingTemplate.convertAndSendToUser(
                email,
                QUEUE_PREFIX + attemptId + "/" + eventName,
                payload
        );
    }

    private String getAuthenticatedEmail(Principal principal) {
        return principal == null
                ? null
                : principal.getName();
    }
}
