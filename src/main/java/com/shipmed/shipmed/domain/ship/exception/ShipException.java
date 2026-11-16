package com.shipmed.shipmed.domain.ship.exception;

import com.shipmed.shipmed.global.exception.BusinessException;
import com.shipmed.shipmed.global.exception.ErrorCode;

public class ShipException extends BusinessException {

    public ShipException(ErrorCode errorCode) {
        super(errorCode);
    }
}
