package com.shipmed.shipmed.domain.usage.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record UsageCreateRequest(
        @NotNull Long medicationId,
        @NotNull @Positive Integer quantity
) {
}
