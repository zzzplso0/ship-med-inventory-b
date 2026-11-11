package com.shipmed.shipmed.domain.usage.service;

import com.shipmed.shipmed.domain.usage.dto.request.UsageCreateRequest;
import com.shipmed.shipmed.domain.usage.dto.response.UsageResult;

public interface UsageService {

    UsageResult create(Long shipId, Long userId, String idempotencyKey, UsageCreateRequest usageCreateRequest);
}
