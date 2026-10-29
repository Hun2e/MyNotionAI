package com.mynotionai.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Map;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiErrorResponse {

    private final String code;
    private final String message;
    private final LocalDateTime timestamp;
    private final Map<String, String> fieldErrors;

    public static ApiErrorResponse of(String code, String message) {
        return ApiErrorResponse.builder()
            .code(code)
            .message(message)
            .timestamp(LocalDateTime.now())
            .build();
    }
}
