package com.shipmed.shipmed.domain.notification.dto.response;

import java.time.LocalDate;

public record ExpiryNotificationLotRow(
        Long shipId,
        String shipName,
        String medicationName,
        String lotNumber,
        int remainingQuantity,
        LocalDate expiryDate
) {
}
