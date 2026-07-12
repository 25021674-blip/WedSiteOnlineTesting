package com.ok.auth.exceptionHandle;

import com.ok.dto.ErrorResponse;

import java.util.LinkedHashMap;

import static org.springframework.http.HttpStatus.resolve;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.security.core.AuthenticationException;

@RestControllerAdvice
public class AuthExceptionHandler {
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse>handleValidation(MethodArgumentNotValidException exception) {
        Map<String, String> errors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors()
                .forEach(error -> errors.putIfAbsent(error.getField(),error.getDefaultMessage()));
        return response(HttpStatus.BAD_REQUEST, "Dữ liệu không hợp lệ", errors);
    }

    private ResponseEntity<ErrorResponse> response(HttpStatus status, String message,
                                                       Map<String, String> errors) {
        return ResponseEntity.status(status)
                .body(new ErrorResponse(status.value(), message, Instant.now(), errors));
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ErrorResponse> handleResponseStatus(ResponseStatusException exception) {
        return response(HttpStatus.valueOf(exception.getStatusCode().value()), exception.getReason(), Map.of());
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication() {
        return response(
                HttpStatus.UNAUTHORIZED,
                "Email hoặc mật khẩu không đúng",
                Map.of()
        );
    }
}
