package com.paytm.wallet.exception;

import com.paytm.wallet.constants.ErrorCode;

public class BadRequestException extends ApiException {

    public BadRequestException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
