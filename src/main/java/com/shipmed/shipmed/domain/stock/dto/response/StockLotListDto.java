package com.shipmed.shipmed.domain.stock.dto.response;

import com.shipmed.shipmed.domain.stock.entity.LotStatus;

import java.time.Instant;
import java.time.LocalDate;

public record StockLotListDto(
        Long lotId,
        Long medicationId,
        String medicationName,
        String unit,
        String lotNumber,
        Integer initialQuantity,
        Integer remainingQuantity,
        LocalDate expiryDate,
        boolean expired,
        LotStatus status,
        Instant receivedAt,
        String receivedBy,
        Instant discardedAt,
        String discardedBy,
        String discardedReason
) {
}
