package com.shipmed.shipmed.domain.stock.exception;

import com.shipmed.shipmed.global.exception.ErrorCode;

public class StockLotReceivedAtInFutureException extends StockLotException {

    public StockLotReceivedAtInFutureException() {
        super(ErrorCode.RECEIVED_AT_IN_FUTURE);
    }
}
