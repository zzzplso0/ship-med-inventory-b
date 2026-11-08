package com.shipmed.shipmed.domain.stock.service;

import com.shipmed.shipmed.domain.stock.dto.request.StockLotCreateRequest;
import com.shipmed.shipmed.domain.stock.dto.response.StockLotDto;

public interface StockLotService {

    StockLotDto create(Long shipId, StockLotCreateRequest stockLotCreateRequest, Long userId);
}
