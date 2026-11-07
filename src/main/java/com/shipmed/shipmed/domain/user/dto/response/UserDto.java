package com.shipmed.shipmed.domain.user.dto.response;

import com.shipmed.shipmed.domain.user.entity.Role;

public record UserDto(
        Long id,
        String loginId,
        String name,
        Role role,
        Long shipId
) {
}
