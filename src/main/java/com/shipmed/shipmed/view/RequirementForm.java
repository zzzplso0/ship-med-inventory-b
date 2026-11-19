package com.shipmed.shipmed.view;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

// 비치 기준 설정 폼. API는 medicationId를 경로로 받지만 폼은 값으로 보낸다(design 6.1).
public record RequirementForm(
        @NotNull Long medicationId,
        @NotNull @Min(0) Integer minQuantity
) {
}
