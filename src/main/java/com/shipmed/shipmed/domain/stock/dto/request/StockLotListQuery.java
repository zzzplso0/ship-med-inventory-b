package com.shipmed.shipmed.domain.stock.dto.request;

import com.shipmed.shipmed.domain.stock.entity.LotStatus;

public record StockLotListQuery(
        Long medicationId,
        LotStatus status
) {
}
