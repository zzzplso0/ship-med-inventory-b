package com.shipmed.shipmed.domain.stock.exception;

import com.shipmed.shipmed.global.exception.ErrorCode;

public class StockLotNotFoundException extends StockLotException {

    public StockLotNotFoundException() {
        super(ErrorCode.LOT_NOT_FOUND);
    }
}
