package com.shipmed.shipmed.domain.ship.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ShipCreateRequest(
        @NotBlank @Size(max = 50) String name
) {
}
