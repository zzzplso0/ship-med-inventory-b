package com.shipmed.shipmed.domain.stock.dto;

import com.shipmed.shipmed.domain.stock.dto.response.StockLotDto;
import com.shipmed.shipmed.domain.stock.entity.StockLot;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface StockLotMapper {

    @Mapping(source = "id", target = "lotId")
    StockLotDto toDto(StockLot stockLot);
}
