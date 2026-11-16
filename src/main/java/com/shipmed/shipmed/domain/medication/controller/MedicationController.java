package com.shipmed.shipmed.domain.medication.controller;

import com.shipmed.shipmed.domain.medication.dto.request.MedicationCreateRequest;
import com.shipmed.shipmed.domain.medication.dto.response.MedicationDto;
import com.shipmed.shipmed.domain.medication.service.MedicationService;
import com.shipmed.shipmed.global.request.PageQuery;
import com.shipmed.shipmed.global.response.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RequiredArgsConstructor
@RequestMapping("/api/medications")
@RestController
public class MedicationController {

    private final MedicationService medicationService;

    @PostMapping
    public ResponseEntity<MedicationDto> createMedication(@Valid @RequestBody MedicationCreateRequest medicationCreateRequest) {
        MedicationDto medicationDto = medicationService.create(medicationCreateRequest);

        return ResponseEntity.status(HttpStatus.CREATED).body(medicationDto);
    }

    @GetMapping
    public ResponseEntity<PageResponse<MedicationDto>> getAllMedications(@Valid @ModelAttribute PageQuery pageQuery) {
        return ResponseEntity.ok(medicationService.getAllMedications(pageQuery));
    }
}
