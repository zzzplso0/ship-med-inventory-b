package com.shipmed.shipmed.domain.ship.dto;


import com.shipmed.shipmed.domain.ship.dto.response.ShipDto;
import com.shipmed.shipmed.domain.ship.entity.Ship;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ShipMapper {

    ShipDto toDto(Ship ship);
}
