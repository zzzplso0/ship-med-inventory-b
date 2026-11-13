package com.shipmed.shipmed.domain.stock.dto.response;

public record StockInventoryRow(
        Long medicationId,
        String medicationName,
        String unit,
        int available,
        boolean requirementSet,
        Integer minQuantity,
        Boolean shortage
) {
}
