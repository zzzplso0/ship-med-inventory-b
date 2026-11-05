package com.shipmed.shipmed.global.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 경로를 찾을 수 없습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 HTTP 메서드입니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "예상치 못한 오류가 발생했습니다.");

    private final int status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status.value();
        this.message = message;
    }
}
