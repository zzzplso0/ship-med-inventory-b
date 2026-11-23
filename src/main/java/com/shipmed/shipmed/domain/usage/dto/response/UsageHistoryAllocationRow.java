package com.shipmed.shipmed.domain.usage.dto.response;

public record UsageHistoryAllocationRow(
        Long usageId,
        Long lotId,
        String lotNumber,
        Integer quantity
) {
}
