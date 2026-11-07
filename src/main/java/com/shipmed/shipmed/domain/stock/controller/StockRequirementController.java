package com.shipmed.shipmed.domain.stock.controller;

import com.shipmed.shipmed.domain.stock.dto.request.StockRequirementCreateRequest;
import com.shipmed.shipmed.domain.stock.dto.response.StockRequirementDto;
import com.shipmed.shipmed.domain.stock.dto.response.StockRequirementListDto;
import com.shipmed.shipmed.domain.stock.dto.response.StockRequirementResult;
import com.shipmed.shipmed.domain.stock.service.StockRequirementService;
import com.shipmed.shipmed.global.request.PageQuery;
import com.shipmed.shipmed.global.response.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RequestMapping("/api/ships")
@RestController
public class StockRequirementController {

    private final StockRequirementService stockRequirementService;

    @PutMapping("/{shipId}/requirements/{medicationId}")
    public ResponseEntity<StockRequirementDto> upsertRequirement(@PathVariable Long shipId, @PathVariable Long medicationId,
                                                                 @Valid @RequestBody StockRequirementCreateRequest stockRequirementCreateRequest) {
        StockRequirementResult result = stockRequirementService.upsertRequirement(shipId, medicationId, stockRequirementCreateRequest);

        if (result.created()) {
            return ResponseEntity.status(HttpStatus.CREATED).body(result.stockRequirement());
        }

        return ResponseEntity.ok(result.stockRequirement());
    }

    @GetMapping("/{shipId}/requirements")
    public ResponseEntity<PageResponse<StockRequirementListDto>> getAllRequirements(@PathVariable Long shipId,
                                                                                    @Valid @ModelAttribute PageQuery pageQuery) {
        PageResponse<StockRequirementListDto> response = stockRequirementService.getRequirements(shipId, pageQuery);

        return ResponseEntity.ok(response);
    }
}
