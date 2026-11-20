package com.shipmed.shipmed.domain.stock.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record StockLotDisposalRequest(
        @NotBlank @Size(max = 255) String reason
) {
}
