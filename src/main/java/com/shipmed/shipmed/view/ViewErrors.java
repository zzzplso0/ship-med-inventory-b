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
            String message = fieldError.isBindingFailure() ? "형식이 올바르지 않습니다." : message(fieldError);
            errors.putIfAbsent(fieldError.getField(), message);
        }

        return errors;
    }

    // 필수값 누락의 기본 메시지("널이어서는 안됩니다" 등)는 화면에 맞는 문구로 바꾼다.
    private static String message(FieldError fieldError) {
        String code = fieldError.getCode();
        if ("NotNull".equals(code) || "NotBlank".equals(code)) {
            return "필수 입력입니다.";
        }
        return fieldError.getDefaultMessage();
    }
}
