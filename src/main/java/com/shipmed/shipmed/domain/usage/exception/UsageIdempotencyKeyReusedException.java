package com.shipmed.shipmed.domain.usage.exception;

import com.shipmed.shipmed.global.exception.BusinessException;
import com.shipmed.shipmed.global.exception.ErrorCode;

public class UsageIdempotencyKeyReusedException extends BusinessException {

    public UsageIdempotencyKeyReusedException() {
        super(ErrorCode.IDEMPOTENCY_KEY_REUSED);
    }
}
