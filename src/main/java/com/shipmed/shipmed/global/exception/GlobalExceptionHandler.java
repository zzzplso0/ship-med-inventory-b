package com.shipmed.shipmed.global.exception;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    // 코드에서 직접 던진 BusinessException. 상태와 코드는 ErrorCode를 따른다.
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handlerBusinessException(BusinessException ex) {
        return ResponseEntity.status(ex.getErrorCode().getStatus())
                .body(ErrorResponse.from(ex));
    }

    // @Valid, @RequestBody 검증 실패 → 400. 필드별 오류를 fieldErrors로 묶어 응답한다.
    @Override
    protected @Nullable ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, Object> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach((fieldError) -> {
            errors.put(fieldError.getField(), fieldError.getDefaultMessage());
        });

        return ResponseEntity.status(status)
                .headers(headers)
                .body(ErrorResponse.of(ErrorCode.INVALID_INPUT, Map.of("fieldErrors", errors)));
    }

    // 나머지 Spring MVC 예외(잘못된 JSON·타입, 파라미터 누락, 404, 405 등). 상태는 유지하고 본문만 공통 형식으로 바꾼다.
    @Override
    protected @Nullable ResponseEntity<Object> handleExceptionInternal(Exception ex, @Nullable Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        if (statusCode.is5xxServerError()) {
            log.error("Unhandled mvc exception", ex);
        }

        ErrorResponse response = ErrorResponse.of(toErrorCode(statusCode), Map.of());
        return super.handleExceptionInternal(ex, response, headers, statusCode, request);
    }

    // 위에서 처리되지 않은 모든 예외 → 500. 원인은 로그에만 남기고 응답에는 노출하지 않는다.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handlerException(Exception ex) {
        log.error("Unhandled exception", ex);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of(ErrorCode.INTERNAL_SERVER_ERROR, Map.of()));
    }

    private ErrorCode toErrorCode(HttpStatusCode status) {
        return switch (status.value()) {
            case 404 -> ErrorCode.RESOURCE_NOT_FOUND;
            case 405 -> ErrorCode.METHOD_NOT_ALLOWED;
            default -> status.is4xxClientError() ? ErrorCode.INVALID_INPUT : ErrorCode.INTERNAL_SERVER_ERROR;
        };
    }
}
