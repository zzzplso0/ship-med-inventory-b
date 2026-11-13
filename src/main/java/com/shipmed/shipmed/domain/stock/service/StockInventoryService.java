package com.shipmed.shipmed.domain.stock.service;

import com.shipmed.shipmed.domain.stock.dto.response.StockInventoryDto;

import java.util.List;

public interface StockInventoryService {

    List<StockInventoryDto> getInventory(Long shipId);
}
