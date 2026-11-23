package com.shipmed.shipmed.domain.usage.dto.response;

import com.shipmed.shipmed.domain.usage.entity.RecordStatus;

import java.time.Instant;
import java.util.List;

public record UsageHistoryDto(
        Long usageId,

        Long medicationId,
        String medicationName,
        String unit,
        Integer quantity,

        RecordStatus status,
        Instant usedAt,

        Long userId,
        String userName,
        Instant cancelledAt,
        String cancelledBy,
        String cancelReason,

        List<UsageHistoryAllocationDto> allocations
) {
}
