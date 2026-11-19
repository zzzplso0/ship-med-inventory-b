package com.shipmed.shipmed.view;

import com.shipmed.shipmed.domain.stock.dto.request.StockLotCreateRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

// 입고 폼. 브라우저의 date, datetime-local 입력 형식으로 받고, 입고 시각은 UTC로 본다.
public record LotForm(
        @NotNull Long medicationId,
        @NotBlank @Size(max = 50) String lotNumber,
        @NotNull @Positive Integer quantity,
        @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expiryDate,
        @NotNull @DateTimeFormat(pattern = LotForm.RECEIVED_AT_PATTERN) LocalDateTime receivedAt
) {
    public static final String RECEIVED_AT_PATTERN = "yyyy-MM-dd'T'HH:mm";

    public StockLotCreateRequest toRequest() {
        return new StockLotCreateRequest(lotNumber, quantity, medicationId, receivedAt.toInstant(ZoneOffset.UTC), expiryDate);
    }
}
