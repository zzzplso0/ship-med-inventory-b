package com.shipmed.shipmed.domain.auth.dto;

import com.shipmed.shipmed.domain.user.entity.Role;
import com.shipmed.shipmed.global.security.principal.CustomUserDetails;

public record LoginResponse(
        Long userId,
        String loginId,
        Role role,
        Long shipId
) {
    public static LoginResponse from(CustomUserDetails principal) {
        return new LoginResponse(
                principal.getUserId(),
                principal.getLoginId(),
                principal.getRole(),
                principal.getShipId()
        );
    }
}
