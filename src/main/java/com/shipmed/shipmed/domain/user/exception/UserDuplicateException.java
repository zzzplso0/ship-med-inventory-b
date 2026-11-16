package com.shipmed.shipmed.domain.user.exception;

import com.shipmed.shipmed.global.exception.ErrorCode;

public class UserDuplicateException extends UserException {

    public UserDuplicateException() {
        super(ErrorCode.DUPLICATE_LOGIN_ID);
    }
}
