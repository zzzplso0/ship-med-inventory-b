package com.shipmed.shipmed.domain.usage.dto;

import com.shipmed.shipmed.domain.usage.dto.response.AllocationDto;
import com.shipmed.shipmed.domain.usage.dto.response.UsageCancelDto;
import com.shipmed.shipmed.domain.usage.dto.response.UsageDto;
import com.shipmed.shipmed.domain.usage.entity.UsageLotAllocation;
import com.shipmed.shipmed.domain.usage.entity.UsageRecord;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface UsageMapper {

    @Mapping(source = "usageRecord.id", target = "usageId")
    @Mapping(source = "usageRecord.totalQuantity", target = "quantity")
    UsageDto toDto(UsageRecord usageRecord, List<UsageLotAllocation> allocations);

    @Mapping(source = "stockLot.id", target = "lotId")
    @Mapping(source = "allocatedQuantity", target = "quantity")
    AllocationDto toAllocationDto(UsageLotAllocation allocation);

    @Mapping(source = "usageRecord.id", target = "usageId")
    @Mapping(source = "usageRecord.cancelledBy.name", target = "cancelledBy")
    @Mapping(source = "allocations", target = "restored")
    UsageCancelDto toCancelDto(UsageRecord usageRecord, List<UsageLotAllocation> allocations);
}
