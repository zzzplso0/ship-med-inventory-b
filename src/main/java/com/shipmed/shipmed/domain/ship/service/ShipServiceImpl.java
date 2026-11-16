package com.shipmed.shipmed.domain.ship.service;

import com.shipmed.shipmed.domain.ship.dto.ShipMapper;
import com.shipmed.shipmed.domain.ship.dto.request.ShipCreateRequest;
import com.shipmed.shipmed.domain.ship.dto.response.ShipDto;
import com.shipmed.shipmed.domain.ship.entity.Ship;
import com.shipmed.shipmed.domain.ship.repository.ShipRepository;
import com.shipmed.shipmed.global.request.PageQuery;
import com.shipmed.shipmed.global.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
@RequiredArgsConstructor
@Service
public class ShipServiceImpl implements ShipService {

    private final ShipRepository shipRepository;
    private final ShipMapper shipMapper;

    @Transactional
    @Override
    public ShipDto create(ShipCreateRequest shipCreateRequest) {
        Ship ship = new Ship(shipCreateRequest.name());

        Ship savedShip = shipRepository.save(ship);

        return shipMapper.toDto(savedShip);
    }

    @Override
    public PageResponse<ShipDto> getAllShips(PageQuery pageQuery) {
        Page<Ship> ships = shipRepository.findAll(pageQuery.toPageable());

        return PageResponse.from(ships.map(shipMapper::toDto));
    }
}
