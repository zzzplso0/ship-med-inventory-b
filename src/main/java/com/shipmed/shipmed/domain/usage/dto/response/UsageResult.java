package com.shipmed.shipmed.domain.usage.dto.response;

public record UsageResult(
        UsageDto usageDto,
        boolean created
) {
}
