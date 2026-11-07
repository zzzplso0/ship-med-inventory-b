package com.shipmed.shipmed.domain.ship.exception;

import com.shipmed.shipmed.global.exception.ErrorCode;

public class ShipNotFoundException extends ShipException {

    public ShipNotFoundException() {
        super(ErrorCode.SHIP_NOT_FOUND);
    }
}
