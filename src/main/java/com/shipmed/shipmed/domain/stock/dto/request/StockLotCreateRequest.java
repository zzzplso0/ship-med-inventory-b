package com.shipmed.shipmed.domain.stock.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;

public record StockLotCreateRequest(
        @NotBlank @Size(max = 50) String lotNumber,
        @NotNull @Positive Integer quantity,
        @NotNull Long medicationId,
        @NotNull Instant receivedAt,
        @NotNull LocalDate expiryDate
) {
}
