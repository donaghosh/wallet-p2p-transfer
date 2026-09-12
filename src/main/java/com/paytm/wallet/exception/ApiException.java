package com.paytm.wallet.exception;

import com.paytm.wallet.constants.ErrorCode;
import lombok.Getter;

/**
 * Base for all business exceptions. Carries a stable {@link ErrorCode} (which owns the
 * HTTP status) rather than baking status into each subclass.
 */
@Getter
public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;

    public ApiException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
