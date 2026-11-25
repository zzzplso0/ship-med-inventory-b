package com.shipmed.shipmed.domain.user.dto.request;

import com.shipmed.shipmed.domain.user.entity.Role;
import jakarta.validation.constraints.*;

public record UserCreateRequest(
        @NotBlank @Size(max = 30) String loginId,
        @NotBlank @Email @Size(max = 100) String email,
        @NotBlank @Size(max = 30) String name,
        @NotNull Role role,

        @NotBlank
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[@$!%*#?&])[A-Za-z\\d@$!%*#?&]{8,20}$",
                message = "비밀번호는 8~20자이며 영문, 숫자, 특수문자(@$!%*#?&)를 각각 1자 이상 포함해야 합니다."
        )
        String password,

        Long shipId
) {
        // 검증 전에 앞뒤 공백을 지운다. @Email은 공백이 붙은 값을 형식 위반으로 본다.
        public UserCreateRequest {
                email = email == null ? null : email.strip();
        }

        @AssertTrue(message = "의무사관은 shipId가 필요하고 관리자는 shipId를 가질 수 없습니다.")
        public boolean isShipMatchingRole() {
                return role == null || (role == Role.MEDICAL_OFFICER) == (shipId != null);
        }
}