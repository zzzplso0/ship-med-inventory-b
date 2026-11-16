package com.shipmed.shipmed.domain.medication.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record MedicationCreateRequest(
        @NotBlank @Size(max = 50) String name,
        // 생략 O, 빈 문자열과 공백은 X.
        @Pattern(regexp = "(?s).*\\S.*", message = "공백일 수 없습니다") @Size(max = 30) String category,
        @NotBlank @Size(max = 20) String unit
) {
}
