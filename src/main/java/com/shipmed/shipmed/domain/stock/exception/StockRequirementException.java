package com.shipmed.shipmed.domain.stock.exception;

import com.shipmed.shipmed.global.exception.BusinessException;
import com.shipmed.shipmed.global.exception.ErrorCode;

public class StockRequirementException extends BusinessException {

    public StockRequirementException(ErrorCode errorCode) {
        super(errorCode);
    }
}
