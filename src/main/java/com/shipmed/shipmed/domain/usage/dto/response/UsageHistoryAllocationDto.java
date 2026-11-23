package com.shipmed.shipmed.domain.usage.dto.response;

public record UsageHistoryAllocationDto(
        Long lotId,
        String lotNumber,
        Integer quantity
) {
}
