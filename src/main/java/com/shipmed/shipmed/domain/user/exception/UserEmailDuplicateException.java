package com.shipmed.shipmed.domain.user.exception;

import com.shipmed.shipmed.global.exception.ErrorCode;

public class UserEmailDuplicateException extends UserException {

    public UserEmailDuplicateException() {
        super(ErrorCode.DUPLICATE_EMAIL);
    }
}
