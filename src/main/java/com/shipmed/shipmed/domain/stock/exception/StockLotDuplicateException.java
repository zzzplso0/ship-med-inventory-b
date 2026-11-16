package com.shipmed.shipmed.domain.stock.exception;

import com.shipmed.shipmed.global.exception.ErrorCode;

public class StockLotDuplicateException extends StockLotException {

    public StockLotDuplicateException() {
        super(ErrorCode.DUPLICATE_LOT);
    }
}
