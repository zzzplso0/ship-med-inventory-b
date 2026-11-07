package com.shipmed.shipmed.domain.stock.dto.response;

public record StockRequirementListDto(
        Long medicationId,
        String medicationName,
        Integer minQuantity
) {
}
