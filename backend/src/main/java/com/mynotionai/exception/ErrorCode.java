package com.mynotionai.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 공통
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "Invalid input"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error"),

    // 인증/사용자
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "User not found"),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "Email already exists"),
    EMAIL_REGISTERED_AS_LOCAL(HttpStatus.CONFLICT, "This email is already registered with a local account"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Invalid credentials"),

    // 일정
    SCHEDULE_NOT_FOUND(HttpStatus.NOT_FOUND, "Schedule not found"),
    INVALID_MONTH(HttpStatus.BAD_REQUEST, "month must be between 1 and 12"),
    INVALID_TIME_RANGE(HttpStatus.BAD_REQUEST, "startAt must be before endAt");

    private final HttpStatus status;
    private final String message;
}
