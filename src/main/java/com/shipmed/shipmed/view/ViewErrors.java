package com.shipmed.shipmed.view;

import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;

import java.util.HashMap;
import java.util.Map;

// 폼 검증 오류를 필드 이름 → 메시지로 바꾼다. 폼 DTO가 record라 th:errors 대신 이 Map을 화면에 넘긴다.
final class ViewErrors {

    private ViewErrors() {
    }

    static Map<String, String> fieldErrors(BindingResult bindingResult) {
        Map<String, String> errors = new HashMap<>();

        for (FieldError fieldError : bindingResult.getFieldErrors()) {
            // 타입 변환 실패의 기본 메시지에는 내부 타입 이름이 들어 있어 일반 문구로 바꾼다.
            String message = fieldError.isBindingFailure() ? "형식이 올바르지 않습니다." : fieldError.getDefaultMessage();
            errors.putIfAbsent(fieldError.getField(), message);
        }

        return errors;
    }
}
