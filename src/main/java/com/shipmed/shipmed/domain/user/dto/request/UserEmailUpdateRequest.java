package com.shipmed.shipmed.domain.user.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserEmailUpdateRequest(
        @NotBlank @Email @Size(max = 100) String email
) {
    // 검증 전에 앞뒤 공백을 지운다. @Email은 공백이 붙은 값을 형식 위반으로 본다.
    public UserEmailUpdateRequest {
        email = email == null ? null : email.strip();
    }
}
