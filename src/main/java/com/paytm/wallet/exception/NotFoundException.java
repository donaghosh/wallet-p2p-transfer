package com.paytm.wallet.exception;

import com.paytm.wallet.constants.ErrorCode;

public class NotFoundException extends ApiException {

    public NotFoundException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
