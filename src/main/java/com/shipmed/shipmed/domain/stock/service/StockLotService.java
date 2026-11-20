package com.shipmed.shipmed.domain.stock.service;

import com.shipmed.shipmed.domain.stock.dto.request.StockLotCreateRequest;
import com.shipmed.shipmed.domain.stock.dto.request.StockLotListQuery;
import com.shipmed.shipmed.domain.stock.dto.response.StockLotDto;
import com.shipmed.shipmed.domain.stock.dto.response.StockLotListDto;
import com.shipmed.shipmed.global.request.PageQuery;
import com.shipmed.shipmed.global.response.PageResponse;

public interface StockLotService {

    StockLotDto create(Long shipId, StockLotCreateRequest stockLotCreateRequest, Long userId);

    PageResponse<StockLotListDto> getLots(Long shipId, StockLotListQuery stockLotListQuery, PageQuery pageQuery);
}
