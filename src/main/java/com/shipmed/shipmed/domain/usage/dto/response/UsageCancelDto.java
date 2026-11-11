package com.shipmed.shipmed.domain.usage.dto.response;

import com.shipmed.shipmed.domain.usage.entity.RecordStatus;

import java.time.Instant;
import java.util.List;

public record UsageCancelDto(
        Long usageId,
        RecordStatus status,
        Instant cancelledAt,
        String cancelledBy,
        List<AllocationDto> restored
) {
}
