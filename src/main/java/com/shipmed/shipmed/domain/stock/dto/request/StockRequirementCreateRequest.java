package com.shipmed.shipmed.domain.stock.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record StockRequirementCreateRequest(
        @NotNull @Min(0) Integer minQuantity
) {
}
