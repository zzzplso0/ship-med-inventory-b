package com.shipmed.shipmed.domain.stock.exception;

import com.shipmed.shipmed.global.exception.BusinessException;
import com.shipmed.shipmed.global.exception.ErrorCode;

public class StockLotException extends BusinessException {

    public StockLotException(ErrorCode errorCode) {
        super(errorCode);
    }
}
