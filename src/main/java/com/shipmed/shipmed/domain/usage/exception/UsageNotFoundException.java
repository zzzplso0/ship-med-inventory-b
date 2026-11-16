package com.shipmed.shipmed.domain.usage.exception;

import com.shipmed.shipmed.global.exception.ErrorCode;

public class UsageNotFoundException extends UsageException {

    public UsageNotFoundException() {
        super(ErrorCode.USAGE_NOT_FOUND);
    }
}
