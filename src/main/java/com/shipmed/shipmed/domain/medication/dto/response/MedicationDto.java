package com.shipmed.shipmed.domain.medication.dto.response;

public record MedicationDto(
        Long id,
        String name,
        String category,
        String unit
) {
}
