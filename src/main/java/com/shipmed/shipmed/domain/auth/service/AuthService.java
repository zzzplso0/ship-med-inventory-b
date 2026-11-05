package com.shipmed.shipmed.domain.auth.service;

import com.shipmed.shipmed.domain.auth.dto.LoginRequest;
import com.shipmed.shipmed.global.exception.BusinessException;
import com.shipmed.shipmed.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;

    public Authentication login(LoginRequest request) {
        try {
            return authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(request.loginId(), request.password()));
        } catch (AuthenticationException e) {
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }
    }
}
