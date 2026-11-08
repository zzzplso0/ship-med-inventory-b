package com.shipmed.shipmed.global.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    // auth
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "로그인 ID 또는 비밀번호가 올바르지 않습니다."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "해당 리소스 접근 권한이 없습니다."),

    // ship
    SHIP_NOT_FOUND(HttpStatus.NOT_FOUND, "선박을 찾을 수 없습니다."),

    // medication
    MEDICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "약품을 찾을 수 없습니다."),

    // user
    DUPLICATE_LOGIN_ID(HttpStatus.CONFLICT, "이미 사용 중인 로그인 ID입니다."),

    // stock_requirement
    REQUIREMENT_CONFLICT(HttpStatus.CONFLICT, "같은 요청이 이미 존재합니다. 다시 시도해 주세요."),

    // stock_lot
    EXPIRED_ON_ARRIVAL(HttpStatus.BAD_REQUEST, "유효기한이 지난 로트는 입고할 수 없습니다."),
    RECEIVED_AT_IN_FUTURE(HttpStatus.BAD_REQUEST, "입고 시각은 현재보다 미래일 수 없습니다."),
    DUPLICATE_LOT(HttpStatus.CONFLICT, "같은 약품에 이미 입고된 로트 번호입니다."),

    // server
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
