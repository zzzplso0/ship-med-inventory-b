package com.shipmed.shipmed.domain.auth.session;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

@Component
public class LoginSessionManager {

    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();
    private final SecurityContextLogoutHandler logoutHandler = new SecurityContextLogoutHandler();

    public void saveContext(Authentication authentication, HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
        // 로그인 전 세션이 있으면 ID 교체 (세션 고정 방지)
        if (httpServletRequest.getSession(false) != null) {
            httpServletRequest.changeSessionId();
        }

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, httpServletRequest, httpServletResponse);
    }

    public void logout(Authentication authentication, HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
        logoutHandler.logout(httpServletRequest, httpServletResponse, authentication);
    }
}
