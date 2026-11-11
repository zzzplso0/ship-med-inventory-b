package com.shipmed.shipmed.domain.usage.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UsageCancelRequest(
        @NotBlank @Size(max = 255) String reason
) {
}
