package com.shipmed.shipmed.domain.medication.service;

import com.shipmed.shipmed.domain.medication.dto.MedicationMapper;
import com.shipmed.shipmed.domain.medication.dto.request.MedicationCreateRequest;
import com.shipmed.shipmed.domain.medication.dto.response.MedicationDto;
import com.shipmed.shipmed.domain.medication.entity.Medication;
import com.shipmed.shipmed.domain.medication.repository.MedicationRepository;
import com.shipmed.shipmed.global.request.PageQuery;
import com.shipmed.shipmed.global.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
@RequiredArgsConstructor
@Service
public class MedicationServiceImpl implements MedicationService {

    private final MedicationRepository medicationRepository;
    private final MedicationMapper medicationMapper;

    @Transactional
    @Override
    public MedicationDto create(MedicationCreateRequest medicationCreateRequest) {
        Medication medication = new Medication(
                medicationCreateRequest.name(),
                medicationCreateRequest.category(),
                medicationCreateRequest.unit()
        );

        Medication savedMedication = medicationRepository.save(medication);

        return medicationMapper.toDto(savedMedication);
    }

    @Override
    public PageResponse<MedicationDto> getAllMedications(PageQuery pageQuery) {
        Page<Medication> medications = medicationRepository.findAll(pageQuery.toPageable());

        return PageResponse.from(medications.map(medicationMapper::toDto));
    }
}
