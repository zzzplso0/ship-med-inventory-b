package com.shipmed.shipmed.domain.ship.service;

import com.shipmed.shipmed.domain.ship.dto.request.ShipCreateRequest;
import com.shipmed.shipmed.domain.ship.dto.response.ShipDto;
import com.shipmed.shipmed.global.request.PageQuery;
import com.shipmed.shipmed.global.response.PageResponse;

public interface ShipService {

    ShipDto create(ShipCreateRequest shipCreateRequest);
    PageResponse<ShipDto> getAllShips(PageQuery pageQuery);
}
