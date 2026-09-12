package com.paytm.wallet.exception;

import com.paytm.wallet.constants.ErrorCode;

public class ConflictException extends ApiException {

    public ConflictException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
