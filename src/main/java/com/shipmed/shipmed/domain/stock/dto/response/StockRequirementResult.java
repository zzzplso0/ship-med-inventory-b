package com.shipmed.shipmed.domain.stock.dto.response;

public record StockRequirementResult(
        StockRequirementDto stockRequirement,
        boolean created
) {
}
