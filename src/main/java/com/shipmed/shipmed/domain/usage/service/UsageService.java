package com.shipmed.shipmed.domain.usage.service;

import com.shipmed.shipmed.domain.usage.dto.request.UsageCancelRequest;
import com.shipmed.shipmed.domain.usage.dto.request.UsageCreateRequest;
import com.shipmed.shipmed.domain.usage.dto.request.UsageHistoryQuery;
import com.shipmed.shipmed.domain.usage.dto.response.UsageCancelDto;
import com.shipmed.shipmed.domain.usage.dto.response.UsageHistoryDto;
import com.shipmed.shipmed.domain.usage.dto.response.UsageResult;
import com.shipmed.shipmed.global.request.PageQuery;
import com.shipmed.shipmed.global.response.PageResponse;

public interface UsageService {

    UsageResult create(Long shipId, Long userId, String idempotencyKey, UsageCreateRequest usageCreateRequest);

    UsageCancelDto cancel(Long usageId, Long userId, UsageCancelRequest usageCancelRequest);

    PageResponse<UsageHistoryDto> getHistory(Long shipId, UsageHistoryQuery query, PageQuery pageQuery);
}
