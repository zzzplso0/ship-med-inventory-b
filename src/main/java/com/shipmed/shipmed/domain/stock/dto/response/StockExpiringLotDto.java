package com.shipmed.shipmed.domain.stock.dto.response;

import java.time.LocalDate;

public record StockExpiringLotDto(
        Long lotId,
        int quantity,
        LocalDate expiryDate
) {
}
