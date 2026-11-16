package com.shipmed.shipmed.global.exception;

import com.fasterxml.jackson.annotation.JsonAnyGetter;

import java.util.Map;

public record ErrorResponse(
        String error,
        String message,
        @JsonAnyGetter Map<String, Object> data
) {
    public static ErrorResponse from(BusinessException exception) {
        return of(exception.getErrorCode(), exception.getData());
    }

    public static ErrorResponse of(ErrorCode errorCode, Map<String, Object> data) {
        return new ErrorResponse(errorCode.name(), errorCode.getMessage(), data);
    }
}
