package com.shipmed.shipmed.domain.stock.dto;

import com.shipmed.shipmed.domain.stock.dto.response.StockRequirementDto;
import com.shipmed.shipmed.domain.stock.dto.response.StockRequirementListDto;
import com.shipmed.shipmed.domain.stock.entity.StockRequirement;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface StockRequirementMapper {

    @Mapping(source = "ship.id", target = "shipId")
    @Mapping(source = "medication.id", target = "medicationId")
    StockRequirementDto toDto(StockRequirement stockRequirement);

    @Mapping(source = "medication.id", target = "medicationId")
    @Mapping(source = "medication.name", target = "medicationName")
    StockRequirementListDto toDtos(StockRequirement stockRequirements);
}
