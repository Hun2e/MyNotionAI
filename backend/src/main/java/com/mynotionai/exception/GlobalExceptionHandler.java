package com.mynotionai.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Spring MVC 예외 처리 경로의 예외를 {code, message, timestamp} 공통 포맷으로 변환한다.
 * ResponseEntityExceptionHandler를 상속해 Spring MVC 기본 예외(잘못된 JSON, 파라미터 누락,
 * ResponseStatusException 등)도 원래 상태 코드를 유지한 채 같은 포맷으로 응답한다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiErrorResponse> handleBusiness(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();
        return ResponseEntity.status(errorCode.getStatus())
            .body(ApiErrorResponse.of(errorCode.name(), errorCode.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception e) {
        log.error("Unexpected error", e);
        ErrorCode errorCode = ErrorCode.INTERNAL_ERROR;
        return ResponseEntity.status(errorCode.getStatus())
            .body(ApiErrorResponse.of(errorCode.name(), errorCode.getMessage()));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
        MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request
    ) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
            .forEach(error -> fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage()));

        ErrorCode errorCode = ErrorCode.INVALID_INPUT;
        ApiErrorResponse body = ApiErrorResponse.builder()
            .code(errorCode.name())
            .message(errorCode.getMessage())
            .timestamp(LocalDateTime.now())
            .fieldErrors(fieldErrors)
            .build();
        return ResponseEntity.status(errorCode.getStatus()).headers(headers).body(body);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
        Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request
    ) {
        HttpStatus status = HttpStatus.resolve(statusCode.value());
        String code = status != null ? status.name() : "HTTP_" + statusCode.value();
        String message;
        if (statusCode.is5xxServerError()) {
            log.error("Spring MVC 요청 처리 중 서버 오류 발생", ex);
            message = ErrorCode.INTERNAL_ERROR.getMessage();
        } else {
            String fallbackMessage = status != null ? status.getReasonPhrase() : code;
            message = resolveClientMessage(ex, body, fallbackMessage);
        }
        return ResponseEntity.status(statusCode).headers(headers)
            .body(ApiErrorResponse.of(code, message));
    }

    private String resolveClientMessage(Exception ex, Object body, String fallbackMessage) {
        if (ex instanceof HttpMessageNotReadableException) {
            return ErrorCode.INVALID_INPUT.getMessage();
        }
        if (body instanceof ProblemDetail problem && problem.getDetail() != null) {
            return problem.getDetail();
        }
        if (ex instanceof ErrorResponse springError && springError.getBody().getDetail() != null) {
            return springError.getBody().getDetail();
        }
        return fallbackMessage;
    }
}
