package com.shipmed.shipmed.domain.stock.exception;

import com.shipmed.shipmed.global.exception.ErrorCode;

public class StockRequirementConflictException extends StockRequirementException {

    public StockRequirementConflictException() {
        super(ErrorCode.REQUIREMENT_CONFLICT);
    }
}
