package com.shipmed.shipmed.domain.stock.dto.response;

public record StockRequirementDto(
        Long shipId,
        Long medicationId,
        Integer minQuantity
) {
}
