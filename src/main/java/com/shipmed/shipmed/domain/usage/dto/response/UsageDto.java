package com.shipmed.shipmed.domain.usage.dto.response;

import com.shipmed.shipmed.domain.usage.entity.RecordStatus;

import java.time.Instant;
import java.util.List;

public record UsageDto(
        Long usageId,
        int quantity,
        RecordStatus status,
        Instant usedAt,
        List<AllocationDto> allocations
) {
}
