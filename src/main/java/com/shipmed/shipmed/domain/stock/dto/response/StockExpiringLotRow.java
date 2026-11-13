package com.shipmed.shipmed.domain.stock.dto.response;

import java.time.LocalDate;

public record StockExpiringLotRow(
        Long medicationId,
        Long lotId,
        int quantity,
        LocalDate expiryDate
) {
}
