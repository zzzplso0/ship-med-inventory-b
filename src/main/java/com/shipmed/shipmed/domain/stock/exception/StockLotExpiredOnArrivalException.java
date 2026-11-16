package com.shipmed.shipmed.domain.stock.exception;

import com.shipmed.shipmed.global.exception.ErrorCode;

public class StockLotExpiredOnArrivalException extends StockLotException {

    public StockLotExpiredOnArrivalException() {
        super(ErrorCode.EXPIRED_ON_ARRIVAL);
    }
}
