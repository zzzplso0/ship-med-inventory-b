package com.shipmed.shipmed.domain.stock.controller;

import com.shipmed.shipmed.domain.stock.dto.request.StockLotCreateRequest;
import com.shipmed.shipmed.domain.stock.dto.response.StockLotDto;
import com.shipmed.shipmed.domain.stock.service.StockLotService;
import com.shipmed.shipmed.global.security.principal.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RequestMapping("/api/ships")
@RestController
public class StockLotController {

    private final StockLotService stockLotService;

    @PostMapping("/{shipId}/lots")
    public ResponseEntity<StockLotDto> create(@PathVariable Long shipId,
                                              @Valid @RequestBody StockLotCreateRequest stockLotCreateRequest,
                                              @AuthenticationPrincipal CustomUserDetails customUserDetails) {
        StockLotDto stockLotDto = stockLotService.create(shipId, stockLotCreateRequest, customUserDetails.getUserId());

        return ResponseEntity.status(HttpStatus.CREATED).body(stockLotDto);
    }
}
