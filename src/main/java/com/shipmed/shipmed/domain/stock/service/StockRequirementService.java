package com.shipmed.shipmed.domain.stock.service;

import com.shipmed.shipmed.domain.stock.dto.request.StockRequirementCreateRequest;
import com.shipmed.shipmed.domain.stock.dto.response.StockRequirementListDto;
import com.shipmed.shipmed.domain.stock.dto.response.StockRequirementResult;
import com.shipmed.shipmed.global.request.PageQuery;
import com.shipmed.shipmed.global.response.PageResponse;

public interface StockRequirementService {

    StockRequirementResult upsertRequirement(Long shipId, Long medicationId, StockRequirementCreateRequest stockRequirementCreateRequest);
    PageResponse<StockRequirementListDto> getRequirements(Long shipId, PageQuery pageQuery);
}
