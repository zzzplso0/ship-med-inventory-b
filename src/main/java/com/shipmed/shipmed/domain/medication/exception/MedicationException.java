package com.shipmed.shipmed.domain.medication.exception;

import com.shipmed.shipmed.global.exception.BusinessException;
import com.shipmed.shipmed.global.exception.ErrorCode;

public class MedicationException extends BusinessException {

    public MedicationException(ErrorCode errorCode) {
        super(errorCode);
    }
}
