package com.shipmed.shipmed.domain.user.exception;

import com.shipmed.shipmed.global.exception.ErrorCode;

public class UserNotFoundException extends UserException {

    public UserNotFoundException() {
        super(ErrorCode.USER_NOT_FOUND);
    }
}
