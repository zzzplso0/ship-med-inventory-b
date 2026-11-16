package com.shipmed.shipmed.domain.stock.dto.response;

import java.util.List;

public record StockInventoryDto(
        Long medicationId,
        String medicationName,
        String unit,
        int available,
        boolean requirementSet,
        Integer minQuantity,
        Boolean shortage,
        List<StockExpiringLotDto> expiringSoon
) {
}
