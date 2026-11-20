package com.shipmed.shipmed.domain.stock.dto.response;

import com.shipmed.shipmed.domain.stock.entity.LotStatus;

import java.time.Instant;

public record StockLotDisposalDto(
        Long lotId,
        LotStatus status,
        Instant discardedAt,
        String discardedBy,
        String discardedReason
) {
}
