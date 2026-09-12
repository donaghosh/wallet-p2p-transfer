package com.paytm.wallet.exception;

import com.paytm.wallet.constants.ErrorCode;
import com.paytm.wallet.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Translates exceptions into the standard {@link ErrorResponse} contract. Expected 4xx
 * are logged at WARN without stack traces; unexpected 5xx at ERROR with the stack and a
 * sanitized client message.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String TRACE_ID_KEY = "traceId";

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApi(ApiException ex, HttpServletRequest request) {
        ErrorCode code = ex.getErrorCode();
        log.warn("api_error code={} status={} message={}", code, code.httpStatus().value(), ex.getMessage());
        return build(code, ex.getMessage(), request, null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<ErrorResponse.FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ErrorResponse.FieldError(fe.getField(), fe.getDefaultMessage()))
                .toList();
        log.warn("validation_error fields={}", fieldErrors);
        return build(ErrorCode.VALIDATION_ERROR, "Request validation failed", request, fieldErrors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        log.warn("malformed_json message={}", ex.getMostSpecificCause().getMessage());
        return build(ErrorCode.MALFORMED_JSON, "Malformed or unreadable request body", request, null);
    }

    /**
     * Backstop for a unique/constraint violation that slips past the service-level guard
     * (e.g. a race the app did not pre-check). Mapped to 409 rather than a 500.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(
            DataIntegrityViolationException ex, HttpServletRequest request) {
        log.warn("data_integrity_violation message={}", ex.getMostSpecificCause().getMessage());
        return build(ErrorCode.IDEMPOTENCY_CONFLICT, "Request conflicts with existing state", request, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("internal_error path={}", request.getRequestURI(), ex);
        return build(ErrorCode.INTERNAL_ERROR, "Internal server error", request, null);
    }

    private ResponseEntity<ErrorResponse> build(
            ErrorCode code, String message, HttpServletRequest request,
            List<ErrorResponse.FieldError> fieldErrors) {
        HttpStatus status = code.httpStatus();
        ErrorResponse body = new ErrorResponse(
                Instant.now(),
                status.value(),
                code.name(),
                message,
                request.getRequestURI(),
                MDC.get(TRACE_ID_KEY),
                fieldErrors);
        return ResponseEntity.status(status).body(body);
    }
}
