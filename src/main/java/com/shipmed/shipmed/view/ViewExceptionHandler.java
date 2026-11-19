package com.shipmed.shipmed.view;

import com.shipmed.shipmed.global.exception.BusinessException;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.TypeMismatchException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;

// 화면 컨트롤러의 예외를 오류 화면으로 바꾼다. GlobalExceptionHandler(JSON)보다 먼저 적용되도록 순서를 앞에 둔다.
// 입력 오류(400)와 비즈니스 충돌(409)은 각 화면 컨트롤러가 입력 화면에 직접 보여 주므로, 여기에는 처리하지 못한 것만 온다.
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@ControllerAdvice(basePackages = "com.shipmed.shipmed.view")
public class ViewExceptionHandler {

    // 코드에서 직접 던진 BusinessException. 소속 선박이 아닌 요청(인터셉터의 403)도 여기로 온다.
    @ExceptionHandler(BusinessException.class)
    public ModelAndView handleBusinessException(BusinessException ex, HttpServletResponse response) {
        return errorView(ex.getErrorCode().getStatus(), ex.getErrorCode().getMessage(), response);
    }

    // Spring MVC 예외(타입 변환 실패, 파라미터 누락 등)는 상태를 유지하고, 그 밖의 예외는 500.
    @ExceptionHandler(Exception.class)
    public ModelAndView handleException(Exception ex, HttpServletResponse response) {
        int status;
        if (ex instanceof ErrorResponse errorResponse) {
            status = errorResponse.getStatusCode().value();
        } else if (ex instanceof TypeMismatchException) {
            // 경로 변수·파라미터 타입 변환 실패(/ships/abc 등). ErrorResponse가 아니라 따로 400으로 본다.
            status = 400;
        } else {
            status = 500;
        }

        if (status >= 500) {
            log.error("Unhandled view exception", ex);
        }

        return errorView(status, null, response);
    }

    private ModelAndView errorView(int status, String message, HttpServletResponse response) {
        response.setStatus(status);

        String viewName = switch (status) {
            case 403 -> "error/403";
            case 404 -> "error/404";
            default -> status >= 500 ? "error/500" : "error/4xx";
        };

        ModelAndView mav = new ModelAndView(viewName);
        mav.addObject("status", status);
        mav.addObject("message", message);

        return mav;
    }
}
