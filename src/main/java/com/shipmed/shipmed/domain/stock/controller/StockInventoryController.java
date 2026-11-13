package com.shipmed.shipmed.domain.stock.controller;

import com.shipmed.shipmed.domain.stock.dto.response.StockInventoryDto;
import com.shipmed.shipmed.domain.stock.service.StockInventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RequiredArgsConstructor
@RequestMapping("/api/ships")
@RestController
public class StockInventoryController {

    private final StockInventoryService inventoryService;

    @GetMapping("/{shipId}/inventory")
    public ResponseEntity<List<StockInventoryDto>> getInventory(@PathVariable Long shipId) {
        return ResponseEntity.ok(inventoryService.getInventory(shipId));
    }
}
