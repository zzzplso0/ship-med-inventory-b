package com.shipmed.shipmed.domain.medication.dto;


import com.shipmed.shipmed.domain.medication.dto.response.MedicationDto;
import com.shipmed.shipmed.domain.medication.entity.Medication;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface MedicationMapper {

    MedicationDto toDto(Medication medication);
}
