package com.shipmed.shipmed.domain.stock.dto.response;

import com.shipmed.shipmed.domain.stock.entity.LotStatus;

import java.time.Instant;
import java.time.LocalDate;

public record StockLotDto(
        Long lotId,
        int initialQuantity,
        int remainingQuantity,
        LocalDate expiryDate,
        Instant receivedAt,
        LotStatus status
) {
}
