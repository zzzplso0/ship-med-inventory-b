package com.shipmed.shipmed.domain.ship.controller;

import com.shipmed.shipmed.domain.ship.dto.request.ShipCreateRequest;
import com.shipmed.shipmed.domain.ship.dto.response.ShipDto;
import com.shipmed.shipmed.domain.ship.service.ShipService;
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
public class ShipController {

    private final ShipService shipService;

    @PostMapping
    public ResponseEntity<ShipDto> createShip(@Valid @RequestBody ShipCreateRequest shipCreateRequest) {
        ShipDto shipDto = shipService.create(shipCreateRequest);

        return ResponseEntity.status(HttpStatus.CREATED).body(shipDto);
    }

    @GetMapping
    public ResponseEntity<PageResponse<ShipDto>> getAllShips(@Valid @ModelAttribute PageQuery pageQuery) {
        return ResponseEntity.ok(shipService.getAllShips(pageQuery));
    }
}
