package com.shipmed.shipmed.domain.user.exception;

import com.shipmed.shipmed.global.exception.BusinessException;
import com.shipmed.shipmed.global.exception.ErrorCode;

public class UserException extends BusinessException {

    public UserException(ErrorCode errorCode) {
        super(errorCode);
    }
}
