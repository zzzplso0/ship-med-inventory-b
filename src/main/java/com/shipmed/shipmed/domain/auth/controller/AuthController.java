package com.shipmed.shipmed.domain.auth.controller;

import com.shipmed.shipmed.domain.auth.dto.LoginRequest;
import com.shipmed.shipmed.domain.auth.dto.LoginResponse;
import com.shipmed.shipmed.domain.auth.service.AuthService;
import com.shipmed.shipmed.domain.auth.session.LoginSessionManager;
import com.shipmed.shipmed.global.security.principal.CustomUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RequestMapping("/auth")
@RestController
public class AuthController {

    private final AuthService authService;
    private final LoginSessionManager loginSessionManager;

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
        Authentication authentication = authService.login(request);

        if (httpServletRequest.getSession(false) != null) {
            httpServletRequest.changeSessionId();
        }

        loginSessionManager.saveContext(authentication, httpServletRequest, httpServletResponse);

        return LoginResponse.from((CustomUserDetails) authentication.getPrincipal());
    }

    @PostMapping("/logout")
    public void logout(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, Authentication authentication) {
        new SecurityContextLogoutHandler().logout(httpServletRequest, httpServletResponse, authentication);
    }
}
