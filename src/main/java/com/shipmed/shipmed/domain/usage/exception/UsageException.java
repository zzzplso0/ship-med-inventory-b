package com.shipmed.shipmed.domain.usage.exception;

import com.shipmed.shipmed.global.exception.BusinessException;
import com.shipmed.shipmed.global.exception.ErrorCode;

import java.util.Map;

public class UsageException extends BusinessException {

    public UsageException(ErrorCode errorCode) {
        super(errorCode);
    }

    public UsageException(ErrorCode errorCode, Map<String, Object> data) {
        super(errorCode, data);
    }
}
