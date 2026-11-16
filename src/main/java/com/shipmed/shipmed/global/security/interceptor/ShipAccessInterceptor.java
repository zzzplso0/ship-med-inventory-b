package com.shipmed.shipmed.global.security.interceptor;

import com.shipmed.shipmed.domain.user.entity.Role;
import com.shipmed.shipmed.global.exception.BusinessException;
import com.shipmed.shipmed.global.exception.ErrorCode;
import com.shipmed.shipmed.global.security.principal.CustomUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;

@Component
public class ShipAccessInterceptor implements HandlerInterceptor {

    // 소속 선박 판정. @Valid보다 먼저 실행해야 403이 400보다 먼저 나감.
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 컨트롤러로 가는 요청만 판정. 없는 경로는 그대로 404.
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        // 미로그인은 필터에서 끝나므로 여기서는 항상 로그인 상태.
        CustomUserDetails principal = (CustomUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        if (principal.getRole() == Role.ADMIN) {
            return true;
        }

        // {shipId="2"}
        // 인터셉터에서는 @PathVariable을 쓸 수 없어 핸들러 매핑이 요청 속성에 넣어 둔 값을 사용.
        @SuppressWarnings("unchecked")
        Map<String, String> pathVariables = (Map<String, String>) request.getAttribute(
                HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);

        // 세션의 소속 선박과 경로의 선박을 비교. DB를 조회하지 않으므로 없는 선박도 403.
        // 문자열로 비교해 /ships/abc/lots도 500이 아니라 403으로 처리.
        if (!String.valueOf(principal.getShipId()).equals(pathVariables.get("shipId"))) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }

        return true;
    }
}
