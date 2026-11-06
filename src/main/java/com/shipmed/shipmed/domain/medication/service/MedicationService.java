package com.shipmed.shipmed.domain.medication.service;

import com.shipmed.shipmed.domain.medication.dto.request.MedicationCreateRequest;
import com.shipmed.shipmed.domain.medication.dto.response.MedicationDto;
import com.shipmed.shipmed.global.request.PageQuery;
import com.shipmed.shipmed.global.response.PageResponse;

public interface MedicationService {

    MedicationDto create(MedicationCreateRequest medicationCreateRequest);
    PageResponse<MedicationDto> getAllMedications(PageQuery pageQuery);
}
