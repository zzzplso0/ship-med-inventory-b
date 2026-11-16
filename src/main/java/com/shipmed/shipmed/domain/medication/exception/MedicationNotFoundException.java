package com.shipmed.shipmed.domain.medication.exception;

import com.shipmed.shipmed.global.exception.ErrorCode;

public class MedicationNotFoundException extends MedicationException {

    public MedicationNotFoundException() {
        super(ErrorCode.MEDICATION_NOT_FOUND);
    }
}
