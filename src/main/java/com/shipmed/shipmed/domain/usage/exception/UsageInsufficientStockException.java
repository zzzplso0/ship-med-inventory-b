package com.shipmed.shipmed.domain.usage.exception;

import com.shipmed.shipmed.global.exception.ErrorCode;

import java.util.Map;

public class UsageInsufficientStockException extends UsageException {

    public UsageInsufficientStockException(int requested, int available) {
        super(ErrorCode.INSUFFICIENT_STOCK, Map.of("requested", requested, "available", available));
    }
}
